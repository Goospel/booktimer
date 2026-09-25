import type { CSSProperties } from 'react';
import { useEffect, useState } from 'react';

import type { SessionRow, StudyDay, StudyHistoryResponse, StudyMonth } from '../api';
import { fetchStudyHistory } from '../api';
import { CACHE_STUDY_HISTORY, cacheGet, cachePut } from '../cache';
import { ErrorMessage, Loading, SERIF_VALUE, Screen, SectionTitle, Text } from '../ui';
import {
  BUTTON_RESET,
  Chevron,
  DAY_TRAY,
  DayBar,
  DayDate,
  DayTotal,
  GRASS_CARD,
  GrassPanel,
  Legend,
  MonthHead,
  SessionLines,
  StatStrip,
  barPercent,
  isExpandable,
} from './History';

/**
 * 공부 기록 — <b>타이머가 잰 측정 사실만</b> 그린다.
 *
 * <p>판정(지킴/못 지킴)은 한 픽셀도 없다: 그건 「일정」 탭(`StudyCalendar`)의 몫이고, 두 화면의 경계가
 * 이 화면이 따로 있는 이유다. 표지 열은 없다 — 날짜를 펼치면 측정 한 건씩 서고 거기서 책을 붙이거나 바꾼다(R2).
 * 수동 입력은 공부 원장에 없다.
 *
 * <p>잔디·스탯·범례·측정 줄은 독서 기록과 <b>같은 조각</b>을 쓴다(`History.tsx`에서 import) — 복제하면
 * 「`weeks[0]`이 최신 주」 같은 규약을 두 곳에서 밟게 된다.
 *
 * <p>자정을 걸친 공부 측정은 서버가 자정에서 쪼개 저장한다 — 조각이 각자 제 날짜 줄에 선다.
 */
export function StudyHistory({
  onError,
  onAssignBook = () => {},
  reloadKey = 0,
  candidateCount = 0,
}: {
  onError: (error: Error) => void;
  onAssignBook?: (row: SessionRow) => void;
  reloadKey?: number;
  candidateCount?: number;
}) {
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
        if (alive) {
          // 재조회(reloadKey)가 성공하면 지난 실패 문구를 걷는다
          setData(r);
          setError(null);
        }
      })
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        else if (alive) setError(e.message);
      });
    return () => {
      alive = false;
    };
  }, [onError, reloadKey]);

  return (
    <Screen title="공부 기록">
      {data === null ? (
        error === null && <Loading />
      ) : (
        <StudyHistoryView data={data} candidateCount={candidateCount} onAssign={onAssignBook} />
      )}
      <ErrorMessage message={error} />
    </Screen>
  );
}

/** 데이터를 받아 <b>그리기만</b> 한다 — 하니스가 정적 렌더라 effect가 안 돈다(T-149). 그려진 꼴은 여기서 잰다. */
export function StudyHistoryView({
  data,
  candidateCount = 0,
  onAssign = () => {},
}: {
  data: StudyHistoryResponse;
  candidateCount?: number;
  onAssign?: (row: SessionRow) => void;
}) {
  return (
    <>
      <StatStrip graph={data.graph} activeDaysLabel="공부한 날" />

      <SectionTitle style={{ margin: '24px 0 12px', ...SERIF_VALUE, fontSize: 20 }}>공부한 날짜</SectionTitle>

      <section style={GRASS_CARD}>
        <GrassPanel graph={data.graph} />
        {/* 「직접 채움」은 뺀다 — 공부 원장엔 수동 입력이 없어 그 스와치가 없는 것을 설명하게 된다. */}
        <Legend manual={false} />
      </section>

      <StudyMonthlyRecords months={data.months} candidateCount={candidateCount} onAssign={onAssign} />
    </>
  );
}

/** 잔디 아래 날짜별 기록 — 독서 `MonthlyRecords`와 같은 조판(월 머리 + 눌린 쟁반), 펼침도 같은 규칙(한 번에 하나). */
export function StudyMonthlyRecords({
  months,
  candidateCount = 0,
  onAssign = () => {},
}: {
  months: StudyMonth[];
  candidateCount?: number;
  onAssign?: (row: SessionRow) => void;
}) {
  const [openDate, setOpenDate] = useState<string | null>(null);

  if (months.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 28 }}>
        아직 공부 기록이 없어요. 홈에서 공부 모드로 측정을 시작해 보세요.
      </Text>
    );
  }

  return (
    <div style={{ marginTop: 12 }}>
      {months.map((section) => (
        <section key={section.month}>
          <MonthHead month={section.month} totalSeconds={section.totalSeconds} />
          {section.days.length > 0 && (
            <div style={DAY_TRAY}>
              {section.days.map((day) => (
                <StudyDayRow
                  key={day.date}
                  day={day}
                  monthMax={maxOf(section)}
                  expanded={day.date === openDate}
                  onToggle={() => setOpenDate(day.date === openDate ? null : day.date)}
                  candidateCount={candidateCount}
                  onAssign={onAssign}
                />
              ))}
            </div>
          )}
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
 * 하루 한 줄의 고정 격자 — 날짜 · 막대 · 시간 · 손잡이.
 *
 * <p>독서 행의 표지 열(56px)이 없다. <b>고정 폭</b>인 이유는 독서와 같다: 시간 글자 폭이 행마다 다르면
 * 막대의 시작·끝이 흔들려 길이로 날을 견주는 것 자체가 거짓이 된다. 손잡이 칸(14px)은 펼칠 수 없는 날에도
 * 비워 남긴다 — 사라지면 시간의 오른쪽 끝이 행마다 밀린다.
 */
const ROW_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '50px minmax(0, 1fr) 84px 14px',
  alignItems: 'center',
  columnGap: 8,
  width: '100%',
  padding: 0,
};

/**
 * 하루 한 줄 — 날짜(2줄) · 막대 · 시간. 측정이 있는 날은 줄 전체가 손잡이라 펼치면 측정 한 건씩 선다(R2).
 * 펼침 상태는 위에서 받는다(한 번에 하나 규칙 + 정적 렌더로 펼친 꼴을 잰다 — 독서 `DayRow`와 같은 이유).
 */
export function StudyDayRow({
  day,
  monthMax,
  expanded,
  onToggle,
  candidateCount,
  onAssign,
}: {
  day: StudyDay;
  monthMax: number;
  expanded: boolean;
  onToggle: () => void;
  candidateCount: number;
  onAssign: (row: SessionRow) => void;
}) {
  const expandable = isExpandable(day);
  const summary = (
    <>
      <DayDate date={day.date} />
      <DayBar percent={barPercent(day.totalSeconds, monthMax)} />
      <DayTotal seconds={day.totalSeconds} />
      {expandable ? <Chevron open={expanded} /> : <span aria-hidden="true" />}
    </>
  );

  return (
    <div>
      {expandable ? (
        <button
          type="button"
          data-day-toggle=""
          aria-expanded={expanded}
          onClick={onToggle}
          style={{ ...BUTTON_RESET, ...ROW_GRID }}
        >
          {summary}
        </button>
      ) : (
        <div style={ROW_GRID}>{summary}</div>
      )}
      {expanded && (
        <div style={{ marginTop: 10 }}>
          <SessionLines rows={day.sessions ?? []} candidateCount={candidateCount} onAssign={onAssign} />
        </div>
      )}
    </div>
  );
}
