import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { MyBookSummary } from './api';
import { Shelf } from './screens/Library';
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

function shelf(books: MyBookSummary[]) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Shelf books={books} busy={false} onAction={() => {}} openId={null} onOpen={() => {}} />
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
