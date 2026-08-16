import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { BookStatus, MyBookSummary } from './api';
import type { LibrarySheet } from './screens/Library';
import { BookGrid, BookSearch, Shelf, metaLine, needsPublishConfirm, resolveSelected } from './screens/Library';
import { userAgent } from './test-fixtures';

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
 * 서재의 여백 문 — 서재는 "내가 뭘 남겼더라"를 들춰보는 자리라 작성 직행이 아니라 여백 화면으로 간다
 * (홈 문은 반대로 작성 직행이다). 핸들이 없으면 서버가 여백 대상을 찾지 못하므로 그리지 않는다.
 */
describe('서재 여백 손잡이', () => {
  it('고른 책 옆에 「관리」와 나란히 선다', () => {
    const markup = shelf([book(1, '데미안', 'READING')], { selectedId: 1 });

    expect(markup).toContain('여백');
    expect(markup).toContain('관리');
  });

  it('비공개 책에도 선다 — 비공개 책의 여백은 나만 보는 메모다(결정 2)', () => {
    expect(shelf([book(1, '메모책', 'READING', { isPublic: false })], { selectedId: 1 })).toContain('여백');
  });

  it('핸들이 없으면 손잡이가 없다 — 「관리」는 그대로 남는다', () => {
    const markup = shelf([book(1, '데미안', 'READING')], { selectedId: 1, myLoginId: null });

    expect(markup).not.toContain('여백');
    expect(markup).toContain('관리');
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
