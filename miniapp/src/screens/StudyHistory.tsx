import type { CSSProperties } from 'react';
import { useEffect, useState } from 'react';

import type { StudyDay, StudyHistoryResponse, StudyMonth } from '../api';
import { fetchStudyHistory } from '../api';
import { CACHE_STUDY_HISTORY, cacheGet, cachePut } from '../cache';
import { formatDuration } from '../format';
import { ErrorMessage, LEVEL_COLORS, Loading, SECTION_RULE, SERIF_VALUE, Screen, SectionTitle, Text } from '../ui';
import { GrassPanel, Legend, StatStrip, barPercent, formatMonthTitle, formatRecordDate, formatWeekday } from './History';

/**
 * 공부 기록 — <b>타이머가 잰 측정 사실만</b> 그린다.
 *
 * <p>판정(지킴/못 지킴)은 한 픽셀도 없다: 그건 「일정」 탭(`StudyCalendar`)의 몫이고, 두 화면의 경계가
 * 이 화면이 따로 있는 이유다. 책이 없으니 표지 열·펼침도 없다 — 공부 원장엔 책·수동 입력이 아예 없다.
 *
 * <p>잔디·스탯·범례는 독서 기록과 <b>같은 조각</b>을 쓴다(`History.tsx`에서 import) — 복제하면
 * 「`weeks[0]`이 최신 주」 같은 규약을 두 곳에서 밟게 된다.
 *
 * <p>⚠️ 자정을 걸친 공부 세션은 <b>시작한 날에 전부</b> 들어간다(서버 귀속 규칙). 독서는 저장 시점에
 * 자정으로 쪼개므로 두 모드의 규칙이 다르다 — 공부 판 분할은 후속 PR 몫이다.
 */
export function StudyHistory({ onError }: { onError: (error: Error) => void }) {
  // 지난 성공 응답이 첫 렌더의 출발점이다(SWR) — 재검증은 그대로 매번 나간다.
  const [data, setData] = useState<StudyHistoryResponse | null>(
    () => cacheGet<StudyHistoryResponse>(CACHE_STUDY_HISTORY) ?? null,
  );
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    fetchStudyHistory()
      .then((r) => {
        cachePut(CACHE_STUDY_HISTORY, r); // 언마운트 뒤 도착해도 캐시엔 넣는다 — 다음 진입의 첫 렌더가 된다
        if (alive) setData(r);
      })
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        else if (alive) setError(e.message);
      });
    return () => {
      alive = false;
    };
  }, [onError]);

  return (
    <Screen title="공부 기록">
      {data === null ? error === null && <Loading /> : <StudyHistoryView data={data} />}
      <ErrorMessage message={error} />
    </Screen>
  );
}

/** 데이터를 받아 <b>그리기만</b> 한다 — 하니스가 정적 렌더라 effect가 안 돈다(T-149). 그려진 꼴은 여기서 잰다. */
export function StudyHistoryView({ data }: { data: StudyHistoryResponse }) {
  return (
    <>
      <StatStrip graph={data.graph} activeDaysLabel="공부한 날" />

      <SectionTitle style={{ margin: '24px 0 8px', ...SERIF_VALUE, fontSize: 20 }}>공부한 날짜</SectionTitle>

      <GrassPanel graph={data.graph} />

      {/* 「직접 채움」은 뺀다 — 공부 원장엔 수동 입력이 없어 그 스와치가 없는 것을 설명하게 된다. */}
      <Legend manual={false} />

      <StudyMonthlyRecords months={data.months} />
    </>
  );
}

/** 잔디 아래 날짜별 기록 — 독서 `MonthlyRecords`와 같은 조판이되 펼침 상태가 없다(펼칠 것이 없다). */
export function StudyMonthlyRecords({ months }: { months: StudyMonth[] }) {
  if (months.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 28 }}>
        아직 공부 기록이 없어요. 홈에서 공부 모드로 측정을 시작해 보세요.
      </Text>
    );
  }

  return (
    <div style={{ marginTop: 28, borderTop: '1px solid var(--adaptiveGrey200, #E4DDD0)' }}>
      {months.map((section) => (
        <section key={section.month}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'baseline',
              margin: '20px 0 4px',
              paddingBottom: 10,
              borderBottom: SECTION_RULE,
            }}
          >
            <Text typography="st10" fontWeight="bold" style={{ ...SERIF_VALUE, fontSize: 20 }}>
              {formatMonthTitle(section.month)}
            </Text>
            <Text typography="st12" color="grey600" style={{ ...SERIF_VALUE, fontSize: 14 }}>
              {formatDuration(section.totalSeconds)}
            </Text>
          </div>
          {section.days.map((day) => (
            <StudyDayRow key={day.date} day={day} monthMax={maxOf(section)} />
          ))}
        </section>
      ))}
    </div>
  );
}

/** 그 달에서 가장 오래 공부한 날의 초 — 막대의 기준. 빈 달은 0(막대가 안 그려진다). */
function maxOf(section: StudyMonth): number {
  return section.days.reduce((max, day) => Math.max(max, day.totalSeconds), 0);
}

/**
 * 하루 한 줄의 고정 격자 — 날짜 · 막대 · 시간.
 *
 * <p>독서 행의 표지 열(56px)·손잡이 열(16px)이 없다. <b>고정 폭</b>인 이유는 독서와 같다: 시간 글자 폭이
 * 행마다 다르면 막대의 시작·끝이 흔들려 길이로 날을 견주는 것 자체가 거짓이 된다.
 */
const ROW_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '50px minmax(0, 1fr) 76px',
  alignItems: 'center',
  columnGap: 8,
  width: '100%',
  padding: '9px 0',
  borderBottom: '1px solid var(--adaptiveGrey100, #EFEAE0)',
};

/** 하루 한 줄 — 날짜(2줄) · 막대 · 시간. 누를 것이 없어 버튼이 아니다. */
function StudyDayRow({ day, monthMax }: { day: StudyDay; monthMax: number }) {
  return (
    <div style={ROW_GRID}>
      <div>
        <Text typography="st11" style={{ display: 'block', lineHeight: 1.2, ...SERIF_VALUE, fontSize: 15 }}>
          {formatRecordDate(day.date)}
        </Text>
        <Text typography="st12" color="grey600" style={{ display: 'block' }}>
          {formatWeekday(day.date)}
        </Text>
      </div>

      {/* 막대 색은 잔디 팔레트에서 가져온다 — 같은 「얼마나 했나」를 두 곳이 다른 색으로 말하지 않게. */}
      <div
        aria-hidden="true"
        style={{
          width: `${barPercent(day.totalSeconds, monthMax)}%`,
          height: 6,
          borderRadius: 3,
          background: LEVEL_COLORS[2],
        }}
      />

      {/* 정렬은 감싸는 요소가 한다 — TDS `Text`는 style의 `text-align`을 걸러 낸다(독서 행과 같은 이유). */}
      <div style={{ textAlign: 'right' }}>
        <Text typography="st11" style={{ whiteSpace: 'nowrap', ...SERIF_VALUE, fontSize: 15 }}>
          {formatDuration(day.totalSeconds)}
        </Text>
      </div>
    </div>
  );
}
