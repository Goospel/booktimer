import { Button } from '@toss/tds-mobile';
import type { CSSProperties } from 'react';
import { useEffect, useState } from 'react';

import type { BookRead, ContributionDay, ContributionGraph, DailyRecord, MonthlySection, SessionRow } from '../api';
import { fetchHistory } from '../api';
import { CACHE_HISTORY, cacheGet, cachePut } from '../cache';
import { formatDuration } from '../format';
import {
  BookCover,
  DENT,
  ErrorMessage,
  GrassGrid,
  LEVEL_COLORS,
  MANUAL_OUTLINE,
  SECTION_RULE,
  SERIF_VALUE,
  Screen,
  SectionTitle,
  TODAY_RING,
  Text,
  monthLabelPositions,
  sectionStyle,
} from '../ui';

/** 기록 화면 잔디 칸 — `GrassGrid`의 기본값과 같아야 월 라벨이 그 열 위에 선다. */
const CELL_SIZE = 11;

/**
 * 기록 — 잔디 · 연속일 · 총 시간. stop 응답에 graph가 동봉되므로 이 화면은 다시 받아오지 않고
 * 홈이 넘겨준 최신 graph를 그대로 그린다(설계 §2.5).
 *
 * <p>탭 재편(PR-5) 전까지 있던 "돌아가기" 버튼은 탭 전환이 대신하므로 없앴다.
 *
 * <p>날짜를 펼치면 측정 한 건씩 서고 [책 붙이기]/[바꾸기]가 붙는다(R2). 시트는 App이 연다 — 이 화면은
 * 누른 줄을 넘길 뿐이다. `reloadKey`가 오르면 목록을 다시 받는다(붙이기·기록 탭에서의 종료 뒤).
 */
export function History({
  graph,
  onAssignBook = () => {},
  reloadKey = 0,
  candidateCount = 0,
}: {
  graph: ContributionGraph;
  onAssignBook?: (row: SessionRow) => void;
  reloadKey?: number;
  /** 붙일 수 있는 책 수 — 0이면 책 없는 줄에 버튼 대신 안내가 선다({@link sessionAction}). */
  candidateCount?: number;
}) {
  // 날짜별 기록은 대시보드에 안 실려 오므로 이 탭에서 따로 받는다. 실패해도 위쪽 잔디는 그대로 두고
  // 아래에만 사유를 남긴다 — 목록 하나 때문에 화면 전체를 에러로 덮으면 손해가 크다.
  // 지난 성공 응답이 첫 렌더의 출발점이다 — 탭을 다시 열 때 아래쪽만 늦게 붙던 자리(재검증은 그대로).
  const [sections, setSections] = useState<MonthlySection[] | null>(
    () => cacheGet<MonthlySection[]>(CACHE_HISTORY) ?? null,
  );
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    fetchHistory()
      .then((r) => {
        cachePut(CACHE_HISTORY, r.months); // 언마운트 뒤 도착해도 캐시엔 넣는다 — 다음 진입의 첫 렌더가 된다
        if (alive) {
          // 재조회(reloadKey)가 성공하면 지난 실패 문구를 걷는다
          setSections(r.months);
          setError(null);
        }
      })
      .catch((e: Error) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, [reloadKey]);

  return (
    <Screen title="내 기록">
      <StatStrip graph={graph} />

      {/* 시안 2d — 이 화면이 답하는 것의 이름이라 값으로 조판한다(세리프 20). */}
      <SectionTitle style={{ margin: '24px 0 12px', ...SERIF_VALUE, fontSize: 20 }}>읽은 날짜</SectionTitle>

      {/* 잔디 카드 — 격자와 범례가 한 면에 선다(시안 Soft-History). */}
      <section style={GRASS_CARD}>
        <GrassPanel graph={graph} />
        <Legend />
      </section>

      {sections !== null && (
        <MonthlyRecords months={sections} candidateCount={candidateCount} onAssign={onAssignBook} />
      )}
      <ErrorMessage message={error} />
    </Screen>
  );
}

/**
 * 잔디 한 판 — 위에 월 라벨, 아래에 격자. 가로 스크롤 상자까지 한 덩어리다.
 *
 * <p>스크롤 위치는 손대지 않는다 — `weeks[0]`이 최신 주라 초기 위치(왼쪽 끝)가 이미 오늘이다(api.ts `weeks`).
 *
 * <p>공부 기록 화면이 같은 판을 쓴다 — 잔디 산수를 두 곳에 두면 「최신 주가 왼쪽」 같은 규약을 두 번 밟는다.
 */
export function GrassPanel({ graph }: { graph: ContributionGraph }) {
  const months = monthLabelPositions(graph.monthLabels, CELL_SIZE);

  return (
    // 스크롤 상자가 링의 바깥 4.5px을 자르지 않게 좌우·아래에 여백을 둔다(overflow가 그림자를 자른다).
    // 위쪽은 안쪽 상자의 `paddingTop: 22`(월 라벨 자리)가 대신한다.
    <div className="no-scrollbar" style={{ overflowX: 'auto', padding: '0 5px 8px' }}>
      {/* 라벨은 격자 폭 안에서 절대 배치된다 — inline-block이라 이 상자가 격자만큼만 넓어진다. */}
      <div style={{ position: 'relative', display: 'inline-block', paddingTop: 22 }}>
        <div aria-hidden="true" style={{ position: 'absolute', top: 0, left: 0, height: 20 }}>
          {months.map(({ label, left }) => (
            <span
              key={label}
              style={{ position: 'absolute', left, fontSize: 14, color: 'var(--adaptiveGrey600, #5B5A4D)', whiteSpace: 'nowrap' }}
            >
              {label}
            </span>
          ))}
        </div>
        <GrassGrid weeks={graph.weeks} cellSize={CELL_SIZE} today={latestDate(graph.weeks)} />
      </div>
    </div>
  );
}

/**
 * 판의 오늘 — 서버가 미래 칸을 `date: null`로 비워 보내므로(`ContributionGraphBuilder`) 가장 늦은 날짜가 곧
 * <b>유저 타임존의 오늘</b>이다. 기기 시계를 읽으면 자정 언저리에 서버와 날이 갈려 링이 엉뚱한 칸에 선다.
 * ISO 날짜라 문자열 비교가 곧 날짜 비교다. 날짜가 하나도 없으면(빈 판) `undefined` — 링을 세우지 않는다.
 */
export function latestDate(weeks: ContributionDay[][]): string | undefined {
  let latest: string | undefined;
  for (const week of weeks) {
    for (const { date } of week) {
      if (date !== null && (latest === undefined || date > latest)) latest = date;
    }
  }
  return latest;
}

/** 잔디 카드 — 섹션 카드 그대로, 제목 바로 아래 붙으므로 위 여백만 뺀다. */
export const GRASS_CARD: CSSProperties = { ...sectionStyle, marginTop: 0 };

/**
 * 화면 맨 위 스탯 줄 — 연속 · 읽은 날 · 총 시간.
 *
 * <p>여기 있던 식물 성장 카드(땅→새싹→꽃→나무 + 진행 막대)는 폐기했다. 사다리가 주는 것은
 * 「다음 단계까지 N일」이라는 재촉뿐이었고, 정작 이 화면이 답해야 할 세 수는 카드 안팎으로
 * 흩어져 있었다.
 *
 * <p>Soft(PR-3): 세로선 칸막이 → <b>타일 셋</b>(시안 Soft-History). 「연속」은 버터(정보색 — 모드 무관),
 * 나머지 둘은 부푼 면의 축소판이다. 그림자는 시안 값 리터럴이다 — `--puffShadow`(10/24px)를 그대로 쓰면
 * 12px 간격의 이웃 타일까지 번진다. 화면당 타일 3개라 큰 흐림 예산 안이다(§6 「스탯/2열 타일 ≤ 3」).
 *
 * <p>총 시간 칸만 넓다: 「11시간 5분」은 「4일」의 두 배가 넘는다. 그래도 분이 붙는 대부분의 값에서 띄어쓰기로
 * 두 줄로 접힌다(`keep-all`, 390폭 실측: 「1시간 30분」도) — 한 줄 고정이면 타일 밖으로 삐져나간다.
 * 사용자 결정으로 허용했다(리뷰 I-1, 2026-09-23) — 줄은 공백 자리(「42시간」/「30분」)에서만 갈린다.
 */
export function StatStrip({
  graph,
  /** 가운데 칸의 이름 — 공부 기록은 「공부한 날」로 바꿔 쓴다. 기본값이 독서의 옛 문구다(렌더 불변). */
  activeDaysLabel = '읽은 날',
}: {
  graph: ContributionGraph;
  activeDaysLabel?: string;
}) {
  const cells = [
    { label: '연속', value: `${graph.currentStreak}일`, flex: 1, butter: true },
    { label: activeDaysLabel, value: `${graph.activeDays}일`, flex: 1, butter: false },
    { label: '총 시간', value: formatDuration(graph.totalSeconds), flex: 1.5, butter: false },
  ];

  return (
    <div style={{ display: 'flex', gap: 12, marginTop: 14 }}>
      {cells.map(({ label, value, flex, butter }) => (
        <div key={label} data-stat-tile="" style={{ ...(butter ? STAT_TILE_BUTTER : STAT_TILE), flex, minWidth: 0 }}>
          <Text
            typography="st12"
            color={butter ? 'var(--butterInk, #5A4A14)' : 'grey600'}
            style={{ display: 'block', fontSize: 15 }}
          >
            {label}
          </Text>
          {/* 세리프 24 — 라벨(15)과 크기·서체 두 축으로 갈린다(시안 Soft-History). */}
          <Text
            typography="st10"
            fontWeight="bold"
            style={{ ...SERIF_VALUE, display: 'block', fontSize: 24, marginTop: 2, wordBreak: 'keep-all' }}
          >
            {value}
          </Text>
        </div>
      ))}
    </div>
  );
}

/** 스탯 타일 공통 꼴 + 윗변 1px 빛(부푼 면의 흰 하이라이트를 타일 크기로 줄인 것 — 홈 남은 시간 타일과 같은 값). */
const STAT_TILE_BASE: CSSProperties = { padding: '10px 14px', borderRadius: 20 };
const STAT_TILE_BUTTER: CSSProperties = {
  ...STAT_TILE_BASE,
  background: 'var(--butterBg, #F1E6C3)',
  boxShadow: 'inset 0 1px 0 rgba(255, 255, 255, 0.7), 5px 5px 12px rgba(150, 125, 60, 0.14)',
};
const STAT_TILE: CSSProperties = {
  ...STAT_TILE_BASE,
  background: 'var(--adaptiveGrey100, #FBF9F4)',
  boxShadow: 'var(--tileShadow), -4px -4px 10px rgba(255, 255, 255, 0.9)',
};

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
 * 하루 막대의 길이(0~100) — 기준은 <b>그날 유효했던 하루 목표</b>다.
 *
 * <p>전에는 「그 달 최대」로 쟀다. 그러면 목표를 채운 날도 같은 달에 더 오래 읽은 날이 하나 있으면
 * 짧게 그려진다 — 막대가 「내가 오늘 할 일을 했나」가 아니라 「그 달 누구보다 길었나」를 답하고 있었다.
 *
 * <p>기준이 0(목표 없음)이면 읽은 날은 가득, 안 읽은 날은 0이다 — 잔디 `levelFor`가 목표 0인 날을
 * lv4로 치는 것과 같은 규칙이고, 덤으로 0으로 나눠 `width: NaN%`가 되는 일도 없다.
 *
 * <p><b>내림이라야 한다.</b> 반올림이면 3588초/3600초가 100%로 그려지는데, 잔디
 * `ContributionGraphBuilder.levelFor`는 `seconds < goal`이라 그 날을 lv3으로 친다 — 같은 화면의 두 그림이
 * 「목표를 채웠나」에 다른 답을 한다. 내림으로 두면 **가득 찬 막대 ⇔ 잔디 lv4**가 참이 된다.
 */
export function barPercent(seconds: number, maxSeconds: number): number {
  if (maxSeconds <= 0) return seconds > 0 ? 100 : 0;
  return Math.min(100, Math.floor((seconds / maxSeconds) * 100));
}

/** 펼친 하루의 마지막 줄 — 막대를 무엇에 견줘 쟀는지. 0·미상(옛 서버 응답)은 「목표 없음」. */
export function goalLabel(goalSeconds: number | undefined): string {
  return goalSeconds === undefined || goalSeconds <= 0 ? '그날 목표 없음' : `그날 목표 ${formatDuration(goalSeconds)}`;
}

/**
 * 그날의 표지 더미 — 앞에 쌓을 책과 넘친 권수.
 *
 * <p>전에는 제목을 쉼표로 이어 붙이고 `nowrap + ellipsis`로 잘랐다. 두 권만 돼도
 * 「미움받을 용기, 사피…」가 되는데, <b>몇 권인지도 무슨 책인지도 안 남는</b> 잘림이었다.
 * 표지로 옮기면 권수가 자리로 보이고, 넘치는 만큼은 숫자로 <b>밝혀서</b> 뺀다.
 *
 * <p>서버가 오래 읽은 순으로 주므로 <b>맨 앞이 그날을 대표하는 책</b>이다 — 여기서 다시 정렬하지 않는다.
 *
 * <p>더미에 서는 <b>칸은 최대 {@code max}개</b>다 — 넘칠 땐 마지막 칸을 권수 타일에 내주므로 표지는
 * {@code max - 1}장만 쌓인다. 넘친 만큼까지 표지로 세우면 칸이 넓어져 옆의 막대를 잡아먹는다.
 */
export function coverStack(books: BookRead[], max = 3): { shown: BookRead[]; more: number } {
  if (books.length <= max) return { shown: books, more: 0 };
  return { shown: books.slice(0, max - 1), more: books.length - (max - 1) };
}

/**
 * 펼칠 수 있는 날인가 — 기준은 <b>「측정이 한 건이라도 있는가」</b>다(R2).
 *
 * <p>펼침은 이제 「무슨 책을」에 더해 <b>책을 붙이는 자리</b>다 — 책을 안 고른 날이야말로 펼쳐야 한다(옛 기준
 * 「책이 한 권이라도」는 정확히 붙일 게 있는 날을 닫아 두었다). `sessions`를 안 주는 옛 서버 응답은 좌표가 없어
 * 붙일 수도 없으니 펼치지 않는다.
 */
export function isExpandable(day: { sessions?: SessionRow[] }): boolean {
  return (day.sessions ?? []).length >= 1;
}

/**
 * 측정 줄 오른쪽에 무엇을 세우나. 책 있는 줄은 `'change'`(그 책이 곧 후보라 후보 수와 무관), 책 없는 줄은
 * 붙일 후보가 있으면 `'attach'`, 0권이면 `'none'` — 빈 시트를 여는 막다른 문 대신 누를 수 없는 안내 글자를 둔다.
 */
export function sessionAction(row: SessionRow, candidateCount: number): 'attach' | 'change' | 'none' {
  if (row.bookId !== null) return 'change';
  return candidateCount > 0 ? 'attach' : 'none';
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
export function MonthlyRecords({
  months,
  candidateCount = 0,
  onAssign = () => {},
}: {
  months: MonthlySection[];
  candidateCount?: number;
  onAssign?: (row: SessionRow) => void;
}) {
  // 한 번에 하나만 펼친다 — 여럿이 동시에 열리면 목록이 벽이 되고, 하루를 보러 온 사람이 스크롤을 잃는다.
  const [openDate, setOpenDate] = useState<string | null>(null);

  if (months.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 28 }}>
        아직 독서 기록이 없어요. 홈에서 측정을 시작해 보세요.
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
                <DayRow
                  key={day.date}
                  day={day}
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

/**
 * 월 머리 — 달 이름과 그 달 합계가 한 줄로 머리를 이룬다, 선은 그 줄 아래를 지난다(시안 2d). 공부 기록이 같이 쓴다.
 *
 * <p>합계는 16 굵게 세이지(시안 Soft-History) — 달 이름(세리프 20)과 크기로 층을 두되 서체는 같이 간다.
 */
export function MonthHead({ month, totalSeconds }: { month: string; totalSeconds: number }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        margin: '20px 0 12px',
        paddingBottom: 10,
        borderBottom: SECTION_RULE,
      }}
    >
      <Text typography="st10" fontWeight="bold" style={{ ...SERIF_VALUE, fontSize: 20 }}>
        {formatMonthTitle(month)}
      </Text>
      <Text typography="st12" fontWeight="bold" color="blue700" style={{ ...SERIF_VALUE, fontSize: 16 }}>
        {formatDuration(totalSeconds)}
      </Text>
    </div>
  );
}

/**
 * 그 달의 날짜 줄들을 담는 <b>눌린 쟁반</b> 하나(시안 Soft-History). 줄 사이 구분선 대신 눌린 면 + 간격이 가른다.
 *
 * <p>월 쟁반을 다시 부푼 카드로 감싸지 않는다(시안은 감쌌다): 카드 패딩 18×2가 하루 막대 칸을 60px 아래로
 * 깎아, 막대로 날을 견주는 것 자체가 흐려진다. 쟁반이 곧 캔버스에 파인 묶음이다.
 */
export const DAY_TRAY: CSSProperties = {
  ...DENT,
  display: 'flex',
  flexDirection: 'column',
  gap: 12,
  padding: 14,
};

/**
 * 하루 한 줄의 고정 격자 — 날짜 · 표지 더미 · 막대 · 시간 · 손잡이.
 *
 * <p><b>고정 폭이라야 한다.</b> 전에는 flex라 표지 개수(1~4장)와 시간 글자 폭(「45분」~「3시간 20분」)이
 * 행마다 달라 막대의 <b>시작점과 끝점이 둘 다 흔들렸다</b> — 같은 퍼센트가 행마다 다른 픽셀로 그려지니
 * 막대 길이로 날을 견주는 것 자체가 거짓이었다. 격자로 못 박으면 구조적으로 어긋날 수가 없다.
 *
 * <p>손잡이 칸은 <b>펼칠 수 없는 날에도 비워 남긴다</b> — 그 칸이 사라지면 시간의 오른쪽 끝이 밀린다.
 *
 * <p>줄 사이 구분선은 없다 — 눌린 쟁반({@link DAY_TRAY})의 간격이 가른다(Soft PR-3). 시간 칸 84px은 세리프 17의
 * 「1시간 15분」(≈82px)이 들어가는 폭이다. 「10시간 30분」처럼 더 긴 값은 오른쪽 정렬이라 빈 막대 쪽으로 넘친다.
 */
const ROW_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '50px 56px minmax(0, 1fr) 84px 14px',
  alignItems: 'center',
  columnGap: 8,
  width: '100%',
  padding: 0,
};

/**
 * 하루 막대 — 트랙(카드 면, 16px 알약) 위에 2px 띄워 앉은 세이지 그라데이션(시안 Soft-History). 폭은
 * {@link barPercent} 그대로다.
 *
 * <p>단색 <b>토큰</b>이다(톤 조율 A — 시안의 그라데이션은 재료를 하나 더 얹었다): 공부 기록이 같은 막대를 쓰는데 `body.study-mode`가
 * 이 토큰을 파랑으로 갈아 끼운다 — 리터럴이면 공부 화면의 이 막대만 세이지로 남는다. 옛 막대가
 * `LEVEL_COLORS[2]`를 쓰던 이유와 같다.
 */
export const BAR_TRACK: CSSProperties = {
  height: 16,
  borderRadius: 999,
  background: 'var(--adaptiveGrey100, #FBF9F4)',
  padding: 2,
  boxSizing: 'border-box',
};
export const BAR_FILL = 'var(--adaptiveBlue500, #5B7F55)';

/** 트랙 위 막대 한 줄 — `barPercent`가 낸 퍼센트를 받는다. 0%면 빈 트랙만 남는다. */
export function DayBar({ percent }: { percent: number }) {
  return (
    <div aria-hidden="true" style={BAR_TRACK}>
      <div style={{ width: `${percent}%`, height: 12, borderRadius: 999, background: BAR_FILL }} />
    </div>
  );
}

/** 버튼의 기본 꼴을 지운다 — 손잡이지 알약이 아니다. `ROW_GRID`를 뒤에 펴서 격자를 입힌다. */
export const BUTTON_RESET: CSSProperties = {
  border: 'none',
  background: 'none',
  color: 'inherit',
  textAlign: 'left',
  cursor: 'pointer',
};

/**
 * 하루 한 줄 — 날짜 · 표지 더미 · 막대 · 시간.
 *
 * <p>전에는 표지를 <b>옆으로 나열</b>했다. 그래서 네 권을 읽은 날은 세 장 + 「+1」로 늘어서기만 하고,
 * <b>어느 책을 오래 읽었는지</b>는 어디에도 없었다 — 그날 총합 하나뿐이었다. 겹쳐 쌓으면 자리를 덜 쓰면서
 * 「여러 권」이 형태로 읽힌다(사용자 요청 2026-08-20). 눌러 펼치면 측정 한 건씩(시각·시간·책)이 서고
 * 거기서 책을 붙이거나 바꾼다(R2).
 *
 * <p>펼침 상태를 스스로 들지 않고 위에서 받는다 — 한 번에 하나만 열려야 하는데, 각 줄이 제 상태를 들면
 * 그 규칙을 아무도 강제할 수 없다. 덕분에 정적 렌더 하니스(클릭이 안 도는)에서도 펼친 꼴을 계측할 수 있다.
 */
export function DayRow({
  day,
  expanded,
  onToggle,
  candidateCount = 0,
  onAssign = () => {},
}: {
  day: DailyRecord;
  expanded: boolean;
  onToggle: () => void;
  candidateCount?: number;
  onAssign?: (row: SessionRow) => void;
}) {
  const expandable = isExpandable(day);
  const summary = (
    <>
      <DayDate date={day.date} />

      <CoverPile books={day.books} />

      {/* 기준은 그날 목표다(서버가 실어 준다). 롤링 배포 중 옛 서버 응답엔 그 필드가 없어 0으로 떨어진다
          — 읽은 날은 가득. */}
      <DayBar percent={barPercent(day.totalSeconds, day.goalSeconds ?? 0)} />

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
          {/* 값이 아니라 말이라 비세리프로 둔다 — 요일 줄과 같은 판단(위계 테스트의 비세리프 목록). */}
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 10 }}>
            {goalLabel(day.goalSeconds)}
          </Text>
        </div>
      )}
    </div>
  );
}

/** 날짜 칸 — 세리프 17 날짜 위, 14 요일 아래(시안 Soft-History). 공부 기록 줄이 같이 쓴다. */
export function DayDate({ date }: { date: string }) {
  return (
    <div>
      <Text typography="st11" style={{ display: 'block', lineHeight: 1.2, ...SERIF_VALUE, fontSize: 17 }}>
        {formatRecordDate(date)}
      </Text>
      <Text typography="st12" color="grey600" style={{ display: 'block' }}>
        {formatWeekday(date)}
      </Text>
    </div>
  );
}

/**
 * 그날 합계 — 세리프 17 잉크색. 이 줄이 대답하는 값이 이것이라 흐린 색이면 왼쪽 날짜보다 뒤로 물러난다.
 *
 * <p>정렬은 감싸는 요소가 한다 — TDS `Text`는 style의 `text-align`을 <b>걸러 낸다</b>(인라인 스타일에 아예 안
 * 실려서 조용히 왼쪽 정렬로 남는다). 오른쪽 정렬이라야 「10시간 30분」 같은 긴 값이 넘칠 때 빈 막대 쪽으로
 * 넘치지, 옆의 손잡이를 침범하지 않는다.
 */
export function DayTotal({ seconds }: { seconds: number }) {
  return (
    <div style={{ textAlign: 'right' }}>
      <Text typography="st11" style={{ whiteSpace: 'nowrap', ...SERIF_VALUE, fontSize: 17 }}>
        {formatDuration(seconds)}
      </Text>
    </div>
  );
}

/**
 * 더미에서 뒷장이 내다보는 폭 — 20px 표지의 12px을 남긴다.
 *
 * <p>처음엔 7px만 남겼는데(겹침 -13), 목 모드에서 <b>가운데 장이 아예 안 보였다</b> — 앞장이 왼쪽을,
 * 권수 타일이 오른쪽을 덮어 남는 게 없었다. 12px이면 첫 글자의 대부분이 나와 「무슨 책이 몇 권」이 읽힌다.
 */
const PILE_OVERLAP = -8;

/**
 * 더미의 한 장.
 *
 * <p>테두리는 더미가 앉은 바탕색이다 — 지금은 눌린 쟁반({@link DAY_TRAY})이라 `--softDent`(blur 0 링).
 * 이게 없으면 색이 비슷한 책 둘이 붙어 <b>한 덩어리로 뭉쳐</b> 몇 권인지가 안 보인다.
 */
const PILE_CARD: CSSProperties = {
  position: 'relative',
  display: 'flex',
  borderRadius: 4,
  boxShadow: '0 0 0 1.5px var(--softDent, #EAE5D9)',
};

/**
 * 겹쳐 쌓은 표지 더미 — <b>앞장이 위</b>다.
 *
 * <p>서버가 오래 읽은 순으로 주므로 맨 앞이 그날을 대표하는 책이다. 그러니 그 한 장은 온전히 보여야 한다
 * (그냥 쌓으면 나중 장이 위로 덮여 대표 표지가 제일 많이 가려진다).
 *
 * <p>넘친 권수 타일은 <b>더미에 겹치지 않고</b> 옆에 선다. 겹쳐서 맨 위에 올리면 그 아래 장이 양쪽에서
 * 눌려 사라지고, 아래에 깔면 「+2」 글자가 잘려 셀 수 없는 표시가 된다 — 애초에 책이 아니니 더미 밖이 맞다.
 */
function CoverPile({ books }: { books: BookRead[] }) {
  const { shown, more } = coverStack(books);

  // 책을 안 고른 날도 칸을 남긴다 — 자리가 무너지면 아래 행의 막대 시작점이 어긋난다.
  if (shown.length === 0) {
    return (
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
    );
  }

  return (
    <span style={{ display: 'flex', alignItems: 'center' }}>
      {shown.map((book, index) => (
        <span
          key={book.title}
          style={{ ...PILE_CARD, marginLeft: index === 0 ? 0 : PILE_OVERLAP, zIndex: shown.length - index }}
        >
          <BookCover url={book.coverUrl} title={book.title} width={20} />
        </span>
      ))}
      {more > 0 && (
        <span
          style={{
            ...PILE_CARD,
            marginLeft: 3,
            width: 20,
            height: 28,
            alignItems: 'center',
            justifyContent: 'center',
            background: 'var(--adaptiveGrey200, #E4DDD0)',
            color: 'var(--adaptiveGrey700, #57534A)',
            fontSize: 14,
          }}
        >
          +{more}
        </span>
      )}
    </span>
  );
}

/**
 * 펼친 날의 측정 줄 — 한 건씩 「시각/시간 · 제목 · 손잡이」(R2). 독서·공부 기록이 같이 쓴다.
 *
 * <p><b>훅 없는 순수 컴포넌트</b>다 — 테스트가 함수로 불러 엘리먼트 트리에서 라벨↔핸들러를 잰다(정적 렌더는
 * `onClick`을 못 본다). 상태·시트는 위(App)가 든다.
 *
 * <p>격자는 3열이다: 360px 폭 쟁반 안 가용 폭은 292px이라 네 값(시각·시간·제목·버튼)을 한 줄에 두면 제목 칸이
 * ≈50px로 남아 두 글자면 잘린다. 시간을 시각 밑으로 쌓아 제목 칸을 ≥120px로 지킨다(설계 §4.5).
 * 들여쓰기·세로선은 없다 — 눌린 쟁반이 이미 「그날에 딸린 것」을 묶는다.
 */
export function SessionLines({
  rows,
  candidateCount,
  onAssign,
}: {
  rows: SessionRow[];
  candidateCount: number;
  onAssign: (row: SessionRow) => void;
}) {
  return (
    <div style={SESSION_GRID}>
      {rows.map((row) => {
        const action = sessionAction(row, candidateCount);
        return (
          // 줄은 격자에 녹는다(display: contents) — 줄마다 격자를 따로 두면 max-content 시각 칸 폭이 줄마다 달라
          // 제목 시작점이 흔들렸다(목 모드 360px 스크린샷). 한 격자라야 세 칸이 모든 줄에서 같은 자리에 선다.
          <div key={row.id} data-session-row="" style={{ display: 'contents' }}>
            <div>
              {/* 값이 아니라 표지라 비세리프다 — 그날의 답(하루 합계, 세리프 17)은 접힌 줄에 있다.
                  크기는 Soft 눈금(보조 14 · 최소 13)이다 — 설계 초안의 13/12는 「최소 13」 규칙과 부딪혀 한 단 올렸다. */}
              <Text typography="st12" style={{ display: 'block', fontSize: 14, whiteSpace: 'nowrap' }}>
                {row.manual ? MANUAL_LABEL : `${row.start}–${row.end}`}
              </Text>
              <Text typography="st12" color="grey600" style={{ display: 'block', fontSize: 13, whiteSpace: 'nowrap' }}>
                {sessionLength(row.seconds)}
              </Text>
            </div>

            <div data-session-title="" style={{ minWidth: 0 }}>
              <Text
                typography="st11"
                color={row.bookTitle === null ? 'grey600' : undefined}
                style={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {row.bookTitle ?? '책 없음'}
              </Text>
              {action === 'none' && (
                <Text typography="st12" color="grey600" style={{ display: 'block', fontSize: 13, wordBreak: 'keep-all' }}>
                  서재에 책을 담으면 붙일 수 있어요
                </Text>
              )}
            </div>

            {action === 'none' ? (
              <span aria-hidden="true" />
            ) : (
              <Button
                variant="weak"
                size="small"
                aria-label={`${row.manual ? MANUAL_LABEL : `${row.start}–${row.end}`} 측정 ${action === 'attach' ? '책 붙이기' : '책 바꾸기'}`}
                onClick={() => onAssign(row)}
              >
                {action === 'attach' ? '책 붙이기' : '바꾸기'}
              </Button>
            )}
          </div>
        );
      })}
    </div>
  );
}

/** 측정 줄 격자 — 시각/시간 스택 · 제목 · 손잡이. 모든 줄이 이 한 격자를 나눠 쓴다. */
const SESSION_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'max-content minmax(0, 1fr) max-content',
  alignItems: 'center',
  columnGap: 8,
  rowGap: 12,
};

/** 수동 기록 줄의 시각 자리 — 그 시각은 서버 앵커(과거 날짜 00:00)라 찍으면 거짓이다. 웹 기록과 같은 말. */
const MANUAL_LABEL = '직접 기록';

/** 줄의 걸린 시간 — 60초 미만은 「1분 미만」(「45초」는 측정 줄에서 소음이다, 웹 기록과 같은 말). */
function sessionLength(seconds: number): string {
  return seconds < 60 ? '1분 미만' : formatDuration(seconds);
}

/**
 * 여닫는 손잡이.
 *
 * <p>도는 것은 `transform`뿐이다 — 합성만 유발해 표지를 다시 래스터화하지 않는다(T-176에서 발광
 * `box-shadow` 애니메이션이 표지를 초당 60번 다시 그리게 했던 자리다).
 */
export function Chevron({ open }: { open: boolean }) {
  return (
    <svg
      width="10"
      height="10"
      viewBox="0 0 10 10"
      aria-hidden="true"
      style={{
        color: 'var(--adaptiveGrey600, #6F6A5E)',
        transform: open ? 'rotate(180deg)' : 'none',
        transition: 'transform 0.15s',
      }}
    >
      <path d="M2 4 L5 7 L8 4" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * 색 농도 범례 — 잔디가 무슨 뜻인지 화면 어디에도 없었다. 웹 `.grass-legend`와 같은 말을 쓴다.
 *
 * @param manual 「직접 채움」 스와치를 함께 둘지. 공부 기록은 수동 입력이 없어 false로 뺀다
 *               (기본값이 독서의 옛 동작이라 그쪽 렌더는 불변이다).
 */
export function Legend({ manual = true }: { manual?: boolean } = {}) {
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
      {manual && (
        <>
          <span style={{ ...swatch, marginLeft: 10, background: LEVEL_COLORS[2], outline: MANUAL_OUTLINE }} />
          <Text typography="st12" color="grey600">
            직접 채움
          </Text>
        </>
      )}
      {/* 오늘 칸 링의 설명 — 격자의 그 칸과 같은 링을 두른다(링이 스와치 밖 4.5px까지 나가 간격을 더 둔다). */}
      <span style={{ ...swatch, marginLeft: 14, background: LEVEL_COLORS[2], boxShadow: TODAY_RING }} />
      <Text typography="st12" color="grey600" style={{ marginLeft: 4 }}>
        오늘
      </Text>
    </div>
  );
}
