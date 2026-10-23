// 서재 세 단(REQ-121 · 122 — PR-G 사양 §5.1). 낱말은 앱 것이고 서버 statusLabel이 아니다.
import { bookMeta, SECTIONS, shelfSections } from './shelf';

type B = { id: number; status: 'READING' | 'FINISHED' | 'WANT_TO_READ'; statusLabel?: string };

test('REQ-121 · 세 단은 언제나 셋이고 읽는 중 → 다 읽음 → 읽고 싶어요 순서다(빈 단도 남는다)', () => {
  const s = shelfSections<B>([{ id: 1, status: 'FINISHED' }]);
  expect(s.map((x) => x.status)).toEqual(['READING', 'FINISHED', 'WANT_TO_READ']);
  expect(s.map((x) => x.books.length)).toEqual([0, 1, 0]);
  expect(shelfSections<B>([])).toHaveLength(3);
  expect(s[0].empty).toBe('읽는 중인 책이 없어요');
});

test('REQ-121 · 단 이름은 앱 낱말이다(서버 statusLabel 「완독」 · 「읽고 싶음」이 아니다)', () => {
  const s = shelfSections<B>([
    { id: 1, status: 'FINISHED', statusLabel: '완독' },
    { id: 2, status: 'WANT_TO_READ', statusLabel: '읽고 싶음' },
  ]);
  expect(s.map((x) => x.title)).toEqual(['읽는 중', '다 읽음', '읽고 싶어요']);
  expect(SECTIONS.map((x) => x.title).join()).not.toMatch(/완독|읽고 싶음/);
});

test('REQ-121 · 단 안의 책은 서버가 준 순서 그대로다', () => {
  const s = shelfSections<B>([
    { id: 3, status: 'READING' },
    { id: 1, status: 'FINISHED' },
    { id: 9, status: 'READING' },
    { id: 2, status: 'READING' },
  ]);
  expect(s[0].books.map((b) => b.id)).toEqual([3, 9, 2]);
});

test('REQ-122 · 메타는 읽은 시간과 여백 수를 「 · 」로 잇고 0인 조각은 뺀다', () => {
  expect(bookMeta({ seconds: 3 * 3600 + 12 * 60, storyCount: 4 })).toBe('3시간 12분 읽음 · 여백 4');
  expect(bookMeta({ seconds: 4 * 3600 + 5 * 60, storyCount: 0 })).toBe('4시간 5분 읽음');
  expect(bookMeta({ seconds: 0, storyCount: 2 })).toBe('여백 2');
  // 1분 아래는 읽은 시간 「0분」이 아니라 조각이 없다
  expect(bookMeta({ seconds: 59, storyCount: 1 })).toBe('여백 1');
});

test('REQ-122 · 둘 다 0이면 메타가 없다', () => {
  expect(bookMeta({ seconds: 0, storyCount: 0 })).toBeUndefined();
});
