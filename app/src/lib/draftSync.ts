// 기기 초안 · 자동저장(v1 REQ-38 · PR-H 사양 REQ-141 · 142). 저장 안 된 제목 · 본문과 띠의 질문은 userId · 글별 칸에 둔다.
// 서버가 원본이고, 칸은 서버가 확인하기 전까지의 안전망이다. 저장 요청은 글마다 한 번에 하나(응답 뒤 그사이 바뀌었으면 칸이 dirty로 남는다).
import { ApiError, failText, NetworkError } from '../api/client';
import type { DraftView, SaveDraft } from '../api/types';

export interface Slot {
  title: string;
  body: string;
  /** 띠의 질문 — 이 기기에만 있다(서버에는 다음 FOLLOW의 focus로만 간다) */
  focus: string | null;
  /** 칸이 기대는 서버 판 */
  base: number;
  /** 서버에 아직 안 간 제목 · 본문이 있다 */
  dirty: boolean;
}

/** AsyncStorage 꼴 — 테스트는 Map 가짜를 넣는다. */
export interface KV {
  getItem(k: string): Promise<string | null>;
  setItem(k: string, v: string): Promise<void>;
  removeItem(k: string): Promise<void>;
  getAllKeys(): Promise<readonly string[]>;
}

export type SaveOutcome =
  | { kind: 'saved'; revision: number }
  | { kind: 'mismatch'; revision: number } // 응답 본문 ≠ 보낸 본문 — 칸을 지우지 않고 알린다
  | { kind: 'conflict' } // 409
  | { kind: 'gone' } // 404 — 칸을 버렸다
  | { kind: 'offline' } // 연결 없음 — 칸에 남는다
  | { kind: 'error'; message: string; cause?: unknown }; // cause = 원래 오류(AI 보내기가 다시 던져 문구를 고른다)

export const AUTOSAVE_MS = 1_500;
/** 기기 칸 접두어 셋(J80) — 옛 글 `draft:` · 여백 쪽지 `margin:` · 블로그 초안 `blog:`. 로그아웃 · 탈퇴는 셋을 다 센다 · 지운다. */
export const SLOT_PREFIXES = ['draft', 'margin', 'blog'] as const;
export type SlotPrefix = (typeof SLOT_PREFIXES)[number];

export const slotKey = (userId: number, id: number, prefix: SlotPrefix = 'draft') => `${prefix}:${userId}:${id}`;

const fromServer = (d: DraftView, focus: string | null): Slot => ({ title: d.title ?? '', body: d.body, focus, base: d.revision, dirty: false });

const userKeys = async (kv: KV, userId: number, prefixes: readonly SlotPrefix[]) =>
  (await kv.getAllKeys()).filter((k) => prefixes.some((x) => k.startsWith(`${x}:${userId}:`)));

async function countDirty(kv: KV, keys: readonly string[]): Promise<number> {
  const slots = await Promise.all(keys.map((k) => kv.getItem(k)));
  return slots.filter((raw) => raw !== null && (JSON.parse(raw) as { dirty?: boolean }).dirty === true).length;
}

/** 이 userId의 미전송 칸 수 — 세 접두어 합(로그아웃 · 탈퇴 확인 — J80). */
export async function pendingAll(kv: KV, userId: number): Promise<number> {
  return countDirty(kv, await userKeys(kv, userId, SLOT_PREFIXES));
}

/** 이 userId의 칸 전부 — 세 접두어(로그아웃 · 탈퇴 — J80). */
export async function clearAll(kv: KV, userId: number): Promise<void> {
  await Promise.all((await userKeys(kv, userId, SLOT_PREFIXES)).map((k) => kv.removeItem(k)));
}

/** 첫 저장 전 새 쪽지 칸(J80) — 로컬 무작위 id라 같은 책 새 쪽지 둘을 오프라인에서 써도 칸이 겹치지 않는다. */
export const newNoteKey = (userId: number, localId: string) => `margin:${userId}:new:${localId}`;

/** 새 쪽지 칸을 만들고 로컬 id를 돌려준다. 칸 안에 bookId(책 없는 쪽지면 null)를 둔다 — 앱이 꺼져도 어느 책 쪽지인지 남게. */
export async function startNewNote<T extends { bookId: number | null }>(kv: KV, userId: number, slot: T): Promise<string> {
  const localId = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
  await kv.setItem(newNoteKey(userId, localId), JSON.stringify(slot));
  return localId;
}

/** 첫 POST가 돌아오면 새 쪽지 칸을 id 칸(`margin:${userId}:${storyId}`)으로 옮긴다. */
export async function adoptNewNote(kv: KV, userId: number, localId: string, storyId: number): Promise<void> {
  const raw = await kv.getItem(newNoteKey(userId, localId));
  if (raw !== null) await kv.setItem(slotKey(userId, storyId, 'margin'), raw);
  await kv.removeItem(newNoteKey(userId, localId));
}

export function draftSync(p: { kv: KV; userId: number; put: (id: number, b: SaveDraft) => Promise<DraftView>; prefix?: SlotPrefix }) {
  const kind = p.prefix ?? 'draft';
  const key = (id: number) => slotKey(p.userId, id, kind);
  const read = async (id: number): Promise<Slot | null> => {
    const raw = await p.kv.getItem(key(id));
    return raw === null ? null : (JSON.parse(raw) as Slot);
  };
  const write = (id: number, s: Slot) => p.kv.setItem(key(id), JSON.stringify(s));
  const chain = new Map<number, Promise<SaveOutcome>>();
  // 칸 읽고 쓰기는 한 줄로 세운다 — 화면은 글자마다 edit를 기다리지 않고 부른다(엇갈리면 앞 편집이 사라진다). 네트워크는 줄 밖이다.
  let tail: Promise<unknown> = Promise.resolve();
  const serial = <T>(f: () => Promise<T>): Promise<T> => {
    const r = tail.then(f, f);
    tail = r.catch(() => undefined);
    return r;
  };

  async function flushOnce(id: number, exported: boolean): Promise<SaveOutcome> {
    const sent = await serial(() => read(id));
    if (sent === null) return { kind: 'error', message: '글을 저장하지 못했어요 — 잠시 뒤 다시 해 주세요' };
    if (!sent.dirty && !exported) return { kind: 'saved', revision: sent.base };
    let res: DraftView;
    try {
      res = await p.put(id, exported ? { title: sent.title, body: sent.body, revision: sent.base, exported } : { title: sent.title, body: sent.body, revision: sent.base });
    } catch (e) {
      if (e instanceof NetworkError) return { kind: 'offline' };
      if (e instanceof ApiError && e.status === 409) return { kind: 'conflict' };
      if (e instanceof ApiError && e.status === 404) {
        await serial(() => p.kv.removeItem(key(id)));
        return { kind: 'gone' };
      }
      return { kind: 'error', message: failText(e, '글을 저장하지 못했어요 — 잠시 뒤 다시 해 주세요'), cause: e };
    }
    const mismatch = res.body !== sent.body;
    await serial(async () => {
      const now = (await read(id)) ?? sent; // 요청 중에 더 쓴 것이 있으면 칸은 dirty로 남는다
      await write(id, { ...now, base: res.revision, dirty: mismatch || now.title !== sent.title || now.body !== sent.body });
    });
    return mismatch ? { kind: 'mismatch', revision: res.revision } : { kind: 'saved', revision: res.revision };
  }

  const api = {
    /** 칸이 dirty면 칸(서버보다 앞), 아니면 서버 글 + 칸의 띠 질문. */
    open: (server: DraftView): Promise<Slot> =>
      serial(async () => {
        const slot = await read(server.id);
        if (slot?.dirty) return slot;
        const next = fromServer(server, slot?.focus ?? null);
        await write(server.id, next);
        return next;
      }),
    /** 칸에 즉시 — 제목 · 본문이 바뀌면 dirty, 띠 질문만이면 dirty가 아니다. */
    edit: (id: number, patch: Partial<Pick<Slot, 'title' | 'body' | 'focus'>>): Promise<void> =>
      serial(async () => {
        const slot = await read(id);
        if (slot === null) return;
        const text = patch.title !== undefined || patch.body !== undefined;
        await write(id, { ...slot, ...patch, dirty: slot.dirty || text });
      }),
    /** 저장 비우기 — 글마다 한 번에 하나. exported면 고친 것이 없어도 보낸다(공유 시트 sharedAction — REQ-37). */
    flush(id: number, opts: { exported?: boolean } = {}): Promise<SaveOutcome> {
      const next = (chain.get(id) ?? Promise.resolve()).catch(() => undefined).then(() => flushOnce(id, !!opts.exported));
      chain.set(id, next);
      return next;
    },
    /** 409 「이 기기 것 유지」 — 서버 판에 기대어 이 기기 글을 다시 보낸다. */
    async keepMine(id: number, server: DraftView): Promise<SaveOutcome> {
      await serial(async () => {
        const slot = (await read(id)) ?? fromServer(server, null);
        await write(id, { ...slot, base: server.revision, dirty: true });
      });
      return api.flush(id);
    },
    /** 409 「그쪽 불러오기」 — 칸을 서버 글로(띠 질문은 남긴다). */
    takeTheirs: (id: number, server: DraftView): Promise<Slot> =>
      serial(async () => {
        const next = fromServer(server, (await read(id))?.focus ?? null);
        await write(id, next);
        return next;
      }),
    drop: (id: number) => serial(() => p.kv.removeItem(key(id))),
    // 미전송 수 · 전부 지우기는 접두어 셋을 함께 보는 pendingAll · clearAll(J80).
  };
  return api;
}

/** 나갈 때 지울 빈 글 — 받은 질문이 있으면 원가가 든 글이라 남긴다(REQ-142). */
export function shouldDiscard(d: { title: string; body: string; rounds: number }): boolean {
  return d.title.trim() === '' && d.body.trim() === '' && d.rounds === 0;
}

/** 저장 결과 → AI 보내기의 revision. 저장이 안 됐으면 던진다 — 그때 POST하지 않는다(REQ-36). */
export function revisionOrThrow(o: SaveOutcome | null): { revision: number } {
  if (o?.kind === 'saved' || o?.kind === 'mismatch') return { revision: o.revision };
  if (o?.kind === 'offline') throw new NetworkError();
  if (o?.kind === 'error') throw o.cause ?? new Error(o.message); // 원래 오류 그대로 — 서버 문구가 없으면 저장 실패 문구가 된다
  if (o?.kind === 'conflict') throw new ApiError(409, '다른 기기에서 이 글이 바뀌었어요 — 그쪽을 불러와 주세요');
  throw new Error('not saved');
}
