import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it } from 'vitest';

import type { BookStatus, MarginEntry, MarginResponse, MyBookSummary, SearchRow } from './api';
import { dismissCoachmark, setCoachmarkWalking } from './coachmark';
import type { LibrarySheet } from './screens/Library';
import {
  AddBookButton,
  AddStatusSheet,
  BookGrid,
  BookSearch,
  MarginBoxView,
  SearchResultRow,
  Shelf,
  marginBoxView,
  metaLine,
  needsPublishConfirm,
  resolveSelected,
} from './screens/Library';
import { stubLocalStorage, userAgent } from './test-fixtures';

beforeEach(() => {
  stubLocalStorage(); // 「책 추가하기」가 렌더 중에 코치마크를 봤는지 읽는다
  setCoachmarkWalking(true); // 인라인 안내는 걷는 중에만 뜬다(잠금 계측은 coachmark.test)
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
    selectedId = null as number | null,
    sheet = null as LibrarySheet,
    myLoginId = 'goospel' as string | null,
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
        onTab={() => {}}
        onSelect={() => {}}
        onSheet={() => {}}
        onAction={() => {}}
        onOpenMargin={() => {}}
        onComposeMargin={() => {}}
        onError={() => {}}
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

    expect(markup).toContain('읽는 중 1');
    expect(markup).toContain('다 읽음 1');
    expect(markup).toContain('읽고 싶어요 1');
  });

  it('빈 탭도 라벨은 남는다 — 탭이 나타났다 사라지면 자리가 흔들린다', () => {
    const markup = shelf([book(1, '읽는책', 'READING')]);

    expect(markup).toContain('다 읽음 0');
    expect(markup).toContain('읽고 싶어요 0');
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

  it('책이 하나도 없으면 추가를 유도한다', () => {
    expect(shelf([])).toContain('아직 책이 없어요');
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
  it('저자는 첫 줄, 읽은 시간·공개 여부는 다음 줄', () => {
    expect(metaLine(book(1, '데미안', 'READING', { author: '헤세', seconds: 7200, isPublic: true }))).toBe(
      '헤세\n2시간 · 공개',
    );
  });

  it('저자가 길어도 줄을 넘겨 섞이지 않는다 — 실기기에서 시간이 이름 사이에 끼어 들던 자리', () => {
    const long = '레프 니콜라예비치 톨스토이 (지은이), 연진희 (옮긴이)';
    expect(metaLine(book(1, '전쟁과 평화 1', 'FINISHED', { author: long, seconds: 39_900, isPublic: true }))).toBe(
      `${long}\n11시간 5분 · 공개`,
    );
  });

  it('아직 안 읽은 책은 시간을 적지 않는다 — 「0분」은 정보가 아니다', () => {
    expect(metaLine(book(1, '코스모스', 'WANT_TO_READ', { author: null }))).toBe('저자 미상\n비공개');
  });

  it('줄바꿈을 살리는 스타일이 실제로 걸린다 — 없으면 두 줄이 한 줄로 붙어 문자열만 바뀐 셈이 된다', () => {
    expect(shelf([book(1, '데미안', 'READING')], { selectedId: 1 })).toContain('white-space:pre-line');
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
    following: false,
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
    bgCode,
    createdAt: new Date(NOW - hoursAgo * HOUR).toISOString(),
    likeCount,
    liked: false,
  });

  const margin = (entries: MarginEntry[]): MarginResponse => ({
    book: { id: 1, title: '데미안', author: '헤세', coverUrl: null, isPublic: true },
    ownerNickname: '구스펠',
    self: true,
    following: false,
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
    expect(markup).toContain('rgba(110,138,106,.14)'); // 세이지 채움 = 주 동작
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

  /**
   * 공개 전환 확인 단계 — 「공개로 바꾸기」를 누르면 곧바로 전환하지 않고 <b>무엇이 새는지</b>를 먼저 말한다.
   * 삭제 확인과 같은 골격이되 색은 danger가 아니다: 파괴가 아니라 노출이다.
   */
  it('글이 있는 비공개 책의 확인 단계는 새는 글 수를 말하고 다른 길을 감춘다', () => {
    const memo = [book(1, '메모책', 'READING', { isPublic: false, storyCount: 2 })];
    const markup = shelf(memo, { selectedId: 1, sheet: { kind: 'actions', confirmDelete: false, confirmPublish: true } });

    expect(markup).toContain('여백에 남긴 글 2개가 팔로워에게 보여요.');
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
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} />
      </TDSMobileProvider>,
    );

    expect(markup).toContain('<form');
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
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} />
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
        <BookSearch busy error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} />
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

  it('추가 요청 중에는 잠근다 — 두 번 눌러 두 권이 들어가지 않게', () => {
    expect(sheet(true)).toContain('disabled');
    expect(sheet()).not.toContain('disabled');
  });

  it('검색 화면 진입 직후에는 시트가 없다 — 화면을 덮는 것은 사용자가 부른 뒤에만(T-183)', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSearch busy={false} error={null} onAdd={() => {}} onFail={() => {}} onBack={() => {}} />
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
 * 「책 추가하기」 + 첫 방문 코치마크 — 게이트와 안내가 <b>한 몸</b>이다.
 *
 * <p>버튼은 서버 플래그(`searchEnabled`)에 달려 있어, 안내를 따로 두면 플래그가 꺼진 날
 * 아무것도 없는 자리를 가리키는 안내만 남는다. 그래서 같은 컴포넌트가 둘을 함께 든다.
 */
describe('책 추가 손잡이', () => {
  const GUIDE = '읽을 책은 여기서 찾아 담아요';

  const addBook = (enabled: boolean) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <AddBookButton enabled={enabled} onPress={() => {}} />
      </TDSMobileProvider>,
    );

  it('바로 앞 걸음(서재 설명)을 본 뒤에 버튼과 안내가 함께 뜬다 — 흐름이 이 화면으로 데려온 직후다', () => {
    dismissCoachmark('library');

    const markup = addBook(true);

    expect(markup).toContain('책 추가하기');
    expect(markup).toContain(GUIDE);
  });

  it('앞 걸음 전에는 안내가 뜨지 않는다 — 딤 두 장이 겹치지 않는다', () => {
    const markup = addBook(true);

    expect(markup).toContain('책 추가하기');
    expect(markup).not.toContain(GUIDE);
  });

  it('한 번 본 사람에게는 버튼만 남는다', () => {
    dismissCoachmark('library');
    dismissCoachmark('add-book');

    const markup = addBook(true);

    expect(markup).toContain('책 추가하기');
    expect(markup).not.toContain(GUIDE);
  });

  it('검색이 꺼져 있으면 버튼도 안내도 없다 — 없는 버튼을 가리키지 않는다', () => {
    dismissCoachmark('library');

    const markup = addBook(false);

    expect(markup).not.toContain('책 추가하기');
    expect(markup).not.toContain(GUIDE);
  });
});
