// 홈 판정(책생글 A2 — F1 §4.1 · §4.6 · REQ-226 · 270 · 273). 목록마다 다른 id · 제목을 둬 「어느 목록을 읽었나」를 가른다.
import type { BlogPostRow, BookOption, Dashboard } from '../api/types';
import { blogSection, homeBox } from './home';
import { noteHref } from './marginSave';

const book = (id: number, title: string, coverUrl: string | null = null): BookOption => ({ id, title, coverUrl, author: null });
const reading = [book(1, '데미안', 'https://img/1')];
const want = [book(2, '싯다르타')];
const finished = [book(3, '수레바퀴 아래서', 'https://img/3')];

function dash(p: Partial<Dashboard> = {}): Dashboard {
  return {
    todayGoalSeconds: 1800,
    todayReadSeconds: 0,
    hasActiveSession: false,
    activeStartedAt: null,
    activeBookTitle: null,
    activeBook: null,
    readingBooks: reading,
    wantToReadBooks: want,
    finishedBooks: finished,
    recentBookId: null,
    graph: { goalStreak: 0 },
    study: { hasActiveSession: false, activeStartedAt: null, todaySeconds: 0, activeBook: null, recentBookId: null, books: [] },
    ...p,
  } as Dashboard;
}

test('REQ-270 · 박스의 책(측정 전) — 최근에 잰 책(다 읽음 · 읽고 싶어요도) · 서재에 없으면 읽는 중 첫 책 · 읽는 중이 없으면 읽고 싶어요 · 그것도 없으면 다 읽음 첫 책 · 세 목록이 다 비면 null(빈 홈)', () => {
  expect(homeBox(dash({ recentBookId: 3 }), false)).toEqual({ bookId: 3, title: '수레바퀴 아래서', coverUrl: 'https://img/3', cover: true });
  expect(homeBox(dash({ recentBookId: 2 }), false)?.bookId).toBe(2);
  expect(homeBox(dash({ recentBookId: 99 }), false)).toEqual({ bookId: 1, title: '데미안', coverUrl: 'https://img/1', cover: true });
  expect(homeBox(dash({ readingBooks: [] }), false)?.bookId).toBe(2);
  expect(homeBox(dash({ readingBooks: [], wantToReadBooks: [] }), false)?.bookId).toBe(3);
  expect(homeBox(dash({ readingBooks: [], wantToReadBooks: [], finishedBooks: [], recentBookId: 5 }), false)).toBeNull();
  const old = dash({ readingBooks: [], recentBookId: 3 });
  delete old.finishedBooks; // 옛 꼴(finishedBooks 키 없음)
  expect(homeBox(old, false)?.bookId).toBe(2);
});

test('REQ-270 · 측정 중 박스 — 재는 책(activeBook) · 서재에 없는 책이면 원장 제목만(천 표지 · 책 칸 null) · 책 없이 재면 표지 없이 「읽는 중」', () => {
  const on = { hasActiveSession: true, activeStartedAt: '2026-11-12T10:00:00Z' };
  expect(homeBox(dash({ ...on, activeBook: want[0], activeBookTitle: '싯다르타', recentBookId: 1 }), true)).toEqual({ bookId: 2, title: '싯다르타', coverUrl: null, cover: true });
  expect(homeBox(dash({ ...on, activeBook: null, activeBookTitle: '지운 책' }), true)).toEqual({ bookId: null, title: '지운 책', coverUrl: null, cover: true });
  expect(homeBox(dash({ ...on, activeBook: null, activeBookTitle: null, readingBooks: [], wantToReadBooks: [], finishedBooks: [] }), true)).toEqual({ bookId: null, title: '읽는 중', coverUrl: null, cover: false });
});

test('REQ-226 · 박스 책 없으면 margin/none — 빈 홈 · 책 없이 재는 중 · 서재에 없는 책 → /margin/none, 박스 책 1 → /margin/1', () => {
  const href = (d: Dashboard, measuring: boolean) => noteHref(homeBox(d, measuring)?.bookId ?? null);
  const on = { hasActiveSession: true, activeStartedAt: '2026-11-12T10:00:00Z' };
  expect(href(dash({ readingBooks: [], wantToReadBooks: [], finishedBooks: [] }), false)).toBe('/margin/none');
  expect(href(dash({ ...on }), true)).toBe('/margin/none');
  expect(href(dash({ ...on, activeBookTitle: '지운 책' }), true)).toBe('/margin/none');
  expect(href(dash({ recentBookId: 1 }), false)).toBe('/margin/1');
});

const post = (id: number, title: string, authorName: string, publishedAt: string): BlogPostRow => ({ id, title, authorName, publishedAt });

test('REQ-273 · 3편만 — 넷이 와도 셋 · 줄 = 제목 + 「이름 · 날짜」', () => {
  const s = blogSection([
    post(14, '여름의 데미안', '서윤', new Date(2026, 9, 29, 12).toISOString()),
    post(13, '두 번 읽은 책', '하람', new Date(2026, 9, 28, 9).toISOString()),
    post(12, '느린 독서', '도윤', new Date(2026, 8, 3, 18).toISOString()),
    post(11, '네 번째', '지우', new Date(2026, 8, 1, 18).toISOString()),
  ]);
  expect(s.rows).toEqual([
    { id: 14, title: '여름의 데미안', meta: '서윤 · 10월 29일' },
    { id: 13, title: '두 번 읽은 책', meta: '하람 · 10월 28일' },
    { id: 12, title: '느린 독서', meta: '도윤 · 9월 3일' },
  ]);
  expect(s.empty).toBeNull();
});

test('REQ-273 · 0편이면 「아직 올라온 글이 없어요」 · 못 읽었으면(null) 줄도 빈 문구도 없음', () => {
  expect(blogSection([])).toEqual({ rows: [], empty: '아직 올라온 글이 없어요' });
  expect(blogSection(null)).toEqual({ rows: [], empty: null });
});
