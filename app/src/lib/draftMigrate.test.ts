// 기기 칸 옮기기(책생글 「여백 + 블로그」 §5.1 · J5 · REQ-222) — dirty `draft:` 칸을 GET movedTo 갈래로 쪽지에 옮긴다. KV는 Map 가짜.
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
}));

import { ApiError, NetworkError } from '../api/client';
import type { CreateMargin, DraftView, EditMargin, MarginView } from '../api/types';
import { migrateDraftSlots } from './draftMigrate';
import { type KV, type Slot, slotKey } from './draftSync';

const MOVED = '이 글은 여백으로 옮겨졌어요 — 앱을 닫았다가 다시 열어 주세요';

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

const slot = (p: Partial<Slot> = {}): Slot => ({ title: '칸 제목', body: '칸 본문', focus: null, base: 3, dirty: true, ...p });

const draft = (p: Partial<DraftView> = {}): DraftView => ({
  id: 7, bookId: 1, bookTitle: '데미안', title: '서버 제목', body: '서버 본문', revision: 3,
  questions: { rounds: [] }, aiUpdatedAt: null, aiRemainingToday: 20, aiConsent: true, movedTo: 70, ...p,
});

const note = (p: Partial<MarginView> = {}): MarginView => ({
  id: 70, bookId: 1, bookTitle: '데미안', quote: null, quoteSource: null, text: '서버 제목\n\n서버 본문', revision: 0,
  questions: { rounds: [] }, aiUpdatedAt: null, aiRemainingToday: 20, aiConsent: true, exported: false,
  createdAt: '2026-11-05T00:00:00Z', updatedAt: '2026-11-05T00:00:00Z', ...p,
});

function setup(p: {
  getDraft?: () => Promise<DraftView>;
  getNote?: () => Promise<MarginView>;
  editNote?: () => Promise<MarginView>;
  createNote?: () => Promise<MarginView>;
  slots?: Record<string, Slot>;
} = {}) {
  const kv = memoryKV();
  for (const [k, v] of Object.entries(p.slots ?? { [slotKey(1, 7)]: slot() })) kv.map.set(k, JSON.stringify(v));
  const calls = { getDraft: [] as number[], getNote: [] as number[], edit: [] as [number, EditMargin][], create: [] as CreateMargin[] };
  const deps = {
    kv,
    userId: 1,
    getDraft: async (id: number) => (calls.getDraft.push(id), (p.getDraft ?? (async () => draft()))()),
    getNote: async (id: number) => (calls.getNote.push(id), (p.getNote ?? (async () => note()))()),
    editNote: async (id: number, b: EditMargin) =>
      (calls.edit.push([id, b]), (p.editNote ?? (async () => note({ text: b.text, revision: b.revision + 1 })))()),
    createNote: async (b: CreateMargin) =>
      (calls.create.push(b), (p.createNote ?? (async () => note({ id: 99, bookId: b.bookId, text: b.text })))()),
  };
  return { kv, calls, run: () => migrateDraftSlots(deps) };
}

test('REQ-222 · movedTo + 같은 글(공백 정규화) → 칸만 지운다', async () => {
  const s = setup({ slots: { [slotKey(1, 7)]: slot({ title: '  서버 제목', body: '서버   본문\n' }) } });
  await s.run();
  expect(s.calls.getNote).toEqual([70]);
  expect(s.calls.edit).toEqual([]);
  expect(s.calls.create).toEqual([]);
  expect(s.kv.map.has(slotKey(1, 7))).toBe(false);
});

test('REQ-222 · revision 0 다른 글 → 그 판(0)으로 PUT · 칸은 받은 뒤 지운다', async () => {
  const s = setup();
  await s.run();
  expect(s.calls.edit).toEqual([[70, { quote: null, text: '칸 제목\n\n칸 본문', revision: 0 }]]);
  expect(s.calls.create).toEqual([]);
  expect(s.kv.map.has(slotKey(1, 7))).toBe(false);
});

test('REQ-222 · 제목만 · 본문만이면 빈 줄 없이 그것만', async () => {
  const s = setup({ slots: { [slotKey(1, 7)]: slot({ title: '  ', body: '본문만' }) } });
  await s.run();
  expect(s.calls.edit[0][1].text).toBe('본문만');
});

test('REQ-222 · revision > 0(이미 고친 쪽지) → 덮어쓰지 않고 새 쪽지(DraftView의 책)', async () => {
  const s = setup({ getNote: async () => note({ revision: 2, text: '앱에서 고친 쪽지' }) });
  await s.run();
  expect(s.calls.edit).toEqual([]);
  expect(s.calls.create).toEqual([{ bookId: 1, quote: null, text: '칸 제목\n\n칸 본문' }]);
  expect(s.kv.map.has(slotKey(1, 7))).toBe(false);
});

test('REQ-222 · movedTo null · 410 → 새 쪽지(책 없는 쪽지 포함)', async () => {
  const unmoved = setup({ getDraft: async () => draft({ movedTo: null, bookId: 5 }) });
  await unmoved.run();
  expect(unmoved.calls.getNote).toEqual([]);
  expect(unmoved.calls.create).toEqual([{ bookId: 5, quote: null, text: '칸 제목\n\n칸 본문' }]);
  expect(unmoved.kv.map.has(slotKey(1, 7))).toBe(false);

  // 책이 풀린 글 — 책 없는 쪽지
  const bookless = setup({ getDraft: async () => draft({ movedTo: null, bookId: null, bookTitle: null }) });
  await bookless.run();
  expect(bookless.calls.create).toEqual([{ bookId: null, quote: null, text: '칸 제목\n\n칸 본문' }]);

  // 410(M2 뒤) — 책을 알 수 없어 책 없는 쪽지
  const gone = setup({ getDraft: async () => { throw new ApiError(410, MOVED); } });
  await gone.run();
  expect(gone.calls.create).toEqual([{ bookId: null, quote: null, text: '칸 제목\n\n칸 본문' }]);
  expect(gone.kv.map.has(slotKey(1, 7))).toBe(false);
});

// J97 — 지운 뜻이 이긴다: 404는 「지운 글」(쪽지를 지우면 원본도 함께 파기 — U-154 ⑤)이라 되살리지 않는다.
test('REQ-222 · 원본 404(지운 쪽지와 함께 파기) → 칸만 지움 · 만들기 0', async () => {
  const s = setup({ getDraft: async () => { throw new ApiError(404, '글을 찾을 수 없어요'); } });
  await s.run();
  expect(s.calls.getNote).toEqual([]);
  expect(s.calls.create).toEqual([]);
  expect(s.kv.map.has(slotKey(1, 7))).toBe(false);
});

test('REQ-222 · movedTo 쪽지 GET 404 · PUT 404 → 칸만 지움 · 만들기 0', async () => {
  const atGet = setup({ getNote: async () => { throw new ApiError(404, '여백을 찾을 수 없어요'); } });
  await atGet.run();
  expect([atGet.calls.edit, atGet.calls.create]).toEqual([[], []]);
  expect(atGet.kv.map.has(slotKey(1, 7))).toBe(false);

  const atPut = setup({ editNote: async () => { throw new ApiError(404, '여백을 찾을 수 없어요'); } });
  await atPut.run();
  expect(atPut.calls.edit).toHaveLength(1);
  expect(atPut.calls.create).toEqual([]);
  expect(atPut.kv.map.has(slotKey(1, 7))).toBe(false);
});

test('REQ-222 · PUT 409 → 칸 그대로 · 다음 실행(쪽지 revision > 0)에 새 쪽지 하나', async () => {
  let fixed = false; // 첫 실행 사이에 다른 기기가 고쳤다 — 다음 실행은 고친 판을 읽는다
  const s = setup({
    getNote: async () => (fixed ? note({ revision: 1, text: '다른 기기에서 고친 쪽지' }) : note()),
    editNote: async () => { fixed = true; throw new ApiError(409, '다른 기기에서 이 글이 바뀌었어요'); },
  });
  await s.run();
  expect(s.calls.create).toEqual([]);
  expect(JSON.parse(s.kv.map.get(slotKey(1, 7))!)).toEqual(slot());

  await s.run();
  expect(s.calls.edit).toHaveLength(1); // 둘째 실행은 revision > 0이라 덮어쓰지 않는다
  expect(s.calls.create).toEqual([{ bookId: 1, quote: null, text: '칸 제목\n\n칸 본문' }]);
  expect(s.kv.map.has(slotKey(1, 7))).toBe(false);
});

test('REQ-222 · 만들기 404(책이 그사이 지워짐) → 칸 그대로', async () => {
  const s = setup({
    getDraft: async () => draft({ movedTo: null, bookId: 5 }),
    createNote: async () => { throw new ApiError(404, '책을 찾을 수 없어요'); },
  });
  await s.run();
  expect(s.calls.create).toHaveLength(1);
  expect(JSON.parse(s.kv.map.get(slotKey(1, 7))!)).toEqual(slot());
});

test('REQ-222 · 연결 실패 → 칸 그대로(다음에 다시)', async () => {
  const atDraft = setup({ getDraft: async () => { throw new NetworkError(); } });
  await atDraft.run();
  expect(JSON.parse(atDraft.kv.map.get(slotKey(1, 7))!)).toEqual(slot());

  const atNote = setup({ getNote: async () => { throw new NetworkError(); } });
  await atNote.run();
  expect(atNote.calls.edit).toEqual([]);
  expect(atNote.kv.map.has(slotKey(1, 7))).toBe(true);
});

test('REQ-222 · 쓰기가 실패하면(연결 · 서버 오류) 칸 그대로', async () => {
  const kv = memoryKV();
  kv.map.set(slotKey(1, 7), JSON.stringify(slot()));
  await migrateDraftSlots({
    kv, userId: 1,
    getDraft: async () => draft({ movedTo: null }),
    getNote: async () => note(),
    editNote: async () => note(),
    createNote: async () => { throw new ApiError(500, '요청에 실패했어요 (500)'); },
  });
  expect(kv.map.has(slotKey(1, 7))).toBe(true);
});

test('REQ-222 · DraftView에 movedTo 필드가 없으면(옛 서버) 칸 그대로 · 아무것도 보내지 않는다', async () => {
  const { movedTo: _, ...old } = draft();
  const s = setup({ getDraft: async () => old as DraftView });
  await s.run();
  expect(s.calls.getNote).toEqual([]);
  expect(s.calls.create).toEqual([]);
  expect(JSON.parse(s.kv.map.get(slotKey(1, 7))!)).toEqual(slot());
});

test('REQ-222 · dirty가 아닌 칸은 지우기만 · 다른 userId · 다른 접두어 칸은 건드리지 않는다', async () => {
  const s = setup({
    slots: {
      [slotKey(1, 7)]: slot({ dirty: false }),
      [slotKey(2, 8)]: slot(),
      'margin:1:70': slot(),
    },
  });
  await s.run();
  expect(s.calls.getDraft).toEqual([]);
  expect([...s.kv.map.keys()].sort()).toEqual([slotKey(2, 8), 'margin:1:70']);
});

test('REQ-222 · 제목 · 본문이 다 빈 dirty 칸은 보낼 것이 없어 지우기만', async () => {
  const s = setup({ slots: { [slotKey(1, 7)]: slot({ title: ' ', body: '\n' }) } });
  await s.run();
  expect(s.calls.getDraft).toEqual([]);
  expect(s.kv.map.has(slotKey(1, 7))).toBe(false);
});
