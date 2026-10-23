// 담기 본문(REQ-126 — PR-G 사양 §5.3 · §11-2). author는 원문 — authorShort를 보내면 「… 외 32명」이 영구 저장된다.
import type { SearchRow } from '../api/types';
import { addBody } from './bookAdd';

// 두 값을 일부러 다르게 둔다 — 같으면 어느 쪽을 읽었는지 묻지 못한다
const row: SearchRow = {
  title: '돈키호테',
  author: '미겔 데 세르반테스 사아베드라 (지은이), 안영옥 (옮긴이)',
  authorShort: '미겔 데 세르반테스 사아베드라 외 1명',
  isbn13: '9788937462818',
  coverUrl: 'https://image.aladin.co.kr/x.jpg',
  publisher: '열린책들',
  purchaseLink: 'https://www.aladin.co.kr/x',
  category: '소설',
  pubDate: '2014-11-25',
  owned: false,
};

test('REQ-126 · 담기 본문은 검색 행의 author 원문을 싣는다(authorShort가 아니다)', () => {
  const b = addBody(row);
  expect(b.author).toBe(row.author);
  expect(b.author).not.toBe(row.authorShort);
  expect(b).not.toHaveProperty('authorShort');
  expect(b).not.toHaveProperty('owned');
  expect(b.isbn13).toBe('9788937462818');
});

test('REQ-126 · 담기 상태 기본은 READING이다', () => {
  expect(addBody(row).status).toBe('READING');
  expect(addBody(row, 'WANT_TO_READ').status).toBe('WANT_TO_READ');
});
