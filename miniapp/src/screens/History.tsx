import type { CSSProperties } from 'react';
import { useEffect, useState } from 'react';

import type { BookRead, ContributionGraph, DailyRecord, MonthlySection } from '../api';
import { fetchHistory } from '../api';
import { CACHE_HISTORY, cacheGet, cachePut } from '../cache';
import { formatDuration, subjectParticle } from '../format';
import { BookCover, ErrorMessage, GrassGrid, LEVEL_COLORS, MANUAL_OUTLINE, PENCIL_FRAME, SECTION_RULE, SERIF_VALUE, Screen, SectionTitle, Text, monthLabelPositions } from '../ui';

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
      <GrowthCard graph={graph} />

      {/* 연속은 위 성장 카드가 이미 말한다 — 여기 다시 두면 한 화면에서 같은 숫자를 두 번 읽게 된다. */}
      <div style={{ display: 'flex', gap: 12, marginTop: 14 }}>
        <Stat label="읽은 날" value={`${graph.activeDays}일`} />
        <Stat label="총 시간" value={formatDuration(graph.totalSeconds)} />
      </div>

      {/* 시안 2d — 이 화면이 답하는 것의 이름이라 값으로 조판한다(세리프 20). */}
      <SectionTitle style={{ margin: '24px 0 8px', ...SERIF_VALUE, fontSize: 20 }}>읽은 날짜</SectionTitle>

      <div className="no-scrollbar" style={{ overflowX: 'auto', paddingBottom: 8 }}>
        {/* 라벨은 격자 폭 안에서 절대 배치된다 — inline-block이라 이 상자가 격자만큼만 넓어진다. */}
        <div style={{ position: 'relative', display: 'inline-block', paddingTop: 16 }}>
          <div aria-hidden="true" style={{ position: 'absolute', top: 0, left: 0, height: 14 }}>
            {months.map(({ label, left }) => (
              <span
                key={label}
                style={{ position: 'absolute', left, fontSize: 11, color: '#6F6A5E', whiteSpace: 'nowrap' }}
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
        <Text typography="t6" fontWeight="bold" style={{ ...SERIF_VALUE, display: 'block', lineHeight: 1.25 }}>
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
 * 펼칠 수 있는 날인가 — 펼쳐서 <b>새로 보이는 게 있어야</b> 손잡이를 단다.
 *
 * <p>기준은 「몇 권인가」가 아니라 <b>「펼치면 줄이 둘 이상인가」</b>다. 한 권만 읽은 날은 그 책 시간이 곧
 * 총합이라 펼쳐도 같은 숫자 하나가 나오고, 책을 아예 안 고른 날도 한 줄이 그날 전부다 — 그런 날까지
 * 눌리게 보이면 화면이 없는 것을 약속하는 셈이다. 반대로 한 권 + 안 고른 시간이면 둘은 다른 숫자라 펼칠 값이 있다.
 */
export function isExpandable(day: DailyRecord): boolean {
  return bookRows(day).length >= 2;
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
    <div style={{ marginTop: 28, borderTop: '1px solid var(--adaptiveGrey200, #E4DDD0)' }}>
      {months.map((section) => (
        <section key={section.month}>
          {/* 달 이름과 합계가 한 줄로 머리를 이룬다 — 선은 그 줄 아래를 지난다(시안 2d). */}
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
            {/* 시안 2d — 달 이름과 그 달의 합계는 둘 다 값이다. 크기로 층을 두되 서체는 같이 간다. */}
            <Text typography="st10" fontWeight="bold" style={{ ...SERIF_VALUE, fontSize: 20 }}>
              {formatMonthTitle(section.month)}
            </Text>
            <Text typography="st12" color="grey600" style={{ ...SERIF_VALUE, fontSize: 14 }}>
              {formatDuration(section.totalSeconds)}
            </Text>
          </div>
          {section.days.map((day) => (
            <DayRow
              key={day.date}
              day={day}
              monthMax={maxOf(section)}
              expanded={day.date === openDate}
              onToggle={() => setOpenDate(day.date === openDate ? null : day.date)}
            />
          ))}
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
 * 하루 한 줄의 고정 격자 — 날짜 · 표지 더미 · 막대 · 시간 · 손잡이.
 *
 * <p><b>고정 폭이라야 한다.</b> 전에는 flex라 표지 개수(1~4장)와 시간 글자 폭(「45분」~「3시간 20분」)이
 * 행마다 달라 막대의 <b>시작점과 끝점이 둘 다 흔들렸다</b> — 같은 퍼센트가 행마다 다른 픽셀로 그려지니
 * 막대 길이로 날을 견주는 것 자체가 거짓이었다. 격자로 못 박으면 구조적으로 어긋날 수가 없다.
 *
 * <p>손잡이 칸은 <b>펼칠 수 없는 날에도 비워 남긴다</b> — 그 칸이 사라지면 시간의 오른쪽 끝이 밀린다.
 */
const ROW_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '50px 56px minmax(0, 1fr) 76px 16px',
  alignItems: 'center',
  columnGap: 8,
  width: '100%',
  padding: '9px 0',
  borderBottom: '1px solid var(--adaptiveGrey100, #EFEAE0)',
};

/** 버튼의 기본 꼴을 지운다 — 손잡이지 알약이 아니다. `ROW_GRID`를 뒤에 펴서 아래 테두리를 되살린다. */
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
  monthMax,
  expanded,
  onToggle,
}: {
  day: DailyRecord;
  monthMax: number;
  expanded: boolean;
  onToggle: () => void;
}) {
  const expandable = isExpandable(day);
  const summary = (
    <>
      <div>
        <Text typography="st11" style={{ display: 'block', lineHeight: 1.2, ...SERIF_VALUE, fontSize: 15 }}>
          {formatRecordDate(day.date)}
        </Text>
        <Text typography="st12" color="grey600" style={{ display: 'block' }}>
          {formatWeekday(day.date)}
        </Text>
      </div>

      <CoverPile books={day.books} />

      {/* 막대 색은 잔디 팔레트에서 가져온다 — 같은 「얼마나 읽었나」를 두 곳이 다른 색으로 말하지 않게. */}
      <div
        aria-hidden="true"
        style={{
          width: `${barPercent(day.totalSeconds, monthMax)}%`,
          height: 6,
          borderRadius: 3,
          background: LEVEL_COLORS[2],
        }}
      />

      {/* 정렬은 감싸는 요소가 한다 — TDS `Text` 는 style 의 `text-align` 을 <b>걸러 낸다</b>(인라인
          스타일에 아예 안 실려서 조용히 왼쪽 정렬로 남는다). 오른쪽 정렬이라야 「10시간 30분」 같은 긴
          값이 넘칠 때 빈 막대 쪽으로 넘치지, 옆의 손잡이를 침범하지 않는다. */}
      <div style={{ textAlign: 'right' }}>
        {/* 잉크색이다 — 이 줄이 대답하는 값이 이것인데, 흐린 색이면 왼쪽 날짜보다 뒤로 물러난다. */}
        <Text typography="st11" style={{ whiteSpace: 'nowrap', ...SERIF_VALUE, fontSize: 15 }}>
          {formatDuration(day.totalSeconds)}
        </Text>
      </div>

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
      {expanded && <BookLines rows={bookRows(day)} />}
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
 * <p>테두리는 캔버스와 같은 종이색이다(`#F7F2E8` — 이 앱 배경엔 css 변수가 없어 리터럴을 쓴다).
 * 이게 없으면 색이 비슷한 책 둘이 붙어 <b>한 덩어리로 뭉쳐</b> 몇 권인지가 안 보인다.
 */
const PILE_CARD: CSSProperties = {
  position: 'relative',
  display: 'flex',
  borderRadius: 4,
  boxShadow: '0 0 0 1.5px #F7F2E8',
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
            fontSize: 12,
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
 * <p>막대 기준은 <b>그날 가장 오래 읽은 줄</b>이다. 하루 막대가 「그 달 최대」를 기준으로 재는 것과 같은
 * 규칙을 한 단계 아래에 쓴 것 — 총합을 기준으로 재면 여러 권인 날은 죄다 짧은 막대가 돼 견줄 수가 없다.
 * 그래서 하루 막대(그 달에서의 크기)와 책 막대(그날 안에서의 비중)는 서로 다른 것을 잰다.
 */
function BookLines({ rows }: { rows: DayBookRow[] }) {
  const longest = rows.reduce((max, row) => Math.max(max, row.seconds), 0);
  const name: CSSProperties = { display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' };

  return (
    // 왼쪽 세로선은 여백의 인용 줄과 같은 값이다 — 「위 줄에 딸린 것」이라는 말을 앱이 한 가지로 한다.
    // 그 줄이 B에서 세이지 200으로 옮겨 갔으므로 여기도 함께 옮긴다(안 옮기면 이 주석이 거짓이 된다).
    // 시안 2d는 `rgba(110,138,106,.35)`로 인용(.5)보다 한 톤 옅지만, 15% 알파 차이로 두 자리를
    // 갈라 두면 「한 가지로 한다」는 규약만 잃는다.
    <div
      style={{
        margin: '2px 0 9px 70px',
        paddingLeft: 16,
        borderLeft: '2px solid var(--adaptiveBlue200, #B6C9AE)',
        display: 'flex',
        flexDirection: 'column',
        gap: 7,
      }}
    >
      {rows.map((row) => (
        <div
          key={row.title}
          style={{
            display: 'grid',
            gridTemplateColumns: '18px minmax(0, 1fr) 46px 56px',
            alignItems: 'center',
            columnGap: 8,
          }}
        >
          {row.unassigned ? (
            <span
              aria-hidden="true"
              style={{
                display: 'block',
                width: 18,
                height: 25,
                borderRadius: 3,
                border: '1px dashed var(--adaptiveGrey200, #E4DDD0)',
              }}
            />
          ) : (
            <BookCover url={row.coverUrl} title={row.title} width={18} />
          )}

          {row.unassigned ? (
            <Text typography="st12" color="grey600" style={name}>
              {row.title}
            </Text>
          ) : (
            <Text typography="st12" style={name}>
              {row.title}
            </Text>
          )}

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
            <Text typography="st12" color="grey600" style={{ whiteSpace: 'nowrap', ...SERIF_VALUE, fontSize: 13 }}>
              {formatDuration(row.seconds)}
            </Text>
          </div>
        </div>
      ))}
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
      {/* 세리프 + st10 — 라벨(st12)과 크기·서체 두 축으로 갈린다. 전에는 3px 차이가 전부였다. */}
      <Text typography="st10" fontWeight="bold" style={{ ...SERIF_VALUE, display: 'block', marginTop: 4 }}>
        {value}
      </Text>
    </div>
  );
}
