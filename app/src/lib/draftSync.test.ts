// 기기 초안 · 자동저장(v1 REQ-38 · PR-H 사양 REQ-141 · 142). KV는 AsyncStorage 꼴의 Map 가짜.
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
}));

import { ApiError, NetworkError } from '../api/client';
import type { CreateMargin, DraftView, SaveDraft } from '../api/types';
import { adoptNewNote, clearAll, draftSync, type KV, newNoteKey, pendingAll, sendNewNotes, shouldDiscard, slotKey, startNewNote } from './draftSync';

function memoryKV(): KV & { map: Map<string, string> } {
  const map = new Map<string, string>();
  return {
    map,
    getItem: async (k) => map.get(k) ?? null,
    setItem: async (k, v) => void map.set(k, v),
    removeItem: async (k) => void map.delete(k),
    getAllKeys: async () => [...map.keys()],
  };
}

const server = (p: Partial<DraftView> = {}): DraftView => ({
  id: 7, bookId: 1, bookTitle: '데미안', title: '서버 제목', body: '서버 본문', revision: 3,
  questions: { rounds: [] }, aiUpdatedAt: null, aiRemainingToday: 20, aiConsent: true, ...p,
});

function setup(userId: number, put: (id: number, b: SaveDraft) => Promise<DraftView>, kv = memoryKV()) {
  const calls: SaveDraft[] = [];
  const sync = draftSync({ kv, userId, put: async (id, b) => { calls.push(b); return put(id, b); } });
  return { kv, calls, sync };
}

const echo = async (_id: number, b: SaveDraft) => server({ title: b.title, body: b.body, revision: b.revision + 1 });

test('REQ-38 · 서버 확인 전 본문은 그 userId 칸에 남는다', async () => {
  const { kv, sync } = setup(1, async () => { throw new NetworkError(); });
  await sync.open(server());
  await sync.edit(7, { body: '끊긴 동안 쓴 글' });
  expect(await sync.flush(7)).toEqual({ kind: 'offline' });
  const slot = JSON.parse(kv.map.get(slotKey(1, 7))!);
  expect(slot).toMatchObject({ body: '끊긴 동안 쓴 글', dirty: true, base: 3 });
  // 다시 열면 서버 글이 아니라 칸의 글
  expect((await sync.open(server())).body).toBe('끊긴 동안 쓴 글');
});

test('REQ-38 · 409면 선택 상태로 가고 로컬 본문을 버리지 않는다', async () => {
  const { kv, sync } = setup(1, async () => { throw new ApiError(409, '다른 기기에서 이 글이 바뀌었어요 — 그쪽을 불러와 주세요'); });
  await sync.open(server());
  await sync.edit(7, { body: '이 기기에서 쓴 글' });
  expect(await sync.flush(7)).toEqual({ kind: 'conflict' });
  expect(JSON.parse(kv.map.get(slotKey(1, 7))!)).toMatchObject({ body: '이 기기에서 쓴 글', dirty: true });

  // 「이 기기 것 유지」 = 서버 판으로 다시 PUT · 「그쪽 불러오기」 = 칸을 서버 글로(띠 질문은 남긴다)
  const keep = setup(1, echo, kv);
  expect(await keep.sync.keepMine(7, server({ revision: 9 }))).toEqual({ kind: 'saved', revision: 10 });
  expect(keep.calls.at(-1)).toMatchObject({ body: '이 기기에서 쓴 글', revision: 9 });
  await keep.sync.edit(7, { body: '또 고침', focus: '띠 질문인가요?' });
  const theirs = await keep.sync.takeTheirs(7, server({ body: '그쪽 글', revision: 12 }));
  expect(theirs).toEqual({ title: '서버 제목', body: '그쪽 글', focus: '띠 질문인가요?', base: 12, dirty: false });
});

test('REQ-38 · PUT 응답 본문이 보낸 것과 다르면 기기 초안을 지우지 않고 알린다', async () => {
  const { kv, sync } = setup(1, async (_id, b) => server({ body: '서버가 바꾼 본문', revision: b.revision + 1 }));
  await sync.open(server());
  await sync.edit(7, { body: '보낸 본문' });
  expect(await sync.flush(7)).toEqual({ kind: 'mismatch', revision: 4 });
  expect(JSON.parse(kv.map.get(slotKey(1, 7))!)).toMatchObject({ body: '보낸 본문', dirty: true });

  // 대조군 — 같으면 저장됨이고 칸은 깨끗해진다
  const ok = setup(1, echo);
  await ok.sync.open(server());
  await ok.sync.edit(7, { body: '보낸 본문' });
  expect(await ok.sync.flush(7)).toEqual({ kind: 'saved', revision: 4 });
  expect(JSON.parse(ok.kv.map.get(slotKey(1, 7))!)).toMatchObject({ dirty: false, base: 4 });
});

test('REQ-38 · 다른 userId로 로그인하면 앞 사람 초안을 보지도 보내지도 않는다', async () => {
  const kv = memoryKV();
  const first = setup(1, async () => { throw new NetworkError(); }, kv);
  await first.sync.open(server());
  await first.sync.edit(7, { body: '앞 사람의 비밀 글', focus: '앞 사람 질문인가요?' });
  await first.sync.flush(7);

  const second = setup(2, echo, kv);
  const opened = await second.sync.open(server());
  expect(opened.body).toBe('서버 본문');
  expect(opened.focus).toBeNull();
  await second.sync.edit(7, { title: '두 번째 사람 제목' });
  await second.sync.flush(7);
  expect(second.calls.map((c) => c.body)).toEqual(['서버 본문']);
  expect(JSON.stringify(second.calls)).not.toContain('앞 사람');
  expect(await pendingAll(kv, 2)).toBe(0);
  expect(await pendingAll(kv, 1)).toBe(1);
  await clearAll(kv, 2);
  expect(await pendingAll(kv, 1)).toBe(1); // 남의 칸은 지우지 않는다
});

test('REQ-38 · 404면 그 기기 초안을 버린다', async () => {
  const { kv, sync } = setup(1, async () => { throw new ApiError(404, '글을 찾을 수 없어요'); });
  await sync.open(server());
  await sync.edit(7, { body: '지워진 글에 쓴 것' });
  expect(await sync.flush(7)).toEqual({ kind: 'gone' });
  expect(kv.map.has(slotKey(1, 7))).toBe(false);
});

test('REQ-38 · 띠의 질문은 그 글의 칸에 남고 다른 글·다른 userId에는 보이지 않는다', async () => {
  const kv = memoryKV();
  const a = setup(1, echo, kv);
  await a.sync.open(server());
  await a.sync.edit(7, { focus: '띠에 올린 질문인가요?' });
  expect(JSON.parse(kv.map.get(slotKey(1, 7))!).dirty).toBe(false); // 띠만 바꾼 것은 저장할 글이 아니다
  expect((await a.sync.open(server())).focus).toBe('띠에 올린 질문인가요?');
  expect((await a.sync.open(server({ id: 8 }))).focus).toBeNull();
  expect((await setup(2, echo, kv).sync.open(server())).focus).toBeNull();
});

test('REQ-38 · 제목·본문 편집을 기다리지 않고 연달아 불러도 둘 다 칸에 남고 저장에 실린다', async () => {
  const { calls, sync } = setup(1, echo);
  await sync.open(server());
  // 화면은 글자마다 edit를 기다리지 않고 부른다 — 읽고 쓰기가 엇갈리면 앞의 편집이 사라진다
  void sync.edit(7, { title: '새 제목' });
  void sync.edit(7, { body: '새 본문' });
  await sync.flush(7);
  expect(calls).toEqual([{ title: '새 제목', body: '새 본문', revision: 3 }]);
});

test('REQ-37 · 내보낸 표시 PUT은 고친 것이 없어도 가고 칸의 판을 올린다(다음 저장이 409가 아니다)', async () => {
  const { calls, kv, sync } = setup(1, echo);
  await sync.open(server());
  expect(await sync.flush(7, { exported: true })).toEqual({ kind: 'saved', revision: 4 });
  expect(calls).toEqual([{ title: '서버 제목', body: '서버 본문', revision: 3, exported: true }]);
  expect(JSON.parse(kv.map.get(slotKey(1, 7))!).base).toBe(4);
  await sync.edit(7, { body: '내보낸 뒤 고침' });
  await sync.flush(7);
  expect(calls[1]).toEqual({ title: '서버 제목', body: '내보낸 뒤 고침', revision: 4 });
});

test('U-137 · 410(옮긴 글)이면 칸을 지우지 않고 서버 평문 그대로 「저장하지 못함」 갈래', async () => {
  const moved = '이 글은 여백으로 옮겨졌어요 — 앱을 닫았다가 다시 열어 주세요';
  const { kv, sync } = setup(1, async () => { throw new ApiError(410, moved); });
  await sync.open(server());
  await sync.edit(7, { body: '옮기기 전에 쓴 글' });
  expect(await sync.flush(7)).toMatchObject({ kind: 'error', message: moved });
  expect(JSON.parse(kv.map.get(slotKey(1, 7))!)).toMatchObject({ body: '옮기기 전에 쓴 글', dirty: true });
});

test('F1 §4.5 · 서버 문구 없는 저장 실패는 「쪽지를 저장하지 못했어요」(쪽지 화면이 이 칸을 쓴다)', async () => {
  const { sync } = setup(1, async () => { throw new ApiError(500, '요청에 실패했어요 (500)'); }, memoryKV());
  await sync.open(server());
  await sync.edit(7, { body: '고침' });
  expect(await sync.flush(7)).toMatchObject({ kind: 'error', message: '쪽지를 저장하지 못했어요 — 잠시 뒤 다시 해 주세요' });
});

// 고정 테스트(지금 코드에서도 초록) — 단독으로 잡는 실패: 질문을 고를 때 생각 칸이 바뀌거나 저장이 나가는 회귀(AI 글자가 생각으로 들어가는 길).
test('REQ-221 · 질문 고르기는 띠만 바꾼다 — 생각 · 옮긴 문장 칸 그대로 · 저장할 글이 아니다', async () => {
  const { calls, kv, sync } = setup(1, echo, memoryKV());
  await sync.open(server({ title: '옮긴 문장', body: '내 생각' }));
  await sync.edit(7, { focus: '그 길에서 무엇이 처음 보였나요?' });
  expect(JSON.parse(kv.map.get(slotKey(1, 7))!)).toEqual({ title: '옮긴 문장', body: '내 생각', focus: '그 길에서 무엇이 처음 보였나요?', base: 3, dirty: false });
  await sync.flush(7);
  expect(calls).toEqual([]);
});

describe('REQ-229 · 기기 칸 접두어 셋(J80)', () => {
  const dirty = JSON.stringify({ dirty: true });
  const clean = JSON.stringify({ dirty: false });

  test('REQ-229 · 접두어 인자 — margin · blog 칸은 그 접두어로 쓰고 셈도 그 접두어만', async () => {
    const kv = memoryKV();
    const m = draftSync({ kv, userId: 1, put: async () => { throw new NetworkError(); }, prefix: 'margin' });
    await m.open(server({ id: 70 }));
    await m.edit(70, { body: '쪽지' });
    expect(slotKey(1, 70, 'margin')).toBe('margin:1:70');
    expect(slotKey(1, 5, 'blog')).toBe('blog:1:5');
    expect(kv.map.has('margin:1:70')).toBe(true);
    expect(kv.map.has(slotKey(1, 70))).toBe(false);
    expect(JSON.parse(kv.map.get('margin:1:70')!)).toMatchObject({ body: '쪽지', dirty: true });
    await m.drop(70);
    expect(kv.map.size).toBe(0);
  });

  test('REQ-229 · pendingAll = draft · margin · blog dirty 칸 합(다른 userId 0)', async () => {
    const kv = memoryKV();
    kv.map.set('draft:1:7', dirty);
    kv.map.set('margin:1:70', dirty);
    kv.map.set('margin:1:new:abc', dirty);
    kv.map.set('blog:1:5', dirty);
    kv.map.set('blog:1:6', clean);
    kv.map.set('draft:10:7', dirty); // userId 10 — 「1:」 접두로 걸리면 안 된다
    kv.map.set('margin:2:70', dirty);
    kv.map.set('booktimer.other', dirty);
    expect(await pendingAll(kv, 1)).toBe(4);
    expect(await pendingAll(kv, 3)).toBe(0);
  });

  test('REQ-229 · clearAll이 그 userId의 세 접두어를 다 지운다(남의 칸 · 다른 키는 남는다)', async () => {
    const kv = memoryKV();
    for (const k of ['draft:1:7', 'margin:1:70', 'margin:1:new:abc', 'blog:1:5', 'draft:10:7', 'margin:2:70', 'booktimer.other']) kv.map.set(k, clean);
    await clearAll(kv, 1);
    expect([...kv.map.keys()].sort()).toEqual(['booktimer.other', 'draft:10:7', 'margin:2:70']);
  });

  test('REQ-229 · 같은 책 새 쪽지 둘(오프라인)이 서로 다른 칸 · 칸 안에 bookId(책 없으면 null)', async () => {
    const kv = memoryKV();
    const a = await startNewNote(kv, 1, { bookId: 3, quote: null, text: '첫째', dirty: true });
    const b = await startNewNote(kv, 1, { bookId: 3, quote: null, text: '둘째', dirty: true });
    const c = await startNewNote(kv, 1, { bookId: null, quote: null, text: '책 없음', dirty: true });
    expect(new Set([a, b, c]).size).toBe(3);
    expect(newNoteKey(1, a)).toMatch(/^margin:1:new:/);
    expect(JSON.parse(kv.map.get(newNoteKey(1, a))!)).toEqual({ bookId: 3, quote: null, text: '첫째', dirty: true });
    expect(JSON.parse(kv.map.get(newNoteKey(1, b))!).text).toBe('둘째');
    expect(JSON.parse(kv.map.get(newNoteKey(1, c))!).bookId).toBeNull();
    expect(await pendingAll(kv, 1)).toBe(3);
  });

  test('REQ-229 · 새 쪽지 칸이 첫 POST 뒤 id 칸으로 옮겨진다', async () => {
    const kv = memoryKV();
    const local = await startNewNote(kv, 1, { bookId: 3, quote: null, text: '오프라인에서 쓴 쪽지', dirty: true });
    await adoptNewNote(kv, 1, local, 70);
    expect(kv.map.has(newNoteKey(1, local))).toBe(false);
    expect(JSON.parse(kv.map.get(slotKey(1, 70, 'margin'))!)).toEqual({ bookId: 3, quote: null, text: '오프라인에서 쓴 쪽지', dirty: true });
  });

  // J100 — 앱이 꺼져 남은 새 쪽지 칸은 목록에 안 보여 다시 열 길이 없다 → 앱이 켜질 때(draftMigrate 다음) 보낸다.
  test('REQ-229 · 앱이 켜질 때 dirty 새 쪽지 칸을 보내고 지운다 · 빈 칸은 보내지 않고 지운다 · 실패(연결 · 404)면 그대로 · 다른 userId 칸은 건드리지 않는다', async () => {
    const kv = memoryKV();
    const put = (k: string, v: object) => kv.map.set(k, JSON.stringify(v));
    put(newNoteKey(1, 'sent'), { bookId: 3, quote: '옮긴 문장', text: '생각', dirty: true });
    put(newNoteKey(1, 'bookless'), { bookId: null, quote: null, text: '책 없는 생각', dirty: true });
    put(newNoteKey(1, 'empty'), { bookId: 3, quote: '  ', text: '\n', dirty: true });
    put(newNoteKey(1, 'offline'), { bookId: 4, quote: null, text: '끊긴 쪽지', dirty: true });
    put(newNoteKey(1, 'gone'), { bookId: 5, quote: null, text: '책이 지워진 쪽지', dirty: true });
    put(newNoteKey(2, 'other'), { bookId: 3, quote: null, text: '남의 칸', dirty: true });
    put('margin:1:70', { dirty: true }); // id 칸 — 화면이 연다(여기 몫 아님)
    const sent: CreateMargin[] = [];
    await sendNewNotes(kv, 1, async (b) => {
      sent.push(b);
      if (b.bookId === 4) throw new NetworkError();
      if (b.bookId === 5) throw new ApiError(404, '책을 찾을 수 없어요');
    });
    expect(sent).toEqual(expect.arrayContaining([
      { bookId: 3, quote: '옮긴 문장', text: '생각' },
      { bookId: null, quote: null, text: '책 없는 생각' },
    ]));
    expect(sent).toHaveLength(4); // 빈 칸 · 남의 칸은 보내지 않는다
    expect([...kv.map.keys()].sort()).toEqual(['margin:1:70', newNoteKey(1, 'gone'), newNoteKey(1, 'offline'), newNoteKey(2, 'other')].sort());
  });
});

test('REQ-142 · 제목·본문이 공백뿐이고 받은 질문이 0인 글만 나갈 때 지운다', () => {
  expect(shouldDiscard({ title: '', body: '  \n', rounds: 0 })).toBe(true);
  expect(shouldDiscard({ title: '', body: 'a', rounds: 0 })).toBe(false);
  expect(shouldDiscard({ title: '제목', body: '', rounds: 0 })).toBe(false);
  expect(shouldDiscard({ title: '', body: '', rounds: 1 })).toBe(false);
});
