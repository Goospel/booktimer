import type { CSSProperties } from 'react';
import { useEffect, useState } from 'react';

import type { BookRead, ContributionDay, ContributionGraph, DailyRecord, MonthlySection } from '../api';
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
 */
export function History({ graph }: { graph: ContributionGraph }) {
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
        if (alive) setSections(r.months);
      })
      .catch((e: Error) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, []);

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

      {sections !== null && <MonthlyRecords months={sections} />}
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
              style={{ position: 'absolute', left, fontSize: 14, color: 'var(--adaptiveGrey600, #4E5A4B)', whiteSpace: 'nowrap' }}
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

/** 스탯 타일 공통 꼴 + 윗변 1px 빛(부푼 면의 흰 하이라이트를 타일 크기로 줄인 것 — 홈 2열 타일과 같은 값). */
const STAT_TILE_BASE: CSSProperties = { padding: '10px 14px', borderRadius: 20 };
const STAT_TILE_BUTTER: CSSProperties = {
  ...STAT_TILE_BASE,
  background: 'var(--butterBg, #F2E8C6)',
  boxShadow: 'inset 0 1px 0 rgba(255, 255, 255, 0.7), 5px 5px 12px rgba(160, 140, 70, 0.18)',
};
const STAT_TILE: CSSProperties = {
  ...STAT_TILE_BASE,
  background: 'var(--adaptiveGrey100, #F9FBF7)',
  boxShadow: '5px 5px 12px rgba(94, 122, 90, 0.16), -4px -4px 10px rgba(255, 255, 255, 0.95)',
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

/** 펼친 하루의 한 줄 — 책 한 권, 또는 마지막의 「책 안 고른 기록」. */
export interface DayBookRow extends BookRead {
  /** 책이 아니라 차액 줄이면 true — 표지 대신 빈 칸, 회색으로 눌러 그린다. */
  unassigned: boolean;
}

/** 「책 안 고른 기록」 줄의 이름 — 책이 아니라서 제목 자리에 설명이 온다. */
const UNASSIGNED_LABEL = '책 안 고른 기록';

/**
 * 펼쳤을 때 세울 줄들 — 책들 + (남으면) 「책 안 고른 기록」.
 *
 * <p>책을 안 고르고 잰 세션은 서버 `books`에 안 잡히지만 `totalSeconds`에는 남아 있다. 그 차액을 그냥
 * 버리면 <b>펼친 시간을 다 더해도 접힌 줄의 총합과 안 맞는다</b> — 사용자가 산수를 해 보는 순간 화면이
 * 거짓말한 게 된다. 그래서 남는 만큼을 마지막 줄로 밝힌다. 차액이 없으면 그 줄을 안 만든다(「0초」 줄이 된다).
 */
export function bookRows(day: DailyRecord): DayBookRow[] {
  const rows: DayBookRow[] = day.books.map((book) => ({ ...book, unassigned: false }));
  const rest = day.totalSeconds - day.books.reduce((sum, book) => sum + book.seconds, 0);
  if (rest > 0) {
    rows.push({ title: UNASSIGNED_LABEL, coverUrl: null, seconds: rest, unassigned: true });
  }
  return rows;
}

/**
 * 펼칠 수 있는 날인가 — 기준은 <b>「책이 한 권이라도 있는가」</b>다.
 *
 * <p>접힌 줄에는 제목이 없고 20px 표지뿐이라, 한 권만 읽은 날도 펼쳐야 <b>무슨 책인지</b>가 나온다.
 * 특히 시리즈물은 권마다 표지가 같아서, 책이 바뀌어도 접힌 줄로는 그게 안 보였다(사용자 보고 2026-09-08).
 * 전 기준 「펼치면 줄이 둘 이상인가」는 펼침의 값을 <b>시간</b>으로만 셌던 셈이다 — 제목도 값이다.
 *
 * <p>책을 아예 안 고른 날은 그대로 안 펼친다 — 펼쳐도 「책 안 고른 기록」 한 줄이 그날 전부라 새로 보이는 게 없다.
 */
export function isExpandable(day: DailyRecord): boolean {
  return day.books.length >= 1;
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
 * <p>양 끝 색이 <b>토큰</b>이다(시안은 `#8FB087 → #5B7F55` 리터럴): 공부 기록이 같은 막대를 쓰는데 `body.study-mode`가
 * 이 두 토큰을 파랑 사다리로 갈아 끼운다 — 리터럴이면 공부 화면의 이 막대만 세이지로 남는다. 옛 막대가
 * `LEVEL_COLORS[2]`를 쓰던 이유와 같다.
 */
export const BAR_TRACK: CSSProperties = {
  height: 16,
  borderRadius: 999,
  background: 'var(--adaptiveGrey100, #F9FBF7)',
  padding: 2,
  boxSizing: 'border-box',
};
export const BAR_FILL = 'linear-gradient(90deg, var(--adaptiveBlue400, #8FB087), var(--adaptiveBlue500, #5B7F55))';

/** 트랙 위 막대 한 줄 — `barPercent`가 낸 퍼센트를 받는다. 0%면 빈 트랙만 남는다. */
export function DayBar({ percent }: { percent: number }) {
  return (
    <div aria-hidden="true" style={BAR_TRACK}>
      <div style={{ width: `${percent}%`, height: 12, borderRadius: 999, background: BAR_FILL }} />
    </div>
  );
}

/** 버튼의 기본 꼴을 지운다 — 손잡이지 알약이 아니다. `ROW_GRID`를 뒤에 펴서 격자를 입힌다. */
const BUTTON_RESET: CSSProperties = {
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
 * 「여러 권」이 형태로 읽히고, 눌러 펼치면 책마다 얼마나 읽었는지가 나온다(사용자 요청 2026-08-20).
 *
 * <p>펼침 상태를 스스로 들지 않고 위에서 받는다 — 한 번에 하나만 열려야 하는데, 각 줄이 제 상태를 들면
 * 그 규칙을 아무도 강제할 수 없다. 덕분에 정적 렌더 하니스(클릭이 안 도는)에서도 펼친 꼴을 계측할 수 있다.
 */
export function DayRow({
  day,
  expanded,
  onToggle,
}: {
  day: DailyRecord;
  expanded: boolean;
  onToggle: () => void;
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
      {expanded && <BookLines rows={bookRows(day)} goalSeconds={day.goalSeconds} />}
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
  boxShadow: '0 0 0 1.5px var(--softDent, #E6ECE3)',
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
 * 펼친 책 줄들 — 무슨 책을 얼마나.
 *
 * <p>막대 기준은 <b>그날 가장 오래 읽은 줄</b>이다 — 총합을 기준으로 재면 여러 권인 날은 죄다 짧은
 * 막대가 돼 견줄 수가 없다. 하루 막대(그날 목표를 얼마나 채웠나)와 책 막대(그날 안에서의 비중)는
 * 서로 다른 것을 잰다.
 *
 * <p>맨 아래 한 줄은 하루 막대의 기준을 밝힌다 — 막대만 보면 무엇에 견줘 쟀는지 알 수 없다.
 */
function BookLines({ rows, goalSeconds }: { rows: DayBookRow[]; goalSeconds?: number }) {
  const longest = rows.reduce((max, row) => Math.max(max, row.seconds), 0);
  const name: CSSProperties = { display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' };

  return (
    // 왼쪽 세로선은 여백의 인용 줄과 같은 값이다 — 「위 줄에 딸린 것」이라는 말을 앱이 한 가지로 한다.
    // 그 줄이 B에서 세이지 200으로 옮겨 갔으므로 여기도 함께 옮긴다(안 옮기면 이 주석이 거짓이 된다).
    // 시안 2d는 `rgba(110,138,106,.35)`로 인용(.5)보다 한 톤 옅지만, 15% 알파 차이로 두 자리를
    // 갈라 두면 「한 가지로 한다」는 규약만 잃는다.
    // 들여쓰기 58 = 날짜 칸(50) + 간격(8) — 가이드라인이 표지 더미 칸 시작점에 선다(쟁반 안이라 옛 70은 막대를 깎았다).
    <div
      style={{
        margin: '10px 0 0 58px',
        paddingLeft: 16,
        borderLeft: '2px solid var(--adaptiveBlue200, #B6C9AE)',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
    >
      {rows.map((row) => (
        <div
          key={row.title}
          style={{
            display: 'grid',
            gridTemplateColumns: '26px minmax(0, 1fr) 36px 72px',
            alignItems: 'center',
            columnGap: 8,
          }}
        >
          {row.unassigned ? (
            <span
              aria-hidden="true"
              style={{
                display: 'block',
                width: 26,
                height: 36,
                borderRadius: 3,
                border: '1px dashed var(--adaptiveGrey200, #E4DDD0)',
              }}
            />
          ) : (
            <BookCover url={row.coverUrl} title={row.title} width={26} />
          )}

          {/* 이름 16 — 시안 펼친 줄. 책 안 고른 줄은 책이 아니라 설명이라 흐리게 눌러 그린다. */}
          <Text typography="st11" color={row.unassigned ? 'grey600' : undefined} style={{ ...name, fontSize: 16 }}>
            {row.title}
          </Text>

          <div
            aria-hidden="true"
            style={{
              width: `${barPercent(row.seconds, longest)}%`,
              height: 4,
              borderRadius: 2,
              background: row.unassigned ? 'var(--adaptiveGrey200, #E4DDD0)' : LEVEL_COLORS[1],
            }}
          />

          <div style={{ textAlign: 'right' }}>
            <Text typography="st11" color="grey700" style={{ whiteSpace: 'nowrap', ...SERIF_VALUE, fontSize: 16 }}>
              {formatDuration(row.seconds)}
            </Text>
          </div>
        </div>
      ))}

      {/* 값이 아니라 말이라 비세리프로 둔다 — 요일 줄과 같은 판단(위계 테스트의 비세리프 목록). */}
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
        {goalLabel(goalSeconds)}
      </Text>
    </div>
  );
}

/**
 * 여닫는 손잡이.
 *
 * <p>도는 것은 `transform`뿐이다 — 합성만 유발해 표지를 다시 래스터화하지 않는다(T-176에서 발광
 * `box-shadow` 애니메이션이 표지를 초당 60번 다시 그리게 했던 자리다).
 */
function Chevron({ open }: { open: boolean }) {
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
