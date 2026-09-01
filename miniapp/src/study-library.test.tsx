import { TDSMobileProvider } from '@toss/tds-mobile';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

// 렌더로 관측 불가능한 배선(App의 모드 분기)은 소스로 잰다 — `timer-toast.test.tsx`와 같은 방식.
import appSource from './App.tsx?raw';

import type { SearchRow, ShelfResponse, StudyBookRow, StudyShelfResponse } from './api';
import { mockRequest } from './dev-mock';
import {
  StudyActionSheet,
  StudyShelf,
  canDecrement,
  readCountLabel,
  studyOwned,
} from './screens/StudyLibrary';
import { userAgent } from './test-fixtures';

/**
 * 공부 서재 — 회독 수가 유일한 분류 축인 별도 화면(설계 2026-09-01).
 *
 * <p>하니스가 정적 렌더라 클릭·effect가 안 돈다(T-149) — 그래서 판정은 <b>순수 함수</b>로 꺼내 재고,
 * 「그 판정이 어떻게 보이나」만 마크업으로 잰다. 실제 탭 왕복은 목 모드 실브라우저가 게이트다.
 */

const studyBook = (id: number, title: string, readCount: number, extra: Partial<StudyBookRow> = {}): StudyBookRow => ({
  id,
  title,
  author: '지은이',
  coverUrl: null,
  isbn13: `978000000000${id}`,
  readCount,
  purchaseLink: null,
  ...extra,
});

const searchRow = (title: string, isbn13: string | null): SearchRow => ({
  title,
  author: '지은이',
  isbn13,
  coverUrl: null,
  publisher: null,
  purchaseLink: null,
  category: null,
  pubDate: null,
  owned: false,
});

function render(node: ReactNode): string {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

describe('readCountLabel — 0독도 정보다', () => {
  it('아직 한 번도 안 돈 책은 「0독」이라고 말한다 — 빈칸으로 두면 「모른다」로 읽힌다', () => {
    expect(readCountLabel(0)).toBe('0독');
  });

  it('돈 횟수를 그대로 센다', () => {
    expect(readCountLabel(1)).toBe('1독');
    expect(readCountLabel(3)).toBe('3독');
  });
});

describe('studyOwned — 담김 판정은 공부 서재의 isbn 집합으로 다시 센다', () => {
  it('서버가 준 owned(독서 책장 기준)와 무관하게 공부 서재 집합으로 판정한다', () => {
    const myIsbns = new Set(['9788996991342']);

    expect(studyOwned(myIsbns, searchRow('미움받을 용기', '9788996991342'))).toBe(true);
    // 독서 책장에 있다고 서버가 true를 줘도 공부 서재엔 없으면 담을 수 있어야 한다.
    expect(studyOwned(myIsbns, { ...searchRow('사피엔스', '9788934972464'), owned: true })).toBe(false);
  });

  it('isbn이 없는 책은 담김이 아니다 — null-state를 「있다」로 뭉개면 담기 자체가 막힌다(N-055)', () => {
    // 집합에 null이 섞여 들어와도 판정이 흔들리지 않아야 한다.
    expect(studyOwned(new Set(['9788996991342']), searchRow('제목만 있는 책', null))).toBe(false);
    expect(studyOwned(new Set(), searchRow('제목만 있는 책', null))).toBe(false);
  });
});

describe('canDecrement — 눌러도 안 되는 죽은 행을 안 그리기 위한 판정', () => {
  it('0독이면 되돌릴 것이 없다', () => {
    expect(canDecrement(0)).toBe(false);
  });

  it('1독부터는 되돌릴 수 있다', () => {
    expect(canDecrement(1)).toBe(true);
    expect(canDecrement(3)).toBe(true);
  });
});

describe('StudyShelf — 표지 아래 「N독」 칩', () => {
  const books = [studyBook(1, '기본서', 3), studyBook(2, '기출문제집', 0)];

  const shelf = (selectedId: number | null | undefined, rows = books) =>
    render(
      <StudyShelf
        books={rows}
        selectedId={selectedId}
        sheet={null}
        busy={false}
        searchEnabled
        onSelect={() => {}}
        onSheet={() => {}}
        onReadCount={() => {}}
        onDelete={() => {}}
        onAddBook={() => {}}
      />,
    );

  /*
   * 칩은 숫자만 세리프로 그린다(캐러셀의 `value` 규약 — 「3」이 값이고 「독」은 그게 무엇인지 말하는
   * 꼬리다). 그래서 마크업에서 둘 사이에 태그가 끼어 `'3독'`이 통째로 나오지 않는다 — 이어진 두 조각으로 잡는다.
   */
  it('고른 책의 회독 수를 칩으로 말한다', () => {
    expect(shelf(1)).toMatch(/3<\/span>독/);
  });

  it('0독 책도 칩이 선다 — 「아직 안 돌았다」가 이 화면의 출발 상태다', () => {
    expect(shelf(2)).toMatch(/0<\/span>독/);
  });

  it('책이 있으면 주 손잡이가 「회독 +1」이다 — 이 화면의 유일한 핵심 동작', () => {
    expect(shelf(1)).toContain('회독 +1');
    expect(shelf(1)).toContain('관리');
  });

  it('빈 서재는 「책 추가」 칸으로 시작한다 — 회독을 올릴 책이 없으니 손잡이도 갈린다', () => {
    const markup = shelf(undefined, []);

    expect(markup).toContain('책 추가');
    expect(markup).toContain('검색해서 담기');
    // 대상이 없는데 「회독 +1」이 서 있으면 눌러도 아무 일이 없는 죽은 손잡이다.
    expect(markup).not.toContain('회독 +1');
  });

  it('상태 탭이 없다 — 공부 책의 분류 축은 상태가 아니라 회독 수다', () => {
    const markup = shelf(1);

    expect(markup).not.toContain('읽는 중');
    expect(markup).not.toContain('읽고 싶어요');
  });
});

describe('StudyActionSheet — 관리 시트', () => {
  const sheet = (book: StudyBookRow, confirmDelete = false) =>
    render(
      <StudyActionSheet
        book={book}
        busy={false}
        confirmDelete={confirmDelete}
        onConfirmDelete={() => {}}
        onReadCount={() => {}}
        onDelete={() => {}}
        onClose={() => {}}
      />,
    );

  it('1독 이상이면 「회독 -1」 행을 그린다', () => {
    expect(sheet(studyBook(1, '기본서', 2))).toContain('회독 -1');
  });

  it('0독이면 「회독 -1」 행 자체가 없다 — 눌러도 안 되는 죽은 행을 안 남긴다', () => {
    expect(sheet(studyBook(1, '기본서', 0))).not.toContain('회독 -1');
  });

  it('구매 링크가 있으면 구매 행과 제휴 고지가 함께 선다 — 둘은 한 몸이다', () => {
    const markup = sheet(studyBook(1, '기본서', 1, { purchaseLink: 'https://www.aladin.co.kr/shop/x' }));

    expect(markup).toContain('알라딘에서 구매');
    expect(markup).toContain('제휴 링크예요');
  });

  it('구매 링크가 없으면 구매 행도 고지도 없다 — 살 곳 없이 수수료 고지만 남는 것도 사고다', () => {
    const markup = sheet(studyBook(1, '기본서', 1));

    expect(markup).not.toContain('알라딘에서 구매');
    expect(markup).not.toContain('제휴 링크예요');
  });

  it('삭제는 확인 한 단계를 거친다 — 확인 중엔 다른 길을 감춘다', () => {
    const markup = sheet(studyBook(1, '기본서', 2), true);

    expect(markup).toContain('정말 삭제');
    expect(markup).toContain('취소');
    // 확인 문구 옆에 다른 손잡이가 남아 있으면 무엇을 확정하는지 흐려진다(서재 관리 시트와 같은 규율).
    expect(markup).not.toContain('회독 -1');
  });
});

/**
 * App 배선 — 공부 모드의 서재 탭이 <b>다른 화면</b>으로 갈린다.
 *
 * <p>정적 렌더로는 못 잰다: 두 화면 모두 같은 자리에 마크업을 내므로 분기 자체가 관측되지 않는다.
 * 그래서 소스로 잠근다 — 분기가 사라지면 공부 모드에서 독서 서재가 뜨는데, 그건 이 기능의 요구
 * 그 자체(두 서재가 안 섞인다)가 깨진 것이다.
 */
describe('App 모드 분기', () => {
  it('서재 탭이 공부 모드에서 StudyLibrary로 갈린다', () => {
    expect(appSource).toMatch(/tab === 'library' &&[\s\S]{0,200}mode === 'study'/);
    expect(appSource).toContain('<StudyLibrary');
  });

  it('StudyLibrary를 실제로 import 한다 — 없으면 위 분기는 컴파일도 안 된다', () => {
    expect(appSource).toMatch(/import \{ StudyLibrary \} from '\.\/screens\/StudyLibrary'/);
  });
});

/**
 * 목 라우트 왕복 — 서버 계약(멱등·400·404)을 목이 흉내내야 브라우저로 그 경로를 밟을 수 있다.
 * 모듈 메모리 상태를 건드리므로 각 케이스가 자기 책을 만들어 쓴다.
 */
describe('dev-mock 공부 서재', () => {
  it('픽스처에 회독 수가 섞여 있다 — 0·1·3이 한 화면에 있어야 칩 세 꼴을 눈으로 견준다', async () => {
    const shelf = await mockRequest<StudyShelfResponse>('/api/study/books', {});

    expect(shelf.searchEnabled).toBe(true);
    expect(shelf.books.map((b) => b.readCount).sort()).toEqual([0, 1, 3]);
  });

  it('추가 → 목록 — 0독으로 실린다', async () => {
    const added = await mockRequest<StudyBookRow>('/api/study/books', {
      body: { title: '정보처리기사 실기', author: '테스터', isbn13: '9791122223333', coverUrl: null,
        publisher: null, purchaseLink: null },
    });
    expect(added.readCount).toBe(0);

    const shelf = await mockRequest<StudyShelfResponse>('/api/study/books', {});
    expect(shelf.books.some((b) => b.id === added.id && b.title === '정보처리기사 실기')).toBe(true);

    await mockRequest(`/api/study/books/${added.id}/delete`, { body: {} });
    const after = await mockRequest<StudyShelfResponse>('/api/study/books', {});
    expect(after.books.some((b) => b.id === added.id)).toBe(false);
  });

  it('같은 isbn 재추가는 기존 행을 준다 — 「추가」가 회독을 리셋하지 않는다(서버 멱등 계약)', async () => {
    const first = await mockRequest<StudyBookRow>('/api/study/books', {
      body: { title: '민법총칙', author: '테스터', isbn13: '9791144445555', coverUrl: null,
        publisher: null, purchaseLink: null },
    });
    await mockRequest(`/api/study/books/${first.id}/read-count`, { body: { readCount: 2 } });

    const again = await mockRequest<StudyBookRow>('/api/study/books', {
      body: { title: '민법총칙', author: '테스터', isbn13: '9791144445555', coverUrl: null,
        publisher: null, purchaseLink: null },
    });

    expect(again.id).toBe(first.id);
    expect(again.readCount).toBe(2);
  });

  it('회독 수는 절대값으로 설정된다 — 음수는 서버처럼 400, 남의 책은 404', async () => {
    const shelf = await mockRequest<StudyShelfResponse>('/api/study/books', {});
    const target = shelf.books[0];
    // ⚠️ 목은 <b>살아 있는 객체</b>를 돌려준다(실물은 JSON 파싱이라 사본이다) — 기대값을 미리 숫자로
    //    떠 놓지 않으면 뮤테이션이 기대값까지 밀어 올려 테스트가 스스로를 쫓는다(실측 실패).
    const next = target.readCount + 1;

    const updated = await mockRequest<StudyBookRow>(`/api/study/books/${target.id}/read-count`, {
      body: { readCount: next },
    });
    expect(updated.readCount).toBe(next);

    // 같은 값 재설정은 멱등이다(연타·재시도에 안전하다는 계약).
    const again = await mockRequest<StudyBookRow>(`/api/study/books/${target.id}/read-count`, {
      body: { readCount: next },
    });
    expect(again.readCount).toBe(next);

    await expect(
      mockRequest(`/api/study/books/${target.id}/read-count`, { body: { readCount: -1 } }),
    ).rejects.toMatchObject({ status: 400 });

    await expect(
      mockRequest('/api/study/books/999999/read-count', { body: { readCount: 1 } }),
    ).rejects.toMatchObject({ status: 404 });
  });

  it('공부 책은 독서 서재에 안 섞인다 — 요구 그 자체를 목에서도 지킨다', async () => {
    const added = await mockRequest<StudyBookRow>('/api/study/books', {
      body: { title: '섞이면 안 되는 책', author: '테스터', isbn13: '9791166667777', coverUrl: null,
        publisher: null, purchaseLink: null },
    });

    const reading = await mockRequest<ShelfResponse>('/api/books', {});
    expect(reading.books.some((b) => b.title === '섞이면 안 되는 책')).toBe(false);

    await mockRequest(`/api/study/books/${added.id}/delete`, { body: {} });
  });
});
