import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it } from 'vitest';

import type { DailyRecord, MonthlySection } from './api';
import { CACHE_HISTORY, cacheClear, cachePut } from './cache';
import {
  DayRow,
  History,
  MonthlyRecords,
  barPercent,
  bookRows,
  coverStack,
  formatMonthTitle,
  formatRecordDate,
  formatWeekday,
  growthNudge,
  isExpandable,
} from './screens/History';
import { graph, userAgent } from './test-fixtures';
import { monthLabelPositions } from './ui';

/**
 * 월 라벨 배치 — 서버가 준 `monthLabels`(주 인덱스 + "M월")를 잔디 격자 위 픽셀 자리로 옮긴다.
 * 웹 `ContributionGraph.vue`는 CSS 그리드의 `gridColumnStart`가 열을 맞춰 주지만, 미니앱 격자는
 * flex + 고정 칸이라 열 자리를 직접 계산해야 한다 — 그 계산만 꺼내 계측한다.
 */
describe('성장 단계 문구 (growthNudge)', () => {
  it('다음 단계가 남아 있으면 며칠 더 읽어야 하는지 말한다 — 계속 읽을 이유가 화면에 남는다', () => {
    expect(growthNudge(2, '꽃')).toBe('2일 더 읽으면 꽃이 돼요');
  });

  it('하루 남았으면 「1일」이라고 그대로 적는다 — 「내일」로 바꾸면 자정 기준이 달라 거짓이 된다', () => {
    expect(growthNudge(1, '나무')).toBe('1일 더 읽으면 나무가 돼요');
  });

  it('가장 큰 단계면 재촉하지 않는다 — 더 오를 곳이 없는데 남은 일수를 적으면 거짓말이다', () => {
    expect(growthNudge(0, null)).toBe('가장 큰 단계예요');
  });
});

describe('월 라벨 배치', () => {
  const at = (...weekIndexes: number[]) => weekIndexes.map((weekIndex) => ({ weekIndex, label: `${weekIndex}월` }));

  it('라벨이 없으면 아무것도 안 그린다 — 빈 그래프(가입 직후)가 여기로 온다', () => {
    expect(monthLabelPositions([], 11)).toEqual([]);
  });

  it('주 인덱스를 칸 폭 + 간격으로 환산한다 — 격자와 같은 산수를 써야 라벨이 그 열 위에 선다', () => {
    // 칸 11 + gap 3 = 주당 14px. 0주는 왼쪽 끝, 4주는 56px.
    expect(monthLabelPositions(at(0, 4), 11)).toEqual([
      { label: '0월', left: 0 },
      { label: '4월', left: 56 },
    ]);
  });

  it('직전 라벨과 너무 가까우면 버린다 — 그래프가 월말에서 시작하면 첫 두 라벨이 한 칸 차라 겹쳐 읽힌다', () => {
    expect(monthLabelPositions(at(0, 1), 11)).toEqual([{ label: '0월', left: 0 }]);
  });

  it('간격은 버린 라벨이 아니라 **남긴 라벨** 기준으로 잰다 — 입력 기준으로 재면 촘촘한 구간이 통째로 사라진다', () => {
    // left = 0·14·28·42·56. 남긴 것 기준이면 0·28·56이 살아남는다(버린 것 기준이면 0 하나만 남는다).
    expect(monthLabelPositions(at(0, 1, 2, 3, 4), 11).map((m) => m.left)).toEqual([0, 28, 56]);
  });
});

/** 기록 화면 — 잔디 위 월 라벨과 아래 범례가 실제로 그려지는지(계산만 맞고 배선이 없으면 화면은 그대로다). */
describe('기록 화면', () => {
  const markup = renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <History graph={graph} />
    </TDSMobileProvider>,
  );

  it('격자 위에 월 라벨을 그린다 — 서버가 실어 주는데 안 그려서 잔디가 언제인지 알 수 없었다', () => {
    expect(markup).toContain('8월');
  });

  it('격자 아래에 색 범례를 둔다 — 색 농도가 무슨 뜻인지 화면 어디에도 없었다', () => {
    expect(markup).toContain('적게');
    expect(markup).toContain('많이');
  });

  it('수동 기록(테두리 칸) 설명도 함께 둔다 — 범례 없이는 테두리가 오류처럼 보인다', () => {
    expect(markup).toContain('직접 채움');
  });

  it('가로 스크롤을 손대지 않는다 — weeks[0]이 최신이라 초기 위치(왼쪽 끝)가 이미 오늘이다', () => {
    // 마운트 이펙트는 정적 렌더에 안 잡히니 소스로 계측한다. 오른쪽 끝으로 밀면 1년 전 빈 잔디가 뜬다.
    expect(readFileSync(new URL('./screens/History.tsx', import.meta.url), 'utf8')).not.toContain('scrollLeft');
  });
});

/**
 * 날짜·월 머리글 포맷 — 표기 로직만 꺼내 계측한다(요일은 `Date`가 계산하므로 경계에서 틀리기 쉽다).
 * 웹 `MonthlyRecords.vue`와 같은 말을 쓰되 폭이 좁아 연도는 일자에서 뺀다.
 */
describe('기록 목록 포맷', () => {
  it('일자는 "MM-DD" — 연도는 월 머리글이, 요일은 아랫줄이 말한다', () => {
    expect(formatRecordDate('2026-08-14')).toBe('08-14');
  });

  it('한 자리 월·일도 0을 유지한다 — 자리수가 흔들리면 오른쪽 시간 열이 들쭉날쭉해진다', () => {
    expect(formatRecordDate('2026-01-05')).toBe('01-05');
  });

  it('요일은 아랫줄에 온말로 — 표지 칸과 높이를 맞추면서 "(금)"보다 읽기 쉽다', () => {
    expect(formatWeekday('2026-08-14')).toBe('금요일');
    expect(formatWeekday('2026-01-05')).toBe('월요일');
  });

  it('월 머리글은 "YYYY년 M월" — 월의 앞 0은 뗀다(사람이 읽는 자리)', () => {
    expect(formatMonthTitle('2026-08')).toBe('2026년 8월');
    expect(formatMonthTitle('2026-01')).toBe('2026년 1월');
  });
});

describe('하루 막대 (barPercent)', () => {
  it('그 달에서 가장 오래 읽은 날이 가득 찬다 — 기준이 달마다 다시 잡혀야 편차가 보인다', () => {
    expect(barPercent(3_600, 3_600)).toBe(100);
    expect(barPercent(1_800, 3_600)).toBe(50);
  });

  it('기준이 0이면 0 — 0으로 나눠 NaN 폭이 되면 막대가 통째로 사라진다', () => {
    expect(barPercent(0, 0)).toBe(0);
  });

  it('기준을 넘겨도 100을 안 넘는다 — 막대가 칸 밖으로 삐져나가지 않는다', () => {
    expect(barPercent(7_200, 3_600)).toBe(100);
  });
});

/** 그날 읽은 책 한 권. 표지 주소는 대개 없다(직접 등록한 책). */
const bk = (title: string, seconds: number, coverUrl: string | null = null) => ({ title, coverUrl, seconds });

const day = (over: Partial<DailyRecord> = {}): DailyRecord => ({
  date: '2026-08-14',
  totalSeconds: 5_400,
  books: [],
  manuallyFilled: false,
  ...over,
});

describe('표지 더미 (coverStack)', () => {
  it('세 권까지는 그대로 쌓는다', () => {
    const stack = coverStack([bk('가', 600), bk('나', 300)]);

    expect(stack.shown.map((b) => b.title)).toEqual(['가', '나']);
    expect(stack.more).toBe(0);
  });

  it('넘치면 마지막 칸을 권수에 내준다 — 표지 세 장 + 권수까지 세우면 칸이 옆의 막대를 잡아먹는다', () => {
    const stack = coverStack([bk('가', 600), bk('나', 500), bk('다', 400), bk('라', 300), bk('마', 200)]);

    expect(stack.shown.map((b) => b.title)).toEqual(['가', '나']);
    expect(stack.more).toBe(3);
  });

  it('딱 세 권이면 권수 타일 없이 세 장 다 쌓는다 — 「+0」이 될 자리를 표지에 준다', () => {
    const stack = coverStack([bk('가', 600), bk('나', 500), bk('다', 400)]);

    expect(stack.shown).toHaveLength(3);
    expect(stack.more).toBe(0);
  });
});

describe('펼침 줄 (bookRows)', () => {
  it('서버가 정한 오래 읽은 순 그대로 세운다 — 화면이 다시 정렬하면 접힌 더미와 펼친 목록이 어긋난다', () => {
    const rows = bookRows(day({ totalSeconds: 5_400, books: [bk('미움받을 용기', 3_600), bk('사피엔스', 1_800)] }));

    expect(rows.map((r) => r.title)).toEqual(['미움받을 용기', '사피엔스']);
  });

  it('책 시간의 합이 총합보다 적으면 마지막에 「책 안 고른 기록」을 둔다 — 조용히 빼면 펼친 시간을 더해도 위 총합과 안 맞는다', () => {
    const rows = bookRows(day({ totalSeconds: 5_400, books: [bk('데미안', 3_600)] }));

    expect(rows.at(-1)).toEqual({ title: '책 안 고른 기록', coverUrl: null, seconds: 1_800, unassigned: true });
  });

  it('차액이 없으면 그 줄을 안 만든다 — 「0초」짜리 빈 줄이 생긴다', () => {
    expect(bookRows(day({ totalSeconds: 3_600, books: [bk('데미안', 3_600)] }))).toHaveLength(1);
  });

  it('책이 하나도 없는 날은 그날 전부가 「책 안 고른 기록」이다', () => {
    expect(bookRows(day({ totalSeconds: 1_200, books: [] }))).toEqual([
      { title: '책 안 고른 기록', coverUrl: null, seconds: 1_200, unassigned: true },
    ]);
  });
});

describe('펼칠 수 있는 날 (isExpandable)', () => {
  it('두 권 이상이면 펼칠 수 있다', () => {
    expect(isExpandable(day({ totalSeconds: 5_400, books: [bk('가', 3_600), bk('나', 1_800)] }))).toBe(true);
  });

  it('한 권뿐이면 펼칠 수 없다 — 펼쳐 봐야 위에 있는 것과 같은 숫자 하나다', () => {
    expect(isExpandable(day({ totalSeconds: 3_600, books: [bk('가', 3_600)] }))).toBe(false);
  });

  it('한 권이어도 책 안 고른 시간이 있으면 펼칠 수 있다 — 그 책 시간과 그날 총합이 다르다', () => {
    expect(isExpandable(day({ totalSeconds: 5_400, books: [bk('가', 3_600)] }))).toBe(true);
  });

  it('책이 아예 없는 날은 펼칠 수 없다 — 한 줄이 곧 그날 전부다', () => {
    expect(isExpandable(day({ totalSeconds: 1_200, books: [] }))).toBe(false);
  });
});

describe('하루 한 줄 (DayRow)', () => {
  const busy = day({ date: '2026-08-14', totalSeconds: 4_500, books: [bk('미움받을 용기', 3_600), bk('사피엔스', 900)] });
  const alone = day({ date: '2026-08-13', totalSeconds: 3_600, books: [bk('데미안', 3_600)] });

  const render = (d: DailyRecord, expanded: boolean) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <DayRow day={d} monthMax={9_000} expanded={expanded} onToggle={() => {}} />
      </TDSMobileProvider>,
    );

  it('접히면 그날 총 시간만 말한다 — 책 이름은 펼쳐야 나온다', () => {
    expect(render(busy, false)).toContain('1시간 15분');
    expect(render(busy, false)).not.toContain('미움받을 용기');
  });

  it('그날 읽은 시간은 흐린 색이 아니다 — 그 줄이 대답하는 값이 이것이다', () => {
    // grey700(흐린 글자)이었는데, 왼쪽 날짜는 잉크색이라 **답이 질문보다 뒤로 물러나** 있었다.
    const markup = render(alone, false);
    const at = markup.indexOf('>1시간<');
    expect(at).toBeGreaterThan(-1);
    expect(markup.slice(markup.lastIndexOf('<', at), at)).not.toContain('--tds-paragraph-color:grey');
  });

  it('펼치면 책마다 얼마나 읽었는지 적는다 — 사용자가 물은 「무슨 책을 얼마나」다', () => {
    const markup = render(busy, true);

    expect(markup).toContain('미움받을 용기');
    expect(markup).toContain('1시간');
    expect(markup).toContain('15분');
  });

  it('책 막대는 그날 가장 오래 읽은 책을 기준으로 잰다 — 하루 막대가 그 달 최대를 기준으로 재는 것과 같은 규칙', () => {
    const markup = render(busy, true);

    // 하루 막대는 4500/9000 = 50%. 책 막대는 3600/3600 = 100%, 900/3600 = 25%.
    expect(markup).toContain('width:50%');
    expect(markup).toContain('width:100%');
    expect(markup).toContain('width:25%');
  });

  it('한 권인 날과 여러 권인 날이 같은 격자를 쓴다 — 막대의 시작·끝이 행마다 어긋나면 길이 비교가 거짓이 된다', () => {
    const grid = /grid-template-columns:([^;"]+)/;

    expect(render(alone, false).match(grid)![1]).toBe(render(busy, false).match(grid)![1]);
  });

  it('펼칠 수 없는 날엔 여는 손잡이를 안 둔다 — 눌러도 같은 숫자만 나오는데 눌리게 보이면 거짓말이다', () => {
    expect(render(busy, false)).toContain('data-day-toggle');
    expect(render(alone, false)).not.toContain('data-day-toggle');
  });
});

/** 잔디 아래 월별 기록 — 화면 조립부. 마운트 이펙트가 안 도는 하니스라 순수 컴포넌트로 꺼내 렌더한다. */
describe('월별 기록 목록', () => {
  const months: MonthlySection[] = [
    {
      month: '2026-08',
      totalSeconds: 45_000,
      days: [
        {
          date: '2026-08-14',
          totalSeconds: 5_400,
          books: [bk('미움받을 용기', 3_600), bk('사피엔스', 1_800)],
          manuallyFilled: false,
        },
        { date: '2026-08-09', totalSeconds: 1_200, books: [], manuallyFilled: true },
      ],
    },
    { month: '2026-07', totalSeconds: 3_600, days: [] },
  ];

  const markup = renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <MonthlyRecords months={months} />
    </TDSMobileProvider>,
  );

  it('월 머리글에 그 달 총 독서 시간을 함께 둔다 — 잔디만으론 "그 달에 얼마나"를 못 읽는다', () => {
    expect(markup).toContain('2026년 8월');
    expect(markup).toContain('12시간 30분');
  });

  it('일자마다 날짜·읽은 시간을 그린다 — 사용자가 요청한 "언제 얼마나"', () => {
    expect(markup).toContain('08-14');
    expect(markup).toContain('금요일');
    expect(markup).toContain('1시간 30분');
  });

  it('제목을 쉼표로 잇지 않는다 — 두 권만 돼도 잘려서 "미움받을 용기, 사피…"가 됐다', () => {
    expect(markup).not.toContain('미움받을 용기, 사피엔스');
  });

  it('책은 표지 더미로 선다 — 몇 권인지가 글자가 아니라 자리로 보인다', () => {
    // 자리 표지는 제목의 첫 글자다(무표지 책과 같은 규칙) — 두 권이면 두 글자가 선다.
    expect(markup).toContain('>미<');
    expect(markup).toContain('>사<');
  });

  it('책 미지정 세션만 있는 날도 행을 남긴다 — 읽은 시간은 있는데 행이 사라지면 합계가 안 맞아 보인다', () => {
    expect(markup).toContain('08-09');
    expect(markup).toContain('일요일');
    expect(markup).toContain('20분');
  });

  it('여러 달을 한 화면에 이어 붙인다 — 월 넘기기 버튼 없이 스크롤로 과거를 본다(A안)', () => {
    expect(markup).toContain('2026년 7월');
  });

  it('기록이 없으면 안내를 대신 둔다 — 가입 직후 잔디 아래가 통째로 비어 고장처럼 보였다', () => {
    const empty = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <MonthlyRecords months={[]} />
      </TDSMobileProvider>,
    );
    expect(empty).toContain('아직 독서 기록이 없어요');
  });
});

/**
 * 기록 세션 캐시 — 잔디는 App이 든 `graph` prop이라 즉시 뜨는데, 월별 기록만 재마운트마다 늦게 붙었다.
 * 지난 응답을 첫 렌더의 출발점으로 삼는다(재검증은 그대로 매번 나간다).
 */
describe('기록 세션 캐시', () => {
  beforeEach(cacheClear);

  const renderMounted = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <History graph={graph} />
      </TDSMobileProvider>,
    );

  it('캐시가 비면 월별 기록 자리가 비어 있다 — effect가 안 도는 정적 렌더의 기본값', () => {
    expect(renderMounted()).not.toContain('2026년 8월');
  });

  it('지난 기록이 캐시에 있으면 첫 렌더부터 월별 목록이 선다', () => {
    cachePut(CACHE_HISTORY, [
      { month: '2026-08', totalSeconds: 45_000, days: [{ date: '2026-08-14', totalSeconds: 5_400, books: [], manuallyFilled: false }] },
    ] satisfies MonthlySection[]);

    const markup = renderMounted();

    expect(markup).toContain('2026년 8월');
    expect(markup).toContain('08-14');
  });
});

/** 그 글자를 감싼 여는 태그 — 크기·서체·색·그림자가 인라인 style이라 태그만 잘라 보면 판정된다. */
const tagBefore = (markup: string, text: string) => {
  const at = markup.indexOf(text);
  return at < 0 ? '' : markup.slice(markup.lastIndexOf('<', at), at);
};

/**
 * 시안 2d의 위계 — 값은 세리프, 말은 고운돋움. 설계의 「비세리프 확인 목록」(요일·타임스탬프)도
 * 함께 잠근다 — 값처럼 보이지만 값이 아닌 자리다.
 */
describe('기록 위계 (시안 2d)', () => {
  const day = (date: string, seconds: number): DailyRecord => ({
    date,
    totalSeconds: seconds,
    books: [{ title: '사피엔스', coverUrl: null, seconds }],
    manuallyFilled: false,
  });
  /**
   * ⚠️ 월 합계가 하루 합계를 <b>문자열로 포함하지 않게</b> 고른다 — 37,800초는 「10시간 30분」이라
   * 하루의 「30분」을 품고, `indexOf`가 월 합계를 먼저 집어 이 테스트가 엉뚱한 요소를 재게 된다.
   * 계측기가 무엇을 집는지 픽스처가 정한다.
   */
  const month = (): MonthlySection => ({
    month: '2026-08',
    totalSeconds: 7_200,
    days: [day('2026-08-21', 1_800)],
  });
  const records = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <MonthlyRecords months={[month()]} />
      </TDSMobileProvider>,
    );

  it('월 머리글은 세리프 20, 월 합계는 세리프 14다', () => {
    const markup = records();
    const head = tagBefore(markup, '2026년 8월');
    const total = tagBefore(markup, '2시간');

    expect(head).toContain('Gowun Batang');
    expect(head).toContain('font-size:20px');
    expect(total).toContain('Gowun Batang');
    expect(total).toContain('font-size:14px');
  });

  it('날짜와 하루 합계는 둘 다 세리프 15다 — 한 줄이 대답하는 두 값이다', () => {
    const markup = records();

    expect(tagBefore(markup, '08-21')).toContain('font-size:15px');
    expect(tagBefore(markup, '08-21')).toContain('Gowun Batang');
    expect(tagBefore(markup, '30분')).toContain('font-size:15px');
  });

  it('요일은 세리프가 아니다 — 값처럼 보이지만 말이다(설계 비세리프 목록)', () => {
    const tag = tagBefore(records(), '금요일');

    expect(tag).not.toBe('');
    expect(tag).not.toContain('Gowun Batang');
  });

  /**
   * ⚠️ 픽스처가 계측기의 조준을 정한다 — 책이 한 권이면 하루 합계와 책 시간이 같은 문자열이 되어
   * `indexOf`가 <b>합계(15px)를 먼저</b> 집는다. 그래서 두 권으로 갈라 책 줄만 겨눈다(월/일 합계에는
   * 이미 같은 규율을 썼는데 여기만 빠져 있었다 — 독립 리뷰 적발).
   */
  it('펼친 책별 시간은 세리프 13이다 — 하루 합계(15)보다 한 단 작다', () => {
    const twoBooks: DailyRecord = {
      date: '2026-08-21',
      totalSeconds: 1_800,
      books: [
        { title: '사피엔스', coverUrl: null, seconds: 1_200 }, // 20분
        { title: '데미안', coverUrl: null, seconds: 600 }, // 10분
      ],
      manuallyFilled: false,
    };
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <DayRow day={twoBooks} monthMax={9_000} expanded onToggle={() => {}} />
      </TDSMobileProvider>,
    );

    const bookTime = tagBefore(markup, '20분');

    expect(bookTime).not.toBe('');
    expect(bookTime).toContain('Gowun Batang');
    expect(bookTime).toContain('font-size:13px');
    expect(tagBefore(markup, '30분')).toContain('font-size:15px'); // 하루 합계는 그대로 15
  });

  it('가이드라인은 여백 인용 줄과 같은 세이지 선이다 — 「위 줄에 딸린 것」을 앱이 한 가지로 말한다', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <DayRow day={day('2026-08-21', 1_800)} monthMax={9_000} expanded onToggle={() => {}} />
      </TDSMobileProvider>,
    );

    expect(markup).toContain('padding-left:16px');
    expect(markup).toContain('2px solid var(--adaptiveBlue200');
  });
});
