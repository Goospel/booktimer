import { Text } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import type { ContributionGraph, MonthlySection } from '../api';
import { fetchHistory } from '../api';
import { formatDuration } from '../format';
import { ErrorMessage, GrassGrid, LEVEL_COLORS, MANUAL_OUTLINE, Screen, monthLabelPositions } from '../ui';

/** 기록 화면 잔디 칸 — `GrassGrid`의 기본값과 같아야 월 라벨이 그 열 위에 선다. */
const CELL_SIZE = 11;

/**
 * 기록 — 잔디 · 연속일 · 총 시간. stop 응답에 graph가 동봉되므로 이 화면은 다시 받아오지 않고
 * 홈이 넘겨준 최신 graph를 그대로 그린다(설계 §2.5).
 *
 * <p>탭 재편(PR-5) 전까지 있던 "돌아가기" 버튼은 탭 전환이 대신하므로 없앴다.
 */
export function History({ graph }: { graph: ContributionGraph }) {
  // 스크롤 위치는 손대지 않는다 — weeks[0]이 최신 주라 초기 위치(왼쪽 끝)가 이미 오늘이다(api.ts `weeks`).
  const months = monthLabelPositions(graph.monthLabels, CELL_SIZE);

  // 날짜별 기록은 대시보드에 안 실려 오므로 이 탭에서 따로 받는다. 실패해도 위쪽 잔디는 그대로 두고
  // 아래에만 사유를 남긴다 — 목록 하나 때문에 화면 전체를 에러로 덮으면 손해가 크다.
  const [sections, setSections] = useState<MonthlySection[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    fetchHistory()
      .then((r) => alive && setSections(r.months))
      .catch((e: Error) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, []);

  return (
    <Screen title="내 기록">
      <div style={{ display: 'flex', gap: 12 }}>
        <Stat label="연속" value={`${graph.currentStreak}일`} />
        <Stat label="읽은 날" value={`${graph.activeDays}일`} />
        <Stat label="총 시간" value={formatDuration(graph.totalSeconds)} />
      </div>

      <Text typography="st11" style={{ display: 'block', margin: '24px 0 10px' }}>
        {graph.growthStageEmoji} {graph.growthStageLabel}
      </Text>

      <div className="no-scrollbar" style={{ overflowX: 'auto', paddingBottom: 8 }}>
        {/* 라벨은 격자 폭 안에서 절대 배치된다 — inline-block이라 이 상자가 격자만큼만 넓어진다. */}
        <div style={{ position: 'relative', display: 'inline-block', paddingTop: 16 }}>
          <div aria-hidden="true" style={{ position: 'absolute', top: 0, left: 0, height: 14 }}>
            {months.map(({ label, left }) => (
              <span
                key={label}
                style={{ position: 'absolute', left, fontSize: 10, color: '#6F6A5E', whiteSpace: 'nowrap' }}
              >
                {label}
              </span>
            ))}
          </div>
          <GrassGrid weeks={graph.weeks} cellSize={CELL_SIZE} />
        </div>
      </div>

      <Legend />

      {sections !== null && <MonthlyRecords months={sections} />}
      <ErrorMessage message={error} />
    </Screen>
  );
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** `2026-08-14` → `08-14 (금)`. 연도는 월 머리글이 이미 말해 주고, 폭이 좁아 일자에선 뺀다. */
export function formatRecordDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return `${date.slice(5)} (${WEEKDAYS[new Date(year, month - 1, day).getDay()]})`;
}

/** `2026-08` → `2026년 8월`. */
export function formatMonthTitle(month: string): string {
  const [year, m] = month.split('-').map(Number);
  return `${year}년 ${m}월`;
}

/**
 * 잔디 아래 날짜별 기록 — 언제·무슨 책을·얼마나 읽었는지. 웹 `MonthlyRecords.vue`와 같은 정보를 담되
 * 월 ◀▶ 이동은 두지 않는다(A안): 폰은 세로 스크롤이 자연스럽고, 한 달만 담으면 잔디 아래가 다시 빈다.
 */
export function MonthlyRecords({ months }: { months: MonthlySection[] }) {
  if (months.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 28 }}>
        아직 독서 기록이 없어요. 홈에서 측정을 시작해 보세요.
      </Text>
    );
  }

  return (
    <div style={{ marginTop: 28, borderTop: '1px solid var(--adaptiveGrey200, #E4DDD0)' }}>
      {months.map((section) => (
        <section key={section.month}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', margin: '20px 0 4px' }}>
            <Text typography="st10" fontWeight="bold">
              {formatMonthTitle(section.month)}
            </Text>
            <Text typography="st12" color="grey600">
              {formatDuration(section.totalSeconds)}
            </Text>
          </div>
          {section.days.map((day) => (
            <div
              key={day.date}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'baseline',
                gap: 10,
                padding: '8px 0',
                borderBottom: '1px solid var(--adaptiveGrey100, #EFEAE0)',
              }}
            >
              {/* minWidth:0 이 없으면 긴 제목이 줄임표 대신 시간 열을 밀어낸다(flex 기본 min-content). */}
              <div style={{ minWidth: 0 }}>
                <Text typography="st11" style={{ display: 'block' }}>
                  {formatRecordDate(day.date)}
                </Text>
                {day.bookTitles.length > 0 && (
                  <Text
                    typography="st12"
                    color="grey600"
                    style={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                  >
                    {day.bookTitles.join(', ')}
                  </Text>
                )}
              </div>
              <Text typography="st11" color="grey700" style={{ flex: '0 0 auto' }}>
                {formatDuration(day.totalSeconds)}
              </Text>
            </div>
          ))}
        </section>
      ))}
    </div>
  );
}

/** 색 농도 범례 — 잔디가 무슨 뜻인지 화면 어디에도 없었다. 웹 `.grass-legend`와 같은 말을 쓴다. */
function Legend() {
  const swatch = { width: 10, height: 10, borderRadius: 2, flex: '0 0 auto' } as const;

  return (
    <div aria-hidden="true" style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 4, marginTop: 4 }}>
      <Text typography="st12" color="grey600">
        적게
      </Text>
      {LEVEL_COLORS.map((color) => (
        <span key={color} style={{ ...swatch, background: color }} />
      ))}
      <Text typography="st12" color="grey600">
        많이
      </Text>
      <span style={{ ...swatch, marginLeft: 10, background: LEVEL_COLORS[2], outline: MANUAL_OUTLINE }} />
      <Text typography="st12" color="grey600">
        직접 채움
      </Text>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        flex: 1,
        padding: '16px 12px',
        borderRadius: 12,
        background: 'var(--adaptiveGrey100, #FCFAF5)',
        textAlign: 'center',
      }}
    >
      <Text typography="st12" color="grey600" style={{ display: 'block' }}>
        {label}
      </Text>
      <Text typography="t6" fontWeight="bold" style={{ display: 'block', marginTop: 4 }}>
        {value}
      </Text>
    </div>
  );
}
