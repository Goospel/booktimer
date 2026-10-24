jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { ApiError } from '../api/client';
import type { SearchRow } from '../api/types';
import { defaultStudyBook, REMOVE_FAILED, removeStudyBook, studyAddBody, studyMeta, studyOwned } from './studyShelf';

// v1 REQ-99 · REQ-148 · REQ-150(PR-S1 사양 §5.2 · §5.6 · §9)

const NOTE_409 = '필기 2장이 있는 책이에요. 필기를 먼저 지운 뒤 서재에서 빼 주세요.';

test('REQ-99 · 공부 서재 지우기의 409 문구를 그대로 보인다', async () => {
  const reload = jest.fn(async () => {});
  const plain = jest.fn(async () => {
    throw new ApiError(409, NOTE_409);
  });
  expect(await removeStudyBook({ remove: plain, reload }, 3)).toEqual({ error: NOTE_409 });
  expect(plain).toHaveBeenCalledWith(3);
  expect(reload).not.toHaveBeenCalled();
  // 대조 — HTML 오류 화면(문구 없는 실패)이면 정해 둔 문구
  const html = async () => {
    throw new ApiError(409, '요청에 실패했어요 (409)');
  };
  expect(await removeStudyBook({ remove: html, reload }, 3)).toEqual({ error: REMOVE_FAILED });
  expect(REMOVE_FAILED).toBe('서재에서 빼지 못했어요 — 잠시 뒤 다시 해 주세요');
});

test('REQ-99 · 빼기가 되면 다시 읽고 알림이 없다', async () => {
  const reload = jest.fn(async () => {});
  expect(await removeStudyBook({ remove: async () => ({ deleted: true }), reload }, 3)).toEqual({});
  expect(reload).toHaveBeenCalledTimes(1);
});

test('REQ-99 · 행 메타는 1분 이상일 때만 「{시간} 공부」다', () => {
  expect(studyMeta(0)).toBeUndefined();
  expect(studyMeta(59)).toBeUndefined();
  expect(studyMeta(3720)).toBe('1시간 2분 공부');
});

test('REQ-148 · 지금 책은 고른 책 → 최근 공부한 책 → 첫 책 순서이고 없는 id는 건너뛴다', () => {
  const books = [{ id: 1 }, { id: 2 }, { id: 3 }];
  expect(defaultStudyBook(books, 3, 2)?.id).toBe(2);
  expect(defaultStudyBook(books, 3, null)?.id).toBe(3);
  expect(defaultStudyBook(books, 3, 99)?.id).toBe(3); // 고른 책이 다른 곳에서 빠짐
  expect(defaultStudyBook(books, 99, 98)?.id).toBe(1);
  expect(defaultStudyBook(books, null, null)?.id).toBe(1);
  expect(defaultStudyBook([], 3, 2)).toBeNull();
});

const row = (p: Partial<SearchRow>): SearchRow => ({
  title: '혼자 공부하는 머신러닝+딥러닝',
  author: '박해선 (지은이)',
  authorShort: '박해선',
  isbn13: '9791162243664',
  coverUrl: 'https://image.aladin.co.kr/x.jpg',
  publisher: '한빛미디어',
  purchaseLink: 'https://www.aladin.co.kr/x',
  category: '컴퓨터',
  pubDate: '2020-12-21',
  owned: false,
  ...p,
});

test('REQ-150 · 「담음」은 공부 서재 isbn으로 다시 센다(검색 응답의 owned는 독서 서재 기준이다)', () => {
  // 두 출처를 다른 값으로 둔다 — 같으면 어느 쪽을 읽었는지 가리지 못한다
  const rows = [row({ isbn13: '9790000000001', owned: true }), row({ isbn13: '9790000000002', owned: false }), row({ isbn13: null, owned: true })];
  const isbns = new Set(['9790000000002']);
  expect(studyOwned(rows, isbns).map((r) => r.owned)).toEqual([false, true, false]);
  expect(studyOwned(rows, null).map((r) => r.owned)).toEqual([false, false, false]);
});

test('REQ-150 · 공부 책 담기 본문은 author 원문을 싣고 독서 서재 필드가 없다', () => {
  const r = row({ author: '사이토 고키 (지은이), 개앞맵시 (옮긴이)', authorShort: '사이토 고키 외 1명' });
  const b = studyAddBody(r);
  expect(Object.keys(b).sort()).toEqual(['author', 'coverUrl', 'isbn13', 'publisher', 'purchaseLink', 'title']);
  expect(b.author).toBe('사이토 고키 (지은이), 개앞맵시 (옮긴이)');
  expect(b.author).not.toBe(r.authorShort);
});
