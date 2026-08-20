import { Text } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import type { ContributionGraph, DailyRecord, MonthlySection } from '../api';
import { fetchHistory } from '../api';
import { formatDuration, subjectParticle } from '../format';
import {
  CoverInitial,
  ErrorMessage,
  GrassGrid,
  LEVEL_COLORS,
  MANUAL_OUTLINE,
  PENCIL_FRAME,
  Screen,
  monthLabelPositions,
} from '../ui';

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
      <GrowthCard graph={graph} />

      {/* 연속은 위 성장 카드가 이미 말한다 — 여기 다시 두면 한 화면에서 같은 숫자를 두 번 읽게 된다. */}
      <div style={{ display: 'flex', gap: 12, marginTop: 14 }}>
        <Stat label="읽은 날" value={`${graph.activeDays}일`} />
        <Stat label="총 시간" value={formatDuration(graph.totalSeconds)} />
      </div>

      <Text typography="st12" color="grey600" style={{ display: 'block', margin: '24px 0 8px' }}>
        읽은 날짜
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

/**
 * 다음 단계까지 — 「N일 더 읽으면 …이 돼요」. 최고 단계면 재촉하지 않는다.
 *
 * <p>「1일 남음」을 「내일」로 바꾸지 않는다: 연속은 <b>유저 타임존의 하루</b> 단위라 자정을 넘기면
 * 기준이 달라져, 오늘 밤 읽는 사람에게 「내일」이 거짓이 된다. 남은 일수는 언제 읽어도 참이다.
 */
export function growthNudge(daysToNext: number, nextLabel: string | null): string {
  if (nextLabel === null) return '가장 큰 단계예요';
  // 단계 이름은 서버가 준다(땅·새싹·꽃·나무) — 「나무이 돼요」가 나오지 않게 받침으로 조사를 고른다.
  return `${daysToNext}일 더 읽으면 ${nextLabel}${subjectParticle(nextLabel)} 돼요`;
}

/**
 * 성장 카드 — 이 앱이 주는 유일한 보상을 카드로 세운다.
 *
 * <p>전에는 스탯과 잔디 사이에 `🌿 어린 나무` 한 줄이 각주처럼 끼어 있었다. 문제가 둘이었다:
 * ① 보상인데 <b>강조가 하나도 없고</b> ② 이 단계가 <b>어디서 오는지</b>가 화면 어디에도 없었다
 * (연속 일수가 정본인데 그 숫자는 옆 칸에 따로 서 있었다). 그래서 연속을 이 카드 안으로 들여
 * 「연속 N일째 → 지금 단계 → 다음 단계까지」 한 흐름으로 묶는다.
 */
function GrowthCard({ graph }: { graph: ContributionGraph }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: 14,
        borderRadius: 12,
        background: 'var(--adaptiveGrey100, #FCFAF5)',
        border: '1px solid transparent',
        borderImage: PENCIL_FRAME,
      }}
    >
      {/* 단계 그림은 서버가 준다(땅·새싹·꽃·나무) — 화면이 사다리를 다시 정하지 않는다. */}
      <span aria-hidden="true" style={{ flex: '0 0 auto', fontSize: 26, lineHeight: 1 }}>
        {graph.growthStageEmoji}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <Text typography="st12" color="grey600" style={{ display: 'block' }}>
          연속 {graph.currentStreak}일째
        </Text>
        <Text typography="t6" fontWeight="bold" style={{ display: 'block', lineHeight: 1.25 }}>
          {graph.growthStageLabel}
        </Text>
        {/* 막대는 「지금 단계 안에서 얼마나 왔나」 — 서버가 퍼센트로 계산해 준다. */}
        <div style={{ height: 5, borderRadius: 3, background: 'var(--adaptiveGrey200, #E4DDD0)', marginTop: 7 }}>
          <span
            style={{
              display: 'block',
              width: `${graph.growthProgressPercent}%`,
              height: '100%',
              borderRadius: 3,
              background: 'var(--adaptiveBlue500, #6E8A6A)',
            }}
          />
        </div>
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 4 }}>
          {growthNudge(graph.daysToNextStage, graph.nextStageLabel)}
        </Text>
      </div>
    </div>
  );
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** `2026-08-14` → `08-14`. 연도는 월 머리글이, 요일은 아랫줄({@link formatWeekday})이 말한다. */
export function formatRecordDate(date: string): string {
  return date.slice(5);
}

/**
 * `2026-08-14` → `금요일`. 날짜 아래 한 줄로 선다.
 *
 * <p>한 줄짜리 `08-14 (금)`을 두 줄로 가른 이유는 <b>자리</b>다 — 같은 행에 표지 칸과 시간 막대가
 * 들어오면서 가로를 80px 넘게 쓰던 날짜가 막대를 밀어냈다. 두 줄이면 52px면 되고, 덤으로 요일이
 * 괄호 약자 대신 온말이 된다.
 */
export function formatWeekday(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return `${WEEKDAYS[new Date(year, month - 1, day).getDay()]}요일`;
}

/**
 * 하루 막대의 길이(0~100) — 기준은 <b>그 달에서 가장 오래 읽은 날</b>이다.
 *
 * <p>달마다 기준을 다시 잡아야 그 달 안의 편차가 보인다(전체 최대로 재면 한가한 달은 죄다 납작해진다).
 * 기준이 0이면 0을 돌려준다 — 0으로 나누면 NaN이 되고, `width: NaN%`는 막대를 통째로 지운다.
 */
export function barPercent(seconds: number, maxSeconds: number): number {
  if (maxSeconds <= 0) return 0;
  return Math.min(100, Math.round((seconds / maxSeconds) * 100));
}

/**
 * 그날의 표지 묶음 — 세울 제목과 넘친 권수.
 *
 * <p>전에는 제목을 쉼표로 이어 붙이고 `nowrap + ellipsis`로 잘랐다. 두 권만 돼도
 * 「미움받을 용기, 사피…」가 되는데, <b>몇 권인지도 무슨 책인지도 안 남는</b> 잘림이었다.
 * 표지 칸으로 옮기면 권수가 자리로 보이고, 넘치는 만큼은 숫자로 <b>밝혀서</b> 뺀다.
 */
export function coverStack(titles: string[], max = 3): { shown: string[]; more: number } {
  return { shown: titles.slice(0, max), more: Math.max(0, titles.length - max) };
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
          {section.days.map((day) => <DayRow key={day.date} day={day} monthMax={maxOf(section)} />)}
        </section>
      ))}
    </div>
  );
}

/** 그 달에서 가장 오래 읽은 날의 초 — 막대의 기준. 빈 달은 0(막대가 안 그려진다). */
function maxOf(section: MonthlySection): number {
  return section.days.reduce((max, day) => Math.max(max, day.totalSeconds), 0);
}

/**
 * 하루 한 줄 — 날짜 · 표지 · 막대 · 시간.
 *
 * <p>전에는 날짜와 쉼표로 이은 제목, 그리고 시간뿐이었다. 화면에 그림이 하나도 없어(기록 탭의
 * `img`·`svg`가 <b>0개였다</b>) 훑을 수가 없었고, 제목은 두 권만 넘어도 잘렸다. 표지 칸이 「무슨 책·몇 권」을,
 * 막대가 「그 달에서 어느 정도였나」를 글자 대신 형태로 말한다.
 */
function DayRow({ day, monthMax }: { day: DailyRecord; monthMax: number }) {
  const { shown, more } = coverStack(day.bookTitles);

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '9px 0',
        borderBottom: '1px solid var(--adaptiveGrey100, #EFEAE0)',
      }}
    >
      <div style={{ flex: '0 0 auto', width: 52 }}>
        <Text typography="st11" style={{ display: 'block', lineHeight: 1.2 }}>
          {formatRecordDate(day.date)}
        </Text>
        <Text typography="st12" color="grey600" style={{ display: 'block' }}>
          {formatWeekday(day.date)}
        </Text>
      </div>

      {/* 표지는 제목색 첫 글자다 — 같은 책은 앱 어디서나 같은 색이라, 작아도 같은 책으로 읽힌다.
          책을 안 붙인 날도 빈 칸을 남긴다(자리가 무너지면 아래 행의 막대 시작점이 어긋난다). */}
      <div style={{ flex: '0 0 auto', display: 'flex', alignItems: 'center', gap: 3 }}>
        {shown.map((title) => (
          <CoverInitial key={title} title={title} width={20} />
        ))}
        {shown.length === 0 && (
          <span
            aria-hidden="true"
            style={{
              display: 'block',
              width: 20,
              height: 28,
              borderRadius: 3,
              border: '1px dashed var(--adaptiveGrey200, #E4DDD0)',
            }}
          />
        )}
        {more > 0 && (
          <Text typography="st12" color="grey600">
            +{more}
          </Text>
        )}
      </div>

      {/* 막대 색은 잔디 팔레트에서 가져온다 — 같은 「얼마나 읽었나」를 두 곳이 다른 색으로 말하지 않게. */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          aria-hidden="true"
          style={{
            width: `${barPercent(day.totalSeconds, monthMax)}%`,
            height: 6,
            borderRadius: 3,
            background: LEVEL_COLORS[2],
          }}
        />
      </div>

      <Text typography="st11" color="grey700" style={{ flex: '0 0 auto' }}>
        {formatDuration(day.totalSeconds)}
      </Text>
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
        border: '1px solid transparent',
        borderImage: PENCIL_FRAME,
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
