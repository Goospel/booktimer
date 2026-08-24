import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it } from 'vitest';

import type { BookStatus, MarginEntry, MarginResponse, MyBookSummary, Recommendation, SearchRow } from './api';
import { CACHE_SHELF, cacheClear, cacheKeyMargin, cachePut } from './cache';
import { dismissCoachmark, setCoachmarkWalking } from './coachmark';
import type { LibrarySheet } from './screens/Library';
import {
  AddStatusSheet,
  BookGrid,
  BookSearch,
  Library,
  MarginBoxView,
  RecommendCard,
  SearchResultRow,
  Shelf,
  marginBoxView,
  bookStats,
  metaLine,
  needsPublishConfirm,
  resolveSelected,
} from './screens/Library';
import { stubLocalStorage, userAgent } from './test-fixtures';

beforeEach(() => {
  stubLocalStorage(); // 「책 추가하기」가 렌더 중에 코치마크를 봤는지 읽는다
  setCoachmarkWalking(true); // 인라인 안내는 걷는 중에만 뜬다(잠금 계측은 coachmark.test)
  cacheClear(); // 세션 캐시는 모듈 상태다 — 안 비우면 앞 테스트가 뒤 테스트의 첫 렌더를 채운다
});

/**
 * 서재 목록 렌더 — 탭 분류(읽는 중/다 읽음/읽고 싶어요)·캐러셀·시트가 계측 대상이다.
 * 액션(상태 변경·공개 토글·삭제)의 실행은 api 계층에서 계측하고, 여기서는 배선의 정적 결과만 본다.
 */
function book(
  id: number,
  title: string,
  status: MyBookSummary['status'],
  extra: Partial<MyBookSummary> = {},
): MyBookSummary {
  return {
    id,
    title,
    author: '저자',
    coverUrl: null,
    isbn13: null,
    status,
    statusLabel: status,
    visibility: 'PRIVATE',
    visibilityLabel: '비공개',
    isPublic: false,
    seconds: 0,
    purchaseLink: null,
    ...extra,
  };
}

function shelf(
  books: MyBookSummary[],
  {
    tab = 'READING' as BookStatus,
    // `undefined`(아직 안 고름)가 기본이다 — `null`은 「책 추가」 칸을 <b>고른</b> 값이라 뜻이 다르다.
    selectedId = undefined as number | null | undefined,
    sheet = null as LibrarySheet,
    myLoginId = 'goospel' as string | null,
    searchEnabled = true,
  } = {},
) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Shelf
        books={books}
        tab={tab}
        selectedId={selectedId}
        sheet={sheet}
        busy={false}
        myLoginId={myLoginId}
        searchEnabled={searchEnabled}
        onTab={() => {}}
        onSelect={() => {}}
        onSheet={() => {}}
        onAction={() => {}}
        onOpenMargin={() => {}}
        onComposeMargin={() => {}}
        onError={() => {}}
        onAddBook={() => {}}
      />
    </TDSMobileProvider>,
  );
}

/** 탭 세 개가 늘 서 있고, 보이는 책은 고른 탭의 것만 — 세로로 길던 3섹션 나열을 대체한다. */
describe('서재 탭', () => {
  const books = [
    book(1, '읽는책', 'READING'),
    book(2, '끝낸책', 'FINISHED'),
    book(3, '살책', 'WANT_TO_READ'),
  ];

  it('탭 라벨에 그 상태의 권수를 함께 적는다', () => {
    const markup = shelf(books);

    expect(textOf(markup)).toContain('읽는 중 1');
    expect(textOf(markup)).toContain('다 읽음 1');
    expect(textOf(markup)).toContain('읽고 싶어요 1');
  });

  it('빈 탭도 라벨은 남는다 — 탭이 나타났다 사라지면 자리가 흔들린다', () => {
    const markup = shelf([book(1, '읽는책', 'READING')]);

    expect(textOf(markup)).toContain('다 읽음 0');
    expect(textOf(markup)).toContain('읽고 싶어요 0');
  });

  it('고른 탭의 책만 캐러셀에 세운다', () => {
    const markup = shelf(books, { tab: 'FINISHED' });

    expect(markup).toContain('끝낸책');
    expect(markup).not.toContain('읽는책');
    expect(markup).not.toContain('살책');
  });

  it('고른 탭이 비었으면 그 탭의 안내만 남고 관리 버튼은 없다', () => {
    const markup = shelf([book(1, '읽는책', 'READING')], { tab: 'FINISHED' });

    expect(markup).toContain('다 읽은 책이 없어요');
    expect(markup).not.toContain('관리');
  });

  it('책이 하나도 없어도 캐러셀이 선다 — 0권이라고 다른 화면이 뜨지 않는다(홈과 같은 처리)', () => {
    const markup = shelf([]);

    expect(markup).toContain('책 추가');
    expect(textOf(markup)).toContain('읽는 중 0'); // 탭도 그대로 — 빈 상태라고 자리가 사라지지 않는다
  });

  /**
   * 검색이 꺼진 날의 마지막 폴백 — 「책 추가」 칸이 게이트에 걸려 사라지면 캐러셀에 세울 것이 하나도 없다.
   * 그때 옛 문장이 남지 않으면 <b>빈 화면</b>이 된다.
   */
  it('책 0권 + 검색 꺼짐이면 옛 안내 문장이 남는다 — 지우면 아무것도 없는 화면이다', () => {
    expect(shelf([], { searchEnabled: false })).toContain('아직 책이 없어요');
  });
});

/**
 * 캐러셀 아래 메타 — 표지만으로는 안 보이는 저자·읽은 시간·공개 여부를 여기서 말한다.
 *
 * <p>**저자와 나머지는 줄을 나눈다**: 알라딘 저자 문자열은 「레프 니콜라예비치 톨스토이 (지은이),
 * 연진희 (옮긴이)」처럼 길어서, 한 줄로 이으면 폰 폭에서 되접히며 읽은 시간·공개가 저자 이름 사이에
 * 끼어 든다(실기기 실측). 줄을 나누면 되접혀도 「누가 썼나」와 「얼마나 읽었나」가 섞이지 않는다.
 */
describe('선택한 책 메타 (metaLine)', () => {
  it('저자만 남는다 — 읽은 시간·공개 여부는 아래 칩이 맡는다', () => {
    expect(metaLine(book(1, '데미안', 'READING', { author: '헤세', seconds: 7200, isPublic: true }))).toBe('헤세');
  });

  it('저자가 길어도 다른 것이 섞이지 않는다 — 실기기에서 시간이 이름 사이에 끼어 들던 자리', () => {
    const long = '레프 니콜라예비치 톨스토이 (지은이), 연진희 (옮긴이)';
    expect(metaLine(book(1, '전쟁과 평화 1', 'FINISHED', { author: long, seconds: 39_900, isPublic: true }))).toBe(long);
  });

  it('저자를 모르면 「저자 미상」', () => {
    expect(metaLine(book(1, '코스모스', 'WANT_TO_READ', { author: null }))).toBe('저자 미상');
  });
});

describe('책 상태 칩 (bookStats)', () => {
  it('읽은 시간은 「N 읽음」으로 — 「2시간」만 있으면 무엇의 2시간인지 안 보인다', () => {
    expect(bookStats(book(1, '데미안', 'READING', { seconds: 7200, isPublic: true }))).toEqual([
      { label: '2시간 읽음', value: '2시간', tone: 'neutral' },
      { label: '공개', tone: 'sage' },
    ]);
  });

  it('아직 안 읽은 책은 시간 칩이 없다 — 「0분」은 정보가 아니다', () => {
    expect(bookStats(book(1, '코스모스', 'WANT_TO_READ', { seconds: 0, isPublic: false }))).toEqual([
      { label: '비공개', tone: 'outline' },
    ]);
  });

  it('공개는 켜지고 비공개는 눌린다 — 색이 곧 「누구에게 보이는가」다', () => {
    const shown = bookStats(book(1, '가', 'READING', { isPublic: true }));
    const hidden = bookStats(book(2, '나', 'READING', { isPublic: false }));

    expect(shown.at(-1)!.tone).toBe('sage');
    expect(hidden.at(-1)!.tone).toBe('outline');
  });

  it('상태 칩이 실제로 그려진다 — 순수 함수만 맞고 화면이 안 부르면 아무 일도 안 일어난다', () => {
    const markup = shelf([book(1, '데미안', 'READING', { seconds: 7200, isPublic: true })], { selectedId: 1 });

    expect(textOf(markup)).toContain('2시간 읽음');
    expect(markup).toContain('data-book-chip=""');
    // 저자와 상태가 한 덩어리로 붙어 있던 옛 모양이 남아 있으면 안 된다.
    expect(markup).not.toContain('2시간 · 공개');
  });
});

/**
 * 선택은 **탭 안에서 다시 푼다** — 상태를 옮기거나 지우면 고른 id가 그 탭에서 사라지는데,
 * 그대로 두면 캐러셀이 아무것도 안 가리킨 채 「관리」만 서 있게 된다.
 */
describe('선택 해석 (resolveSelected)', () => {
  const rows = [book(1, '가', 'READING'), book(2, '나', 'READING')];

  it('고른 책이 그 탭에 있으면 그대로', () => {
    expect(resolveSelected(rows, 2)?.title).toBe('나');
  });

  it('탭을 옮겨 고른 책이 없으면 첫 책으로 떨어진다', () => {
    expect(resolveSelected(rows, 99)?.title).toBe('가');
  });

  it('빈 탭이면 아무것도 안 고른다', () => {
    expect(resolveSelected([], 1)).toBeNull();
  });
});

/**
 * 인라인 여백 박스가 든 스냅 판정 — 캐러셀을 훑으면 요청이 겹쳐 도착하므로, 스냅에 **어느 책의 응답인지**
 * 태그를 달아 두고 지금 책과 다르면 무조건 로딩으로 떨어뜨린다(경합 렌더 가드).
 * 이 가드가 없으면 책 B로 넘어간 화면에 책 A의 글이 잠깐 비친다 — 남의 책 아래 내 글이 붙는 셈이다.
 */
describe('인라인 여백 스냅 판정 (marginBoxView)', () => {
  const response = (bookId: number): MarginResponse => ({
    book: { id: bookId, title: '데미안', author: '헤세', coverUrl: null },
    ownerNickname: '구스펠',
    self: true,
    entries: [],
  });

  it('스냅이 아직 없으면 로딩이다', () => {
    expect(marginBoxView(null, 1)).toBe('loading');
  });

  it('다른 책의 스냅이면 로딩이다 — 옛 책의 글이 새 책 아래 그려질 수 없다', () => {
    expect(marginBoxView({ bookId: 2, margin: response(2) }, 1)).toBe('loading');
  });

  it('같은 책의 응답이면 그대로 그린다', () => {
    const margin = response(1);

    expect(marginBoxView({ bookId: 1, margin }, 1)).toBe(margin);
  });

  it('같은 책인데 응답이 null이면 실패다 — 화면 전체가 아니라 박스 안에 머문다', () => {
    expect(marginBoxView({ bookId: 1, margin: null }, 1)).toBe('error');
  });
});

/**
 * 인라인 박스 표시 — 로딩·실패·내용 세 상태가 <b>같은 뼈대(테두리 + 헤더 줄)</b> 안에 머문다.
 * 실패도 화면 전체를 빨갛게 만들지 않고 박스 안 한 줄이다(401만 App으로 올라간다).
 *
 * <p>미리보기는 최대 {@link MARGIN_PREVIEW_COUNT}장이고 나머지는 「전체 보기 ›」가 맡는다 —
 * 서재의 박스는 목록이 아니라 <b>들춰보기 + 진입로</b>다.
 */
describe('인라인 여백 박스 표시 (MarginBoxView)', () => {
  const NOW = Date.parse('2026-08-16T12:00:00Z');
  const HOUR = 3_600_000;

  const entry = (id: number, text: string, bgCode: string, hoursAgo: number, likeCount = 0): MarginEntry => ({
    id,
    text,
    quote: null,
    bgCode,
    createdAt: new Date(NOW - hoursAgo * HOUR).toISOString(),
    likeCount,
    liked: false,
  });

  const margin = (entries: MarginEntry[]): MarginResponse => ({
    book: { id: 1, title: '데미안', author: '헤세', coverUrl: null, isPublic: true },
    ownerNickname: '구스펠',
    self: true,
    entries,
  });

  const three = margin([
    entry(1, '첫째 문장', 'night', 3, 2),
    entry(2, '둘째 문장', 'forest', 28),
    entry(3, '셋째 문장', 'sea', 50),
  ]);

  const box = (view: MarginResponse | 'loading' | 'error') =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <MarginBoxView view={view} now={NOW} onOpenAll={() => {}} />
      </TDSMobileProvider>,
    );

  it('헤더가 글 수를 말하고 전체 화면으로 가는 길을 준다', () => {
    const markup = box(three);

    expect(markup).toContain('여백');
    expect(markup).toContain('3');
    expect(markup).toContain('전체 보기');
  });

  it('미리보기는 두 장까지 — 나머지는 「전체 보기」의 몫이다', () => {
    const markup = box(three);

    expect(markup).toContain('첫째 문장');
    expect(markup).toContain('둘째 문장');
    expect(markup).not.toContain('셋째 문장');
  });

  it('카드는 여백 화면과 같은 팔레트 배경과 상대 시각으로 선다', () => {
    const markup = box(three);

    expect(markup).toContain('#1f2233'); // night
    expect(markup).toContain('3시간 전');
  });

  /** 지우기는 전체 화면의 몫이다 — 박스는 표시 전용이라 `self=false`로 카드를 빌려 쓴다. */
  it('박스 안에는 지우기 UI가 없다 — 글 본문은 그대로 보인다', () => {
    const markup = box(three);

    expect(markup).toContain('첫째 문장');
    expect(markup).not.toContain('지우기');
  });

  /**
   * <b>이 박스가 `self={false}`를 넘기는 자리다</b> — 글은 실제로 내 것인데 「삭제 UI를 감춰」라는 뜻으로
   * 쓴다. 그래서 좋아요 손잡이를 `!self`로 켰다면 <b>내 서재에서 내 글에 하트가 떴을 것이다</b>.
   * 손잡이의 게이트가 `self`가 아니라 <b>핸들러의 유무</b>인 덕에 여기엔 구조적으로 안 뜬다.
   */
  it('박스 안에는 좋아요 손잡이가 없다 — 개수는 보인다(내 글이라 누를 수만 없다)', () => {
    const markup = box(three);

    expect(markup).not.toContain('aria-label="좋아요"');
    expect(markup).toContain('좋아요 2명');
  });

  it('글이 0장이어도 박스는 서 있다 — 사라지면 캐러셀을 밀 때마다 화면 높이가 출렁인다', () => {
    const markup = box(margin([]));

    expect(markup).toContain('data-margin-box');
    expect(markup).toContain('아직 남긴 글이 없어요');
  });

  it('로딩도 박스 안에서 말한다', () => {
    const markup = box('loading');

    expect(markup).toContain('data-margin-box');
    expect(markup).toContain('불러오는 중');
  });

  it('실패도 박스 안에 머문다 — 화면 전체가 빨개지지 않는다', () => {
    const markup = box('error');

    expect(markup).toContain('data-margin-box');
    expect(markup).toContain('여백을 불러오지 못했어요');
  });
});

/**
 * 서재 손잡이 줄 + 인라인 박스 — 「여백」을 눌러 목록 화면까지 가야 <b>무엇을 남겼는지</b> 보이던 것을
 * 뒤집었다: 글은 박스가 그 자리에서 보여 주고, 손잡이는 <b>작성 직행</b>이 된다(홈 문과 같은 경로).
 * 핸들이 없으면 서버가 여백 대상을 찾지 못하므로 글쓰기·박스 둘 다 그리지 않는다.
 */
describe('서재 손잡이 줄과 인라인 여백 박스', () => {
  it('손잡이는 작성 직행과 관리 둘이다 — 목록으로 가던 옛 「여백」 버튼은 없다', () => {
    const markup = shelf([book(1, '데미안', 'READING')], { selectedId: 1 });

    expect(markup).toContain('여백에 글쓰기');
    expect(markup).toContain('관리');
    expect(markup).not.toContain('>여백</button>'); // 옛 손잡이(라벨이 「여백」뿐인 버튼)
  });

  it('두 손잡이는 전폭을 2:1로 나눠 가진다 — 쓰기가 주, 관리가 보조다', () => {
    const markup = shelf([book(1, '데미안', 'READING')], { selectedId: 1 });

    expect(markup).toContain('flex:2');
    expect(markup).toContain('flex:1');
    // 시안 2c에서 틴트(.14)가 **채움**으로 바뀌었다 — 이 화면의 주 동작 하나라는 뜻이 색으로 선다.
    expect(tagBefore(markup, '여백에 글쓰기')).toContain('--adaptiveBlue700');
  });

  it('고른 책 아래 박스가 선다 — effect가 안 도는 정적 렌더에서는 로딩이다', () => {
    const markup = shelf([book(1, '데미안', 'READING')], { selectedId: 1 });

    expect(markup).toContain('data-margin-box');
    expect(markup).toContain('불러오는 중');
  });

  it('비공개 책에도 선다 — 비공개 책의 여백은 나만 보는 메모다(결정 2)', () => {
    const markup = shelf([book(1, '메모책', 'READING', { isPublic: false })], { selectedId: 1 });

    expect(markup).toContain('여백에 글쓰기');
    expect(markup).toContain('data-margin-box');
  });

  it('핸들이 없으면 글쓰기도 박스도 없다 — 「관리」가 홀로 남는다', () => {
    const markup = shelf([book(1, '데미안', 'READING')], { selectedId: 1, myLoginId: null });

    expect(markup).not.toContain('여백에 글쓰기');
    expect(markup).not.toContain('data-margin-box');
    expect(markup).toContain('관리');
  });

  it('빈 탭에는 손잡이 줄도 박스도 없다 — 가리킬 책이 없다', () => {
    const markup = shelf([book(1, '읽는책', 'READING')], { tab: 'FINISHED' });

    expect(markup).toContain('다 읽은 책이 없어요');
    expect(markup).not.toContain('여백에 글쓰기');
    expect(markup).not.toContain('data-margin-box');
  });
});

/**
 * 관리 시트 — 액션 넷을 상시 노출하지 않고 「관리」 뒤로 접었다. 정적 렌더라 탭해서 열 수 없으므로
 * 열린 상태를 프롭으로 받아 계측한다(홈 `BookSheet`와 같은 처지).
 */
describe('관리 시트', () => {
  const books = [book(1, '데미안', 'READING', { isPublic: true })];
  const actions: LibrarySheet = { kind: 'actions', confirmDelete: false, confirmPublish: false };

  it('지금 상태를 뺀 나머지 두 곳으로 옮기는 길만 준다', () => {
    const markup = shelf(books, { selectedId: 1, sheet: actions });

    expect(markup).toContain('다 읽음(으)로 옮기기');
    expect(markup).toContain('읽고 싶어요(으)로 옮기기');
    expect(markup).not.toContain('읽는 중(으)로 옮기기');
  });

  it('공개된 책이면 비공개로 되돌리는 길을 준다', () => {
    expect(shelf(books, { selectedId: 1, sheet: actions })).toContain('비공개로 바꾸기');
  });

  it('확인 전에는 「정말 삭제」가 없다 — 오삭제는 한 탭 거리가 아니다', () => {
    const markup = shelf(books, { selectedId: 1, sheet: actions });

    expect(markup).toContain('서재에서 삭제');
    expect(markup).not.toContain('정말 삭제');
  });

  it('구매 링크가 있으면 사러 가는 길과 제휴 고지를 함께 준다', () => {
    const withLink = [book(1, '데미안', 'READING', { purchaseLink: 'https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=1&ttbkey=k' })];

    const markup = shelf(withLink, { selectedId: 1, sheet: actions });

    expect(markup).toContain('알라딘에서 구매');
    expect(markup).toContain('제휴 링크');
  });

  /**
   * 고지문구가 구매 줄과 <b>한 몸</b>이어야 하는 이유: 살 곳이 없는데 "수수료를 받는다"만 남으면
   * 사용자에게 거짓 고지가 된다. 둘을 각 시트에서 따로 조립하면 한쪽만 빠뜨리기 쉬워서 함께 묶었고,
   * 이 단언이 그 묶음을 지킨다.
   */
  it('구매 링크가 없으면 고지문구도 함께 사라진다 — 살 곳 없는 고지는 거짓말이다', () => {
    const markup = shelf(books, { selectedId: 1, sheet: actions }); // 기본 픽스처의 purchaseLink는 null

    expect(markup).not.toContain('알라딘에서 구매');
    expect(markup).not.toContain('제휴 링크');
  });

  /**
   * 공개 전환 확인 단계 — 「공개로 바꾸기」를 누르면 곧바로 전환하지 않고 <b>무엇이 새는지</b>를 먼저 말한다.
   * 삭제 확인과 같은 골격이되 색은 danger가 아니다: 파괴가 아니라 노출이다.
   */
  /**
   * 2026-08-22 — 옛 문구는 「팔로워에게 보여요」였는데 <b>실제보다 적게 고지</b>하고 있었다. 그 글 중
   * 「모두의 여백」에 올린 것은 팔로워가 아니라 전체에게 열렸고(비공개 책에서도 켤 수 있다), 팔로우
   * 축이 사라진 지금은 <b>모든 글</b>이 그렇다. 되돌리기 어려운 행동 직전의 고지라 범위를 좁게 말하면
   * 안 된다 — 「누구에게나」가 이 문장의 핵심이다.
   */
  it('글이 있는 비공개 책의 확인 단계는 새는 글 수를 말하고 다른 길을 감춘다', () => {
    const memo = [book(1, '메모책', 'READING', { isPublic: false, storyCount: 2 })];
    const markup = shelf(memo, { selectedId: 1, sheet: { kind: 'actions', confirmDelete: false, confirmPublish: true } });

    expect(markup).toContain('여백에 남긴 글 2개가 누구에게나 보여요.');
    expect(markup).not.toContain('팔로워');
    expect(markup.match(/공개로 바꾸기/g)).toHaveLength(1); // 행이 아니라 확인 버튼 하나뿐
    expect(markup).toContain('취소');
    expect(markup).not.toContain('옮기기');
    expect(markup).not.toContain('서재에서 삭제');
  });

  it('확인 단계에서는 삭제와 취소만 남는다', () => {
    const markup = shelf(books, { selectedId: 1, sheet: { kind: 'actions', confirmDelete: true, confirmPublish: false } });

    expect(markup).toContain('정말 삭제');
    expect(markup).toContain('취소');
    expect(markup).not.toContain('옮기기');
  });
});

/**
 * 공개 전환 확인 — 비공개 책에 쌓아 둔 여백 글이 <b>공개로 바꾸는 순간</b> 팔로워에게 새는 것을 막는
 * 두 번째 방어다(첫째는 쓸 때의 캡션). 되돌리는 방향(공개→비공개)이나 글이 없는 책은 묻지 않는다 —
 * 물어서 얻을 것이 없는 자리에 확인을 두면 확인 자체가 무의미해진다.
 */
describe('공개 전환 확인 판정 (needsPublishConfirm)', () => {
  it('비공개 + 남긴 글이 있으면 묻는다', () => {
    expect(needsPublishConfirm(book(1, '메모책', 'READING', { isPublic: false, storyCount: 2 }))).toBe(true);
  });

  it('비공개인데 글이 0개면 묻지 않는다 — 샐 글이 없다', () => {
    expect(needsPublishConfirm(book(1, '메모책', 'READING', { isPublic: false, storyCount: 0 }))).toBe(false);
  });

  it('이미 공개된 책이면 묻지 않는다 — 그 행은 비공개로 되돌리는 방향이다', () => {
    expect(needsPublishConfirm(book(1, '공개책', 'READING', { isPublic: true, storyCount: 5 }))).toBe(false);
  });

  it('필드가 없는 옛 서버 응답은 0으로 취급해 묻지 않는다 — 보안 게이트가 아니라 고지 UX라 fail-open', () => {
    expect(needsPublishConfirm(book(1, '메모책', 'READING', { isPublic: false }))).toBe(false);
  });
});

/** 펼쳐보기 — 캐러셀로 훑기 답답한 권수를 격자로 한 번에 본다(화면 이동 없이 같은 화면 위). */
describe('펼쳐보기 시트', () => {
  const books = [book(1, '가', 'READING'), book(2, '나', 'READING'), book(3, '다', 'READING'), book(4, '라', 'FINISHED')];

  it('고른 탭의 책을 전부 격자에 편다', () => {
    const markup = shelf(books, { selectedId: 1, sheet: { kind: 'grid' } });

    expect(markup).toContain('읽는 중 3권');
    expect(markup).toContain('repeat(3,minmax(0,1fr))');
    expect(markup).not.toContain('라');
  });
});

/**
 * 격자 본체(`BookGrid`) — 시트 껍데기에서 꺼내 <b>책방이 본문에 그대로 인라인</b>할 수 있게 했다.
 * 서재는 고르는 격자(시트), 책방은 보기만 하는 격자다 — 그 차이를 `onPick`의 유무가 진다.
 */
describe('책 격자 (BookGrid)', () => {
  const rows = [
    { id: 1, title: '자바 최적화', coverUrl: null },
    { id: 2, title: '데미안', coverUrl: null },
  ];
  const grid = (onPick?: (id: number) => void) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookGrid rows={rows} selectedId={1} onPick={onPick} />
      </TDSMobileProvider>,
    );

  it('고를 수 있으면 각 칸이 버튼이다 — 서재의 「펼쳐보기」 시트가 이 경로다', () => {
    const markup = grid(() => {});

    expect(markup).toContain('data-grid-title="자바 최적화"');
    expect(markup).toContain('<button');
  });

  it('고를 수 없으면 버튼이 아니다 — 눌러도 아무 일 없는 칸을 버튼처럼 보이게 하지 않는다(#788과 같은 규율)', () => {
    const markup = grid();

    expect(markup).toContain('data-grid-title="자바 최적화"');
    expect(markup).not.toContain('<button');
  });

  /**
   * 긴 제목이 줄 높이를 키울 때의 어긋남 — 실기기에서 표지가 저 혼자 내려앉아 보인 버그(2026-08-16).
   * 격자 기본값 `stretch`가 모든 칸을 그 줄 최대 높이로 늘리는데, `<button>`은 남는 높이만큼
   * 내용을 세로 가운데로 미는 UA 기본 동작이 있어 <b>짧은 제목 칸의 표지만 아래로 밀린다</b>.
   * 실측(390px 목 모드): 칸 top은 셋 다 514인데 표지 top이 550 / 514 / 550.
   */
  it('칸을 늘리지 않는다 — 늘어난 버튼은 표지를 세로 가운데로 밀어 긴 제목 옆에서 저 혼자 내려앉는다', () => {
    expect(grid(() => {})).toContain('align-items:start');
  });

  /**
   * 24시간 안에 새 글이 달린 책 — 표지가 발광한다(책방 격자 전용 선택 필드).
   * 서재는 `fresh`를 안 넘기므로 아무것도 달라지지 않아야 한다 — 그 회귀를 여기서 못 박는다.
   */
  describe('새 글 발광 (fresh)', () => {
    const fresh = () =>
      renderToStaticMarkup(
        <TDSMobileProvider userAgent={userAgent}>
          <BookGrid
            rows={[
              { id: 1, title: '자바 최적화', coverUrl: null, fresh: true },
              { id: 2, title: '데미안', coverUrl: null },
            ]}
            selectedId={null}
            onPick={() => {}}
          />
        </TDSMobileProvider>,
      );

    it('새 글이 달린 책에만 점 배지와 pulse 클래스를 단다', () => {
      const markup = fresh();

      expect(markup.match(/data-fresh-dot/g)).toHaveLength(1);
      expect(markup.match(/margin-fresh/g)).toHaveLength(1);
    });

    it('색·움직임만으로 구분 못 하는 사람을 위해 이름에도 「새 글」을 남긴다', () => {
      expect(fresh()).toContain('aria-label="자바 최적화 새 글"');
    });

    it('fresh를 안 넘기는 서재 격자는 배지도 pulse도 없다 — 책방 전용 표식이 새어 나가지 않게', () => {
      const markup = grid(() => {});

      expect(markup).not.toContain('data-fresh-dot');
      expect(markup).not.toContain('margin-fresh');
    });
  });
});

/** 엔터 제출 — 모바일 키보드의 「완료」가 아무 일도 안 해 검색 버튼을 따로 눌러야 했다. */
describe('책 검색 엔터 제출', () => {
  it('제목 입력을 form으로 감싼다', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} onOpenBookMargin={() => {}} />
      </TDSMobileProvider>,
    );

    expect(markup).toContain('<form');
  });
});

/**
 * 검색 손잡이 — 칸 <b>아래 전폭 버튼</b>을 걷고 <b>칸 안 아이콘</b> 하나로 옮겼다(2026-08-21).
 *
 * <p>위 「엔터 제출」이 form을 씌워 엔터를 살린 순간부터, 아래 버튼은 사실상 죽은 자리였다 — 사람은
 * 제목을 치고 엔터를 치지 굳이 아래로 손을 내리지 않는다(사용자 지적 2026-08-21).
 *
 * <p>⚠️ <b>`enterKeyHint`가 이 변경의 절반이다.</b> 버튼만 걷고 이걸 안 넣으면 키캡이 「완료」로 남아,
 * 눌러도 되는지 모르는 사람에게는 <b>제출 수단이 통째로 사라진 화면</b>이 된다. 그래서 「버튼이 없다」와
 * 「키캡이 검색이다」를 <b>따로</b> 단언한다 — 하나만 봐서는 반쪽 변경이 초록으로 통과한다.
 */
describe('책 검색 손잡이', () => {
  const search = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} onOpenBookMargin={() => {}} />
      </TDSMobileProvider>,
    );

  it('칸 아래 전폭 「검색」 버튼이 없다 — 손잡이가 둘이면 통일이 아니라 중복이다', () => {
    // 글자 「검색」을 품은 요소가 없어야 한다. 아이콘 손잡이는 이름을 aria-label로 다니 여기 안 걸린다.
    expect(search()).not.toMatch(/>검색</);
  });

  it('키보드 엔터키가 「검색」이라고 적힌다 — 「완료」로는 눌러도 되는지 알 수 없다', () => {
    // ⚠️ 대소문자를 가리지 않는다. React 18의 정적 렌더는 속성 이름을 **camelCase 그대로**
    //    뱉고(`enterKeyHint`), 브라우저는 HTML 파싱에서 소문자로 바꾼다(`enterkeyhint`) — 같은
    //    속성인데 철자가 둘이다. 여기서 재는 것은 철자가 아니라 「그 속성이 실려 나가는가」다.
    expect(search()).toMatch(/enterkeyhint="search"/i);
  });

  it('아이콘 손잡이에 이름이 붙어 있다 — 그림뿐인 버튼은 읽어 줄 이름이 없다', () => {
    expect(search()).toContain('aria-label="검색"');
  });

  /**
   * 손잡이는 <b>form이 제출한다</b>(`type="submit"`, 자체 `onClick` 없음).
   *
   * <p>처음엔 `onClick`도 달았는데, form 안의 submit 버튼은 탭 한 번에 <b>click과 submit이 둘 다</b>
   * 돌아 검색이 두 번 나갔다(브라우저 실측: click 1 + submit 1). GET이라 화면은 멀쩡해 보이고 알라딘
   * 호출만 두 배가 되는 <b>조용한 낭비</b>였다.
   *
   * <p>그렇다고 `type="button"`으로 바꾸면 이번엔 <b>아무 일도 안 일어난다</b> — 그때는 `onClick`이
   * 필수가 된다. 그 실수를 여기서 막는다(정적 렌더는 `onClick` 유무를 못 보므로 type을 못 박는 것이
   * 이 하니스가 걸 수 있는 유일한 가드다).
   */
  it('손잡이가 form을 제출한다 — type이 submit이라 핸들러 없이도 눌린다', () => {
    expect(search()).toMatch(/<button[^>]*type="submit"[^>]*aria-label="검색"/);
  });
});

/**
 * 검색칸 여백 — 제목과 칸 사이가 <b>63px</b>이었다(사용자 지적 2026-08-21: 「공백이 너무 넓어」).
 *
 * <p>그 63px의 절반 가까이가 <b>「책 제목」 라벨 자리</b>였다. TDS는 값이 있을 때만 라벨을 띄우고 빈 칸에서는
 * <code>visibility: hidden</code>으로 <b>자리만 잡는다</b> — 사용자가 처음 보는 그 상태에서 27px이
 * <b>아무것도 안 그린 채</b> 서 있었다. 값이 생겨 라벨이 떠도 화면 제목이 이미 「책 추가」라 보탤 말이 없다.
 *
 * <p>⚠️ 걷은 것은 <b>라벨이지 이름이 아니다</b>. 그래서 「안 그린다」와 「이름이 있다」를 <b>따로</b> 단언한다 —
 * 하나만 보면 읽어 줄 이름째 지운 변경이 초록으로 통과한다.
 *
 * <p>칸 자체의 높이(73 → 53px)는 css라 이 하니스가 못 본다 — 실브라우저가 게이트다.
 */
describe('책 검색칸 — 안 보이는 라벨 자리를 걷었다', () => {
  const search = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} onOpenBookMargin={() => {}} />
      </TDSMobileProvider>,
    );

  it('「책 제목」 라벨 줄을 그리지 않는다 — 빈 칸에선 아무것도 안 그리면서 27px을 먹던 자리다', () => {
    expect(search()).not.toMatch(/>책 제목</);
  });

  it('칸의 이름은 남는다 — 라벨을 걷는 것과 이름을 지우는 것은 다르다', () => {
    expect(search()).toContain('aria-label="책 제목"');
  });

  it('칸 위 기본 여백 16px을 0으로 넘긴다 — 아래 16px은 카드와 떨어뜨리느라 남긴다', () => {
    expect(search()).toMatch(/padding-top:\s*0(px)?;\s*padding-bottom:\s*16px/);
  });
});

/**
 * 추천 카드 — 검색 버튼을 걷고 남은 자리를 채운다(2026-08-21). 사용자 지적:
 * 「책 추가하기 버튼 누르면 이동하는 페이지가 너무 비어보여」(실측 460px · 화면의 55%가 빈 종이였다).
 *
 * <p>제목·근거 문장은 <b>서버가</b> 만든다 — 화면은 어느 전략(내 저자 / 베스트셀러)으로 뽑혔는지 모른 채
 * 라벨을 그대로 그린다. 그래서 여기서 재는 것도 「받은 것을 그리는가」와 <b>「없을 때 안 그리는가」</b>다.
 *
 * <p>⚠️ {@code BookSearch} 안에서가 아니라 <b>카드를 직접</b> 렌더해 잰다 — 이 하니스는 정적 렌더라
 * effect가 안 돌아 추천 응답이 영영 도착하지 않는다({@code SearchResultRow}를 꺼낸 것과 같은 사정).
 * 그래서 「검색 결과가 뜨면 추천을 숨긴다」는 배선은 여기서 볼 수 없다 — 실브라우저 게이트가 맡는다.
 */
describe('책 추가 — 추천 카드', () => {
  const reco = (over: Partial<Recommendation> = {}): Recommendation => ({
    title: '김호연의 다른 책',
    reason: '『불편한 편의점』을 읽으셨네요',
    results: [
      {
        title: '망원동 브라더스',
        author: '김호연',
        isbn13: '9788994120720',
        coverUrl: null,
        publisher: '나무옆의자',
        purchaseLink: null,
        category: '소설',
        pubDate: '2013-04-05',
        owned: false,
      },
    ],
    ...over,
  });

  const card = (data: Recommendation) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <RecommendCard data={data} busy={false} onPick={() => {}} />
      </TDSMobileProvider>,
    );

  it('서버가 준 제목·근거·책을 그대로 그린다 — 「왜 이 책인가」가 안 보이면 추천이 광고로 읽힌다', () => {
    const markup = card(reco());

    expect(markup).toContain('김호연의 다른 책');
    expect(markup).toContain('『불편한 편의점』을 읽으셨네요');
    expect(markup).toContain('망원동 브라더스');
  });

  // 「아무것도 안 그린다」를 빈 문자열로 재면 안 된다 — Provider가 전역 <style>을 늘 뱉으므로 영영 실패한다.
  // 카드의 몸통인 <section>이 없는지를 본다.
  it('뽑을 것이 없으면 카드를 아예 안 그린다 — 제목만 있는 빈 카드가 서면 없느니만 못하다', () => {
    expect(card(reco({ title: null, reason: null, results: [] }))).not.toContain('<section');
  });

  /**
   * 저자 40명짜리 책이 이 카드를 통째로 먹은 자리다(실기기 제보 2026-08-21). 서버가 세트를 걸러
   * 이 책 자체는 이제 안 오지만, <b>제목·저자가 긴 다음 책이 언제든 온다</b> — 줄 높이는 데이터가
   * 아니라 화면이 정해야 한다. 검색 결과 행과 같은 고침이고, 같은 이유로 둘 다 필요하다.
   */
  it('한 줄이 창을 넘지 못하게 제목·저자를 자른다 — 카드 높이가 데이터에 안 휘둘린다', () => {
    const markup = card(
      reco({
        results: [
          {
            title: '노벨라33 세트 - 전33권',
            author: '미겔 데 세르반떼스, 메리 셸리, 오노레 드 발자크, 알렉산드르 세르게예비치 푸시킨',
            authorShort: '미겔 데 세르반떼스 외 3명',
            isbn13: '9788972756194',
            coverUrl: null,
            publisher: '현대문학',
            purchaseLink: null,
            category: null,
            pubDate: null,
            owned: false,
          },
        ],
      }),
    );

    expect(markup).toContain('미겔 데 세르반떼스 외 3명');
    expect(markup).not.toContain('오노레 드 발자크'); // 축약본이 원문을 대신했다
    expect((markup.match(/white-space:nowrap/g) ?? []).length).toBeGreaterThanOrEqual(2);
  });

  it('제목이 있어도 책이 0권이면 안 그린다 — 머리만 남은 카드를 막는다', () => {
    const markup = card(reco({ results: [] }));

    expect(markup).not.toContain('<section');
    expect(markup).not.toContain('김호연의 다른 책');
  });
});

/**
 * 입력칸 힌트 — 「예: 자바 최적화」가 <b>입력값과 같은 잉크색·같은 굵기</b>로 떠 있어, 빈 칸이 아니라
 * 이미 무언가 적힌 칸처럼 보이던 자리다(사용자 제보 2026-08-21).
 *
 * <p>고칠 것이 둘인데 성질이 다르다. <b>문구</b>는 마크업에 실려 여기서 계측되고, <b>흐림</b>은 css라
 * 정적 렌더가 스타일을 적용하지 않아 여기서 못 본다 — 그쪽은 토큰 정의로 `typography.test.tsx`가 지킨다.
 *
 * <p>예시 제목을 뺀 이유: 예시는 「이 칸에 그런 종류를 적어라」를 가르치지만, 이 칸은 무엇을 적는지가
 * 이미 자명하다(화면 제목이 「책 추가」다). 남는 건 진짜 값과 헷갈리는 비용뿐이고, 실제로 그렇게 읽혔다.
 */
describe('책 검색 입력칸 힌트', () => {
  const search = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} onOpenBookMargin={() => {}} />
      </TDSMobileProvider>,
    );

  it('무엇을 적는 자리인지만 말한다 — 특정 책을 예로 들지 않는다', () => {
    const markup = search();

    expect(markup).toContain('placeholder="책 이름을 적어주세요"');
    // 「예:」가 돌아오면 어떤 제목이든 같은 병이 재발한다 — 문구가 아니라 **예시라는 형식**을 막는다.
    expect(markup).not.toContain('예:');
  });
});

/**
 * 책 추가의 나가는 길 — 하단 버튼을 걷고 **상단 「돌아가기」 하나**로 통일했다(2026-08-16).
 * 앱 전체가 같은 자리·같은 모양으로 나가야 한다. 개수를 세는 것이 핵심이다 — 위아래 둘이 되면
 * 「통일」이 아니라 중복이고, 존재 단언만으로는 그걸 못 잡는다.
 */
describe('책 추가 — 나가는 길', () => {
  const search = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} onOpenBookMargin={() => {}} />
      </TDSMobileProvider>,
    );

  it('「돌아가기」가 정확히 하나다 — 상단 손잡이와 하단 버튼이 겹치지 않는다', () => {
    expect(search().match(/돌아가기/g)).toHaveLength(1);
  });

  it('제목보다 위에 온다 — 목록이 길어도 나갈 길을 찾아 내려가지 않는다', () => {
    const markup = search();

    expect(markup).toContain('돌아가기');
    expect(markup.indexOf('돌아가기')).toBeLessThan(markup.indexOf('책 추가'));
  });

  it('추가 요청 중에는 잠근다 — 응답 전에 나가면 결과를 못 본다', () => {
    const busy = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSearch busy error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} onOpenBookMargin={() => {}} />
      </TDSMobileProvider>,
    );
    // 손잡이 앞부분만 잘라 본다. 한가할 때도 함께 봐야 의미가 있다 — 손잡이가 화면 아래에 있으면
    // 이 구간에 입력·검색 버튼의 disabled가 섞여 들어와 busy 단언만으로는 저절로 참이 된다(T-181).
    const backAt = (m: string) => m.slice(0, m.indexOf('돌아가기'));

    expect(backAt(busy)).toContain('disabled');
    expect(backAt(search())).not.toContain('disabled');
  });
});

/**
 * 담을 곳 고르기 — 검색 결과를 탭하면 곧바로 「읽는 중」으로 들어가던 자리다. 읽고 싶어 담은 책까지
 * 읽는 중이 된 뒤 서재에서 다시 옮겨야 했다(사용자 제보 2026-08-19).
 *
 * <p>정적 렌더라 탭해서 열 수 없으므로 <b>시트를 직접 렌더해</b> 계측한다(관리 시트와 같은 처지).
 */
describe('책 담을 곳 고르기', () => {
  const row: SearchRow = {
    title: '자바 최적화',
    author: '김한도',
    isbn13: '9791162242452',
    coverUrl: null,
    publisher: null,
    purchaseLink: null,
    category: null,
    pubDate: null,
    owned: false,
  };

  const sheet = (busy = false) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <AddStatusSheet row={row} busy={busy} onPick={() => {}} onClose={() => {}} />
      </TDSMobileProvider>,
    );

  it('세 곳을 서재 탭과 같은 어휘·순서로 준다 — 같은 상태가 두 이름으로 보이지 않게', () => {
    const markup = sheet();

    expect(markup).toContain('읽는 중(으)로 담기');
    expect(markup).toContain('다 읽음(으)로 담기');
    expect(markup).toContain('읽고 싶어요(으)로 담기');
    expect(markup.indexOf('읽는 중')).toBeLessThan(markup.indexOf('읽고 싶어요'));
  });

  it('고른 책 제목을 시트 제목으로 세운다 — 어느 책을 담는 중인지가 시트 안에 남는다', () => {
    expect(sheet()).toContain('자바 최적화');
  });

  /**
   * 담기 직전이 사러 갈 의향이 가장 높은 자리다 — 서재에 이미 있는 책(관리 시트)보다 여기가 앞선다.
   * 두 시트가 <b>같은 컴포넌트</b>를 쓰므로 라벨·고지가 갈릴 수 없다.
   */
  it('구매 링크가 있으면 담기 옵션과 함께 사러 가는 길도 준다', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <AddStatusSheet
          row={{ ...row, purchaseLink: 'https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=1&ttbkey=k' }}
          busy={false}
          onPick={() => {}}
          onClose={() => {}}
        />
      </TDSMobileProvider>,
    );

    expect(markup).toContain('알라딘에서 구매');
    expect(markup).toContain('제휴 링크');
    // 담기가 이 시트의 본래 일이다 — 구매가 그걸 밀어내지 않는다.
    expect(markup.indexOf('읽는 중(으)로 담기')).toBeLessThan(markup.indexOf('알라딘에서 구매'));
  });

  it('구매 링크가 없으면 고지문구도 함께 사라진다 — 살 곳 없는 고지는 거짓말이다', () => {
    const markup = sheet(); // 기본 픽스처의 purchaseLink는 null

    expect(markup).not.toContain('알라딘에서 구매');
    expect(markup).not.toContain('제휴 링크');
  });

  it('추가 요청 중에는 잠근다 — 두 번 눌러 두 권이 들어가지 않게', () => {
    expect(sheet(true)).toContain('disabled');
    expect(sheet()).not.toContain('disabled');
  });

  it('검색 화면 진입 직후에는 시트가 없다 — 화면을 덮는 것은 사용자가 부른 뒤에만(T-183)', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} onOpenBookMargin={() => {}} />
      </TDSMobileProvider>,
    );

    expect(markup).not.toContain('role="dialog"');
    expect(markup).not.toContain('담기');
  });
});

/**
 * 이미 서재에 있는 책 — <b>눌러도 아무 일이 없는 칸</b>을 눈에 보이게 한다(사용자 제보 2026-08-19).
 *
 * <p>예전엔 저자 이름 뒤에 「 · 이미 서재에 있어요」로 이어 붙였다. 저자와 이 안내가 같은
 * `st12 · grey600` 한 벌이라 저자 이름의 일부처럼 읽혔다. 정작 그 행은 `disabled`라 탭이 막혀 있어서,
 * 안내를 놓친 사람에겐 눌러도 아무 반응이 없는 칸이 된다 — 앱이 멈춘 것으로 읽히는 자리다.
 *
 * <p>그래서 신호를 둘로 나눴다: <b>읽어야 아는 것</b>(칩)과 <b>읽지 않아도 보이는 것</b>(눕힌 종이·도장·
 * 접힌 모서리 = `.book-owned`). 뒤엣것이 「이 칸은 끝난 칸」을 형태로 말하므로 글자를 안 읽어도 전달된다.
 *
 * <p>정적 렌더라 `searchBooks` 응답이 안 돌아 `BookSearch` 통째로는 결과 행이 영영 안 뜬다 —
 * 그래서 행을 `SearchResultRow`로 꺼내 직접 렌더해 계측한다(`AddStatusSheet`와 같은 처지).
 */
describe('검색 결과 — 이미 서재에 있는 책', () => {
  const found: SearchRow = {
    title: '미움받을 용기',
    author: '기시미 이치로',
    isbn13: '9788996991342',
    coverUrl: null,
    publisher: null,
    purchaseLink: null,
    category: null,
    pubDate: null,
    owned: false,
  };

  const row = (owned: boolean, busy = false) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <SearchResultRow row={{ ...found, owned }} busy={busy} onPick={() => {}} />
      </TDSMobileProvider>,
    );

  /**
   * 목록 한 줄은 <b>높이가 고정</b>이어야 한다 — 실기기 제보(2026-08-21): 저자 40명짜리 책
   * (「노벨라33 세트 - 전33권」)이 한 줄을 세로 900px로 부풀려 추천 카드를 통째로 먹었다.
   *
   * <p>카드 창은 `calc(5 × --reco-row + --reco-peek)` = 「75px짜리 줄 5개 + 반 줄」로 잡혀 있는데,
   * <b>줄 높이는 가정일 뿐 강제가 아니었다</b>. 줄 하나가 창보다 커지면 그 계산도, 반 줄이 말하던
   * 「더 있다」 신호도 함께 죽는다.
   *
   * <p>⚠️ 자르기를 TDS `Text`에 직접 걸지 않는다 — TDS가 인라인 `display`를 자기 값으로 덮어써
   * `-webkit-box` line-clamp가 죽는다(서재 격자 제목이 그래서 두 줄 자르기를 포기했다). 감싸는
   * `div`에 `nowrap + ellipsis`로 거는 것이 이 레포에서 검증된 방식이다.
   */
  it('제목과 저자를 각각 한 줄로 자른다 — 데이터가 줄 높이를 못 깨게', () => {
    const markup = row(false);
    const clamps = markup.match(/white-space:nowrap/g) ?? [];

    expect(clamps.length).toBeGreaterThanOrEqual(2); // 제목 줄 + 저자 줄
    expect(markup).toContain('text-overflow:ellipsis');
  });

  /**
   * 표시는 짧게, <b>저장은 원문으로</b> — 「담기」가 이 행의 `author`를 그대로 서버로 보내 DB에
   * 저장하므로, 축약본은 반드시 별도 필드(`authorShort`)여야 한다.
   */
  it('저자는 축약본을 그린다 — 원문은 담기용으로 그대로 둔다', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <SearchResultRow
          row={{
            ...found,
            author: '레프 니콜라예비치 톨스토이 (지은이), 연진희 (옮긴이)',
            authorShort: '레프 니콜라예비치 톨스토이',
          }}
          busy={false}
          onPick={() => {}}
        />
      </TDSMobileProvider>,
    );

    expect(markup).toContain('레프 니콜라예비치 톨스토이');
    expect(markup).not.toContain('옮긴이'); // 축약본이 원문을 대신했다
  });

  /**
   * 미니앱과 서버는 따로 배포된다 — 미니앱이 먼저 나가면 옛 서버엔 `authorShort`가 없다.
   * 그때 저자 줄이 빈칸이 되면 <b>배포 순서에 화면이 의존</b>하게 된다.
   */
  it('축약본이 없으면 원문으로 떨어진다 — 옛 서버에서도 저자가 빈칸이 되지 않는다', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <SearchResultRow row={{ ...found, author: '기시미 이치로' }} busy={false} onPick={() => {}} />
      </TDSMobileProvider>,
    );

    expect(markup).toContain('기시미 이치로');
  });

  it('담긴 책엔 「서재에 있어요」 칩이 선다', () => {
    expect(row(true)).toContain('서재에 있어요');
  });

  it('그 말을 한 번만 한다 — 저자 줄로 이사가 끝났는지가 여기서 갈린다', () => {
    expect(row(true).match(/서재에 있어요/g)).toHaveLength(1);
    expect(row(true)).not.toContain('이미 서재에 있어요'); // 옛 문구가 저자 뒤에 남아 있으면 실패
  });

  it('읽지 않아도 보이는 표식을 함께 단다 — 문구는 읽어야 알지만 형태는 그냥 보인다', () => {
    expect(row(true)).toContain('book-owned');
  });

  /**
   * 「여백 N」 배지 — 검색 행에서 <b>책축 여백</b>으로 가는 문(2026-08-22 책축 개방). 낯선 책에 닿는
   * 유일한 경로가 검색이라, 이 배지가 없으면 「이 책의 여백」 화면에 도달할 방법 자체가 없다.
   *
   * <p>배지는 행 <b>바깥의 형제 버튼</b>이다 — 행 버튼 안에 버튼을 넣으면 마크업이 깨지고, 담긴 책은
   * 행이 `disabled`라 안쪽에 있으면 눌리지도 않는다(담긴 책일수록 여백을 보고 싶다).
   */
  const badged = (marginCount?: number, owned = false) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <SearchResultRow
          row={{ ...found, owned }}
          busy={false}
          onPick={() => {}}
          marginCount={marginCount}
          onOpenMargin={() => {}}
        />
      </TDSMobileProvider>,
    );

  it('함께 걸린 글이 있으면 「여백 N」 배지가 뜬다', () => {
    expect(badged(3)).toContain('여백 3');
  });

  it('0이면 배지를 안 그린다 — 빈 상태를 숫자로 박제하면 「아무도 안 썼다」가 행마다 반복된다', () => {
    const markup = badged(0);

    expect(markup).toContain('미움받을 용기'); // 행 자체는 그려졌다(부재 단언의 쌍)
    expect(markup).not.toContain('여백 0');
  });

  it('서버가 키를 안 준 책(undefined)도 배지가 없다 — 0인 책은 응답에 키 자체가 없다', () => {
    const markup = badged(undefined);

    expect(markup).toContain('미움받을 용기');
    expect(markup).not.toContain('여백 ');
  });

  it('담긴 책의 배지는 눌린다 — 행은 `disabled`지만 배지는 그 바깥이다', () => {
    expect(badged(2, true)).toMatch(/aria-label="이 책의 여백 2개 보기"/);
  });

  it('아직 없는 책엔 칩도 표식도 없다 — 담을 수 있는 칸이 담긴 칸처럼 보이지 않게', () => {
    const markup = row(false);

    expect(markup).not.toContain('서재에 있어요');
    expect(markup).not.toContain('book-owned');
  });

  it('담긴 책은 여전히 눌리지 않는다 — 보이게 만든 것이지 담기게 만든 게 아니다', () => {
    expect(row(true)).toContain('disabled');
    expect(row(false)).not.toContain('disabled');
  });
});

/**
 * 「책 추가」 칸 — 화면 맨 아래 전폭 버튼이던 것이 <b>캐러셀 0번</b>으로 들어왔다(2026-08-21).
 *
 * <p>홈의 「책 없이」 칸과 같은 부품(`leadCard`)이고 규약도 같다: <b>0번은 언제나 특수 칸</b>이다.
 * 두 화면이 한 문장으로 통일되고, 「내 책을 훑는 손짓」과 「추가」가 같은 동작이 된다.
 *
 * <p>게이트와 안내는 여전히 <b>한 몸</b>이다 — 서버 플래그(`searchEnabled`)가 칸과 코치마크를 함께
 * 끈다. 따로 두면 플래그가 꺼진 날 없는 칸을 가리키는 안내만 남는다.
 */
describe('책 추가 칸 (캐러셀 0번)', () => {
  const GUIDE = '읽을 책은 여기서 찾아 담아요';
  const books = [book(1, '읽는책', 'READING'), book(2, '딴책', 'READING')];

  it('전폭 「책 추가하기」 버튼이 사라졌다 — 칸이 그 일을 대신한다', () => {
    expect(shelf(books)).not.toContain('책 추가하기');
  });

  it('점선 칸이 실제 책보다 앞에 선다 — 0번이라는 규약이 곧 마크업 순서다', () => {
    const markup = shelf(books);

    expect(markup.indexOf('data-lead-card')).toBeGreaterThan(-1);
    expect(markup.indexOf('data-lead-card')).toBeLessThan(markup.indexOf('data-cover-title'));
  });

  it('표지와 같은 크기의 점선 상자다 — 색 상자면 실제 표지와 구분이 안 된다', () => {
    expect(shelf(books)).toContain('border:2px dashed');
  });

  /**
   * 칸이 가운데 오면 아래가 통째로 갈린다 — 캐러셀 문법이 「가운데 온 것이 곧 대상」이라,
   * 가리킬 책이 없는데 그 책의 손잡이·여백이 서 있으면 <b>다른 책의 것을 그 칸의 것으로 읽는다</b>.
   */
  describe('칸이 가운데 왔을 때 (selectedId === null)', () => {
    const centered = () => shelf(books, { selectedId: null });

    it('제목 자리가 「책 추가」가 되고 무엇을 하는 칸인지 한 줄로 말한다', () => {
      const markup = centered();

      expect(markup).toContain('data-selected-book="책 추가"');
      expect(markup).toContain('제목으로 검색해 서재에 담아요');
    });

    it('손잡이가 「검색해서 담기」 하나로 갈린다 — 가리킬 책이 없으니 여백·관리는 뜻이 없다', () => {
      const markup = centered();

      expect(markup).toContain('검색해서 담기');
      expect(markup).not.toContain('여백에 글쓰기');
      expect(markup).not.toContain('관리');
    });

    it('여백 박스가 없다 — 남의 책 여백을 이 칸의 것으로 읽게 두지 않는다', () => {
      expect(centered()).not.toContain('전체 보기');
    });
  });

  it('책이 가운데면 아래는 지금 그대로다 — 칸이 생겼다고 평소 화면이 바뀌지 않는다', () => {
    const markup = shelf(books, { selectedId: 1 });

    expect(markup).toContain('여백에 글쓰기');
    expect(markup).not.toContain('검색해서 담기');
  });

  /**
   * `undefined`(아직 안 고름)와 `null`(칸을 고름)은 다른 값이다 — 하나로 합치면 서재를 열 때마다
   * 「책 추가」가 가운데인 채로 시작한다(홈이 먼저 밟은 구분 — `App.tsx`).
   */
  it('아직 안 골랐으면 첫 책이 가운데다 — 칸을 고른 것과 다르다', () => {
    expect(shelf(books)).toContain('data-selected-book="읽는책"');
  });

  it('검색이 꺼져 있으면 칸도 안내도 없다 — 없는 칸을 가리키지 않는다', () => {
    dismissCoachmark('library');

    const markup = shelf(books, { searchEnabled: false });

    expect(markup).not.toContain('data-lead-card');
    expect(markup).not.toContain(GUIDE);
  });

  it('바로 앞 걸음(서재 설명)을 본 뒤에 칸과 안내가 함께 뜬다 — 흐름이 이 화면으로 데려온 직후다', () => {
    dismissCoachmark('library');

    const markup = shelf(books);

    expect(markup).toContain('data-lead-card');
    expect(markup).toContain(GUIDE);
  });

  it('앞 걸음 전에는 안내가 뜨지 않는다 — 딤 두 장이 겹치지 않는다', () => {
    expect(shelf(books)).not.toContain(GUIDE);
  });

  it('한 번 본 사람에게는 칸만 남는다', () => {
    dismissCoachmark('library');
    dismissCoachmark('add-book');

    const markup = shelf(books);

    expect(markup).toContain('data-lead-card');
    expect(markup).not.toContain(GUIDE);
  });
});

/**
 * 세션 캐시 첫 렌더 — 탭을 오갈 때마다 화면이 `<Loading/>`으로 비었다가 다시 차던 자리다.
 *
 * <p>이 화면은 재마운트마다 `fetchShelf()`를 다시 돌린다(재검증은 그대로 둔다). 바뀐 것은 **첫 렌더의
 * 출발점**뿐이다 — 지난 성공 응답이 캐시에 있으면 `books`가 `null`이 아닌 채로 시작해 로딩 분기를
 * 건너뛴다. effect가 안 도는 정적 렌더라 여기서 보이는 것은 오직 캐시가 채운 것이다(양성 단언).
 */
describe('서재 세션 캐시', () => {
  function library(myLoginId: string | null = 'goospel') {
    return renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Library
          myLoginId={myLoginId}
          onError={() => {}}
          onShelfChanged={() => {}}
          onOpenMargin={() => {}}
          onComposeMargin={() => {}}
          onOpenBookMargin={() => {}}
        />
      </TDSMobileProvider>,
    );
  }

  it('캐시가 비어 있으면 지금처럼 로딩부터다 — 캐시는 없던 데이터를 지어내지 않는다', () => {
    const markup = library();

    expect(markup).toContain('불러오는 중');
    expect(markup).not.toContain('데미안');
  });

  it('지난 책장이 캐시에 있으면 첫 렌더부터 책이 선다 — 탭 왕복의 빈 화면이 사라지는 지점', () => {
    cachePut(CACHE_SHELF, { searchEnabled: true, books: [book(1, '데미안', 'READING')] });

    const markup = library();

    expect(markup).toContain('데미안');
    expect(textOf(markup)).toContain('읽는 중 1');
  });

  it('캐시된 책장은 검색 가능 여부까지 되살린다 — 「책 추가」 칸이 한 박자 늦게 나타나지 않게', () => {
    cachePut(CACHE_SHELF, { searchEnabled: true, books: [book(1, '데미안', 'READING')] });

    expect(library()).toContain('data-lead-card');
  });

  it('방금 본 책의 여백은 캐시에서 곧바로 선다 — 캐러셀을 왕복해도 「불러오는 중」으로 안 되돌아간다', () => {
    cachePut(CACHE_SHELF, { searchEnabled: true, books: [book(1, '데미안', 'READING')] });
    cachePut(cacheKeyMargin('goospel', 1), {
      bookId: 1,
      margin: {
        book: { id: 1, title: '데미안', author: '헤세', coverUrl: null },
        ownerNickname: '구스펠',
        self: true,
        entries: [
          { id: 9, text: '캐시에서 살아난 문장', bgCode: 'paper', createdAt: '2026-08-16T00:00:00Z', likeCount: 0, liked: false },
        ],
      },
    });

    const markup = library();

    expect(markup).toContain('캐시에서 살아난 문장');
    // 헤더 글 수 = 내용 상태로 그려졌다는 증거. 숫자가 세리프 span으로 갈렸으므로(시안 2c) 서식이
    // 아니라 글자로 잰다 — 이 단언이 지키려던 것은 「로딩이 아니라 내용이 그려졌다」다.
    expect(textOf(markup)).toContain('여백 1');
    expect(markup).not.toContain('불러오는 중');
  });

  it('남의 여백 캐시는 내 박스에 안 선다 — 키에 loginId가 박혀 있다', () => {
    cachePut(CACHE_SHELF, { searchEnabled: true, books: [book(1, '데미안', 'READING')] });
    cachePut(cacheKeyMargin('nabi', 1), {
      bookId: 1,
      margin: {
        book: { id: 1, title: '데미안', author: '헤세', coverUrl: null },
        ownerNickname: '나비',
        self: false,
        entries: [{ id: 9, text: '남의 문장', bgCode: 'paper', createdAt: '2026-08-16T00:00:00Z', likeCount: 0, liked: false }],
      },
    });

    const markup = library();

    expect(markup).not.toContain('남의 문장');
    expect(markup).toContain('불러오는 중');
  });
});

/**
 * 태그를 걷은 글자 — 탭 라벨·칩은 값 조각만 세리프로 감싸느라 여러 span으로 나뉘어(2c) 마크업 통짜
 * 비교가 안 된다. 그런데 이 단언들이 지키려는 건 서식이 아니라 <b>「이 자리가 무엇을 말하는가」</b>다.
 *
 * <p>⚠️ 태그를 <b>지우지</b> 않고 텍스트 노드를 <b>모은다</b> — `replace(/<[^>]*>/g, '')`는 고장 난 HTML
 * 살균기의 전형이라 CodeQL `js/incomplete-multi-character-sanitization`이 high로 잡는다(B에서 실제로
 * 겪었다). 여기선 살균이 아니지만 레포 스캔에 보안 경보를 남길 값이 없다.
 */
const textOf = (markup: string) =>
  [...markup.matchAll(/>([^<>]+)</g)].map((m) => m[1]).join('');

/** 그 글자를 감싼 여는 태그 — 크기·서체·색·그림자가 인라인 style이라 태그만 잘라 보면 판정된다. */
const tagBefore = (markup: string, text: string) => {
  const at = markup.indexOf(text);
  return at < 0 ? '' : markup.slice(markup.lastIndexOf('<', at), at);
};

/**
 * 시안 2c의 위계 — <b>자리마다 하나씩</b> 못 박는다.
 *
 * <p>B 리뷰가 「`Home.tsx`를 통째로 되돌려도 전 스위트 초록」인 사각을 잡았다. 원인은 `typography.test`의
 * `SCALE` 가드가 <b>계단 위 값끼리의 바꿔치기를 통과시킨다</b>는 것이었다 — 계단에 있는지가 아니라
 * 어느 자리가 어느 칸인지가 값이다. 그래서 C는 처음부터 자리별로 잠근다.
 */
describe('서재 위계 (시안 2c)', () => {
  const read = (extra = {}) => book(1, '미움받을 용기', 'READING', { seconds: 7_200, ...extra });

  it('고른 세그먼트만 굵고 그림자를 진다 — 색만으로는 어느 탭인지 약하다', () => {
    const markup = shelf([read()], { tab: 'READING' });
    const on = tagBefore(markup, '읽는 중');
    const off = tagBefore(markup, '다 읽음');

    expect(on).toContain('font-weight:700');
    expect(on).toContain('box-shadow');
    expect(off).not.toBe('');
    expect(off).not.toContain('box-shadow');
  });

  it('세그먼트 권수는 세리프다 — 탭 이름은 말이고 권수는 값이다', () => {
    const markup = shelf([read()], { tab: 'READING' });
    const at = markup.indexOf('읽는 중');
    const after = markup.slice(at, at + 260);

    expect(after).toContain('Gowun Batang');
  });

  it('「N시간 읽음」 칩은 시간만 세리프다 — 「읽음」까지 세리프면 값이 어디까지인지 흐려진다', () => {
    const markup = shelf([read()], { tab: 'READING', selectedId: 1 });

    expect(tagBefore(markup, '2시간')).toContain('Gowun Batang');
    expect(tagBefore(markup, ' 읽음')).not.toContain('Gowun Batang');
  });

  it('여백 헤더는 16이고 카운트만 세리프다', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <MarginBoxView
          view={{
            book: { id: 1, title: '데미안', author: '헤세', coverUrl: null },
            ownerNickname: '구스펠',
            self: true,
            entries: [],
          }}
          now={0}
          onOpenAll={() => {}}
        />
      </TDSMobileProvider>,
    );

    expect(tagBefore(markup, '여백')).toContain('font-size:16px');
  });

  it('「여백에 글쓰기」는 채움 버튼이다 — 이 화면의 주 동작 하나', () => {
    const tag = tagBefore(shelf([read()], { tab: 'READING', selectedId: 1 }), '여백에 글쓰기');

    expect(tag).not.toBe('');
    expect(tag).toContain('--adaptiveBlue700');
    expect(tag).toContain('#F7F2E8');
  });
});
