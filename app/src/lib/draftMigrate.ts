// 기기 칸 옮기기(책생글 「여백 + 블로그」 §5.1 · J5 · REQ-222) — 옛 글 화면의 dirty `draft:` 칸을 여백 쪽지로 옮긴다.
// 앱이 켜질 때 한 번. 서버가 받은 뒤에만 칸을 지우고, 실패(연결 · 서버 오류)면 칸을 남겨 다음에 다시 한다.
// 갈래 표는 설계 §5.11 R1(J97) — 지운 뜻이 이긴다(원본 · 쪽지 404는 칸만 지움) · 모르면 잃지 않는다 · 엇갈리면(PUT 409) 다시 읽는다.
// 옛 JS가 사라지면(M2 뒤 확인) 이 파일을 지운다 — 앱에서 /api/app/drafts를 부르는 곳은 여기뿐이다(금지 표 REQ-224).
import { ApiError, margins, request } from '../api/client';
import type { CreateMargin, DraftView, EditMargin, MarginView } from '../api/types';
import { type KV, newNoteKey, sendNewNotes, type Slot } from './draftSync';

export interface MigrateDeps {
  kv: KV;
  userId: number;
  getDraft: (id: number) => Promise<DraftView>;
  getNote: (id: number) => Promise<MarginView>;
  editNote: (id: number, body: EditMargin) => Promise<MarginView>;
  createNote: (body: CreateMargin) => Promise<MarginView>;
}

/** 칸 글 = 서버 옮기기(V105)와 같은 꼴 — 제목 · 본문이 둘 다 있으면 「제목 + 빈 줄 + 본문」, 하나면 그것. */
const slotText = (s: Slot) => [s.title.trim(), s.body].filter((x) => x.trim() !== '').join('\n\n');
const norm = (s: string) => s.replace(/\s+/g, ' ').trim();
const isGone = (e: unknown) => e instanceof ApiError && e.status === 404;

async function migrateOne(d: MigrateDeps, key: string, id: number, slot: Slot): Promise<void> {
  const text = slotText(slot);
  if (!slot.dirty || text === '') return d.kv.removeItem(key); // 보낼 글이 없다

  let bookId: number | null = null;
  let movedTo: number | null = null;
  try {
    const draft = await d.getDraft(id);
    if (!('movedTo' in draft)) return; // M1 전 서버(J96) — 칸을 건드리지 않고 다음에 다시
    bookId = draft.bookId;
    movedTo = draft.movedTo ?? null;
  } catch (e) {
    if (isGone(e)) return d.kv.removeItem(key); // 원본이 없다 = 지운 글(J97) — 되살리지 않는다
    if (!(e instanceof ApiError && e.status === 410)) throw e; // M2 뒤 — 책을 알 수 없어 책 없는 쪽지
  }

  if (movedTo !== null) {
    try {
      const note = await d.getNote(movedTo);
      if (norm(note.text) === norm(text)) return d.kv.removeItem(key);
      if (note.revision === 0) {
        await d.editNote(movedTo, { quote: note.quote, text, revision: 0 }); // 409면 던져 칸을 남긴다 — 다음 실행이 다시 읽는다
        return d.kv.removeItem(key);
      }
    } catch (e) {
      if (isGone(e)) return d.kv.removeItem(key); // 그사이 쪽지를 지웠다(J97)
      throw e;
    }
    // 이미 고친 쪽지 — 덮어쓰지 않고 새 쪽지로
  }
  await d.createNote({ bookId, quote: null, text }); // 404(책이 그사이 지워짐)는 지움 신호가 아니다 — 던져 칸을 남긴다
  await d.kv.removeItem(key);
}

export async function migrateDraftSlots(d: MigrateDeps): Promise<void> {
  const prefix = `draft:${d.userId}:`;
  for (const key of (await d.kv.getAllKeys()).filter((k) => k.startsWith(prefix))) {
    const raw = await d.kv.getItem(key);
    if (raw === null) continue;
    try {
      await migrateOne(d, key, Number(key.slice(prefix.length)), JSON.parse(raw) as Slot);
    } catch {
      // 연결 실패 · 서버 오류 — 칸을 남긴다(다음에 켜질 때 다시)
    }
  }
}

/** 앱이 켜질 때 부르는 연결 — 실제 서버 경로. */
export const migrateDrafts = (kv: KV, userId: number) =>
  migrateDraftSlots({
    kv,
    userId,
    getDraft: (id) => request<DraftView>(`/api/app/drafts/${id}`),
    getNote: margins.get,
    editNote: margins.edit,
    createNote: margins.add,
  });

/**
 * 앱이 켜질 때(J5 · J100 · §5.11) — 옛 `draft:` 칸 옮기기 다음에 남은 새 쪽지 칸 보내기. 옮기기가 던져도 보내기는 돈다(둘 다 실패면 칸이 남는다).
 * 보낼 새 쪽지 칸은 옮기기 전에 스냅숏으로 뜬다 — 옮기는 동안 화면이 쓰기 시작한 칸은 화면 몫이다(A1 리뷰 9). _layout은 이 한 호출만 한다.
 */
export async function startSlotSync(
  kv: KV,
  userId: number,
  p: { migrate: (kv: KV, userId: number) => Promise<void>; send: (kv: KV, userId: number, keys: readonly string[]) => Promise<void> } = {
    migrate: migrateDrafts,
    send: (k, u, keys) => sendNewNotes(k, u, undefined, keys),
  },
): Promise<void> {
  const keys = (await kv.getAllKeys()).filter((k) => k.startsWith(newNoteKey(userId, '')));
  await p.migrate(kv, userId).catch(() => undefined); // 칸은 남아 다음 실행에 다시
  await p.send(kv, userId, keys);
}
