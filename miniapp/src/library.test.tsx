import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { MyBookSummary } from './api';
import type { OpenRow } from './screens/Library';
import { Shelf, toggleOpen } from './screens/Library';
import { userAgent } from './test-fixtures';

/**
 * 서재 목록 렌더 — 섹션 분류(읽는 중/다 읽음/읽고 싶어요)와 빈 서재 안내가 계측 대상이다.
 * 액션(상태 변경·공개 토글·삭제)은 api 계층에서 계측하고, 여기서는 배선의 정적 결과만 본다.
 */
function book(
  id: number,
  title: string,
  status: MyBookSummary['status'],
  coverUrl: string | null = null,
): MyBookSummary {
  return {
    id,
    title,
    author: '저자',
    coverUrl,
    isbn13: null,
    status,
    statusLabel: status,
    visibility: 'PRIVATE',
    visibilityLabel: '비공개',
    isPublic: false,
    seconds: 0,
    purchaseLink: null,
  };
}

function shelf(books: MyBookSummary[], open: OpenRow | null = null) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Shelf books={books} busy={false} onAction={() => {}} open={open} onOpen={() => {}} />
    </TDSMobileProvider>,
  );
}

describe('서재 목록', () => {
  it('상태별 섹션으로 나눠 그린다', () => {
    const markup = shelf([
      book(1, '읽는책', 'READING'),
      book(2, '끝낸책', 'FINISHED'),
      book(3, '살책', 'WANT_TO_READ'),
    ]);

    expect(markup).toContain('읽는 중');
    expect(markup).toContain('다 읽음');
    expect(markup).toContain('읽고 싶어요');
    expect(markup.indexOf('읽는책')).toBeLessThan(markup.indexOf('끝낸책'));
    expect(markup.indexOf('끝낸책')).toBeLessThan(markup.indexOf('살책'));
  });

  it('비어 있는 섹션은 제목도 그리지 않는다 — 빈 헤더만 남는 걸 막는다', () => {
    const markup = shelf([book(1, '읽는책', 'READING')]);

    expect(markup).toContain('읽는 중');
    expect(markup).not.toContain('다 읽음');
    expect(markup).not.toContain('읽고 싶어요');
  });

  it('책이 하나도 없으면 추가를 유도한다', () => {
    expect(shelf([])).toContain('아직 책이 없어요');
  });
});

describe('책 한 줄', () => {
  it('표지가 있으면 썸네일을 그린다 — 표지가 0개라 목록이 글자만 남아 있었다', () => {
    const markup = shelf([book(1, '데미안', 'READING', 'https://img/demian.jpg')]);

    expect(markup).toContain('src="https://img/demian.jpg"');
    expect(markup).toContain('loading="lazy"');
  });

  it('표지가 없으면 자리 채움 상자로 대신한다 — 줄 높이가 책마다 들쭉날쭉해지지 않게', () => {
    const markup = shelf([book(1, '데미안', 'READING')]);

    expect(markup).not.toContain('<img');
    expect(markup).toContain('📚');
  });

  it('제목과 메타를 각자 다른 블록에 둔다 — 짧은 제목이면 "데미안저자"처럼 붙어 보였다', () => {
    const markup = shelf([book(1, '데미안', 'READING')]);
    const between = markup.slice(markup.indexOf('데미안') + 3, markup.indexOf('저자'));

    expect(between).toContain('</div>');
  });
});

/**
 * 삭제 확인 — 「삭제」를 한 번 더 눌러야 지워진다. 확인 상태는 **열린 행에 매여** 있어야 한다.
 *
 * <p>예전엔 확인이 행 안의 독립 state라 열림과 수명이 어긋났다: 확인을 띄운 채 다른 행이나 액션을
 * 건드려도 그 확인이 살아남아, 그 행을 다시 열면 「정말 삭제」가 곧바로 노출됐다(오삭제 한 탭 거리).
 * 정적 렌더 하니스라 클릭을 못 잡으므로 전이는 {@link toggleOpen}으로, 표시는 마크업으로 계측한다.
 */
describe('삭제 확인', () => {
  const books = [book(1, '데미안', 'READING'), book(2, '노인과 바다', 'READING')];

  it('확인 중인 행에만 「정말 삭제」가 뜬다', () => {
    expect(shelf(books, { id: 1, confirmDelete: true })).toContain('정말 삭제');
  });

  it('열려 있어도 확인 전이면 「삭제」만 있다', () => {
    const markup = shelf(books, { id: 1, confirmDelete: false });

    expect(markup).toContain('삭제');
    expect(markup).not.toContain('정말 삭제');
  });
});

describe('행 여닫기 (toggleOpen)', () => {
  it('접힌 상태에서 누르면 그 행이 열린다 — 확인은 풀린 채로 시작', () => {
    expect(toggleOpen(null, 1)).toEqual({ id: 1, confirmDelete: false });
  });

  it('열린 행을 다시 누르면 접힌다', () => {
    expect(toggleOpen({ id: 1, confirmDelete: true }, 1)).toBeNull();
  });

  it('다른 행으로 옮기면 이전 확인이 따라가지 않는다 — 이게 오삭제를 막는 불변식이다', () => {
    expect(toggleOpen({ id: 1, confirmDelete: true }, 2)).toEqual({ id: 2, confirmDelete: false });
  });
});
