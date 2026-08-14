import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { BookStatus, MyBookSummary } from './api';
import type { LibrarySheet } from './screens/Library';
import { BookSearch, Shelf, metaLine, resolveSelected } from './screens/Library';
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
  { tab = 'READING' as BookStatus, selectedId = null as number | null, sheet = null as LibrarySheet } = {},
) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Shelf
        books={books}
        tab={tab}
        selectedId={selectedId}
        sheet={sheet}
        busy={false}
        onTab={() => {}}
        onSelect={() => {}}
        onSheet={() => {}}
        onAction={() => {}}
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
 * 관리 시트 — 액션 넷을 상시 노출하지 않고 「관리」 뒤로 접었다. 정적 렌더라 탭해서 열 수 없으므로
 * 열린 상태를 프롭으로 받아 계측한다(홈 `BookSheet`와 같은 처지).
 */
describe('관리 시트', () => {
  const books = [book(1, '데미안', 'READING', { isPublic: true })];
  const actions: LibrarySheet = { kind: 'actions', confirmDelete: false };

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

  it('확인 단계에서는 삭제와 취소만 남는다', () => {
    const markup = shelf(books, { selectedId: 1, sheet: { kind: 'actions', confirmDelete: true } });

    expect(markup).toContain('정말 삭제');
    expect(markup).toContain('취소');
    expect(markup).not.toContain('옮기기');
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
