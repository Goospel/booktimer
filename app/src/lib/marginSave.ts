// 여백 쪽지 판정(책생글 「여백 + 블로그」 A1 — 설계 §5.1 · F1 사양 §4.3 · §4.4). 화면은 이 함수들의 결과를 그대로 그린다.
// 「공개 책이에요」 확인 단계는 없다 — 여백은 나만 본다(D1 · REQ-223). 저장은 자동이라 [남기기]도 없다(F1 §8-3).
import { NetworkError } from '../api/client';
import type { BookOption, CreateMargin, Dashboard, EditMargin, MarginSummary, MarginView } from '../api/types';
import { isGone, type SaveOutcome, type Slot, shouldDiscard } from './draftSync';
import { bookSourceLine, buildNoteExport } from './exportText';
import { dateLabel } from './format';

const has = (s: string | null | undefined) => (s ?? '').trim() !== '';

// Java Character.isWhitespace 집합 — 서버 Story.edit의 String.strip()이 걷는 글자. JS trim()과 다르다:
// 줄바꿈 없는 공백(U+00A0 · U+2007 · U+202F)과 BOM(U+FEFF)은 남기고, U+001C~U+001F는 걷는다(A1 리뷰 11 — 검증자 JDK 25 실측).
const JAVA_WS = '[\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000]';
const JAVA_STRIP = new RegExp(`^${JAVA_WS}+|${JAVA_WS}+$`, 'g');

/** 서버 String.strip()과 같은 결과. */
export function javaStrip(s: string): string {
  return s.replace(JAVA_STRIP, '');
}

/** 자동저장 응답의 생각 — 서버가 앞뒤 공백만 걷은 것이면 보낸 글을 그대로 둔다(「서버와 달라요」 · 영구 dirty를 띄우지 않는다). */
export function keptBody(sent: string, server: string): string {
  return server === javaStrip(sent) ? sent : server;
}

/** 두 칸 중 하나라도 글자가 있다 — [내보내기]가 켜진다(F1 §4.4-1). */
export function canSaveMargin(i: { quote: string; thought: string }): boolean {
  return has(i.quote) || has(i.thought);
}

/** 생각이 비었거나 문장부호 · 공백뿐이면 그리지 않는다 — 서버 WritingMaterial.NO_THOUGHT와 같은 식(이모지는 생각이다). */
export function showsThought(text: string | null | undefined): boolean {
  return !/^[\p{P}\p{Z}\s]*$/u.test(text ?? '');
}

/** 쪽지 생각 입력칸 상한 — 1만 자, 불러온 글이 더 긴 옮긴 쪽지(옛 글 최대 10,102자)는 그 길이까지(REQ-210 · J3). 서버 상한은 10,200. */
export function noteMaxLength(loaded: string): number {
  return Math.max(10_000, loaded.length);
}

/** 옮긴 문장 칸 — 책이나 출처 스냅샷이 있는 쪽지만(서버 Story.edit가 옮긴 문장을 받는 조건과 같다 — J84). */
export function hasQuoteField(n: { bookId: number | null; quoteSource: string | null }): boolean {
  return n.bookId !== null || n.quoteSource !== null;
}

/**
 * 자동저장 PUT 몸 — quote를 늘 싣는다. 칸이 있으면 칸 값, 없으면 불러온 값 그대로(F1 §4.4-3).
 * 서버 Story.edit는 quote를 늘 덮어써서, 빼거나 null을 새로 만들어 보내면 옮긴 문장이 말없이 지워진다.
 */
export function noteEditBody(p: { field: boolean; quote: string; text: string; revision: number; loadedQuote: string | null; exported?: boolean }): EditMargin {
  const body: EditMargin = { quote: p.field ? p.quote : p.loadedQuote, text: p.text, revision: p.revision };
  return p.exported ? { ...body, exported: true } : body;
}

/** 쪽지 화면 머리 제목 · 생각 칸 자리글자(F1 §4.4-1 · §7-8 · §7-15). */
export function noteHead(p: { bookTitle: string | null; field: boolean }): { title: string; placeholder: string } {
  return {
    title: p.bookTitle ?? '책 없는 여백',
    placeholder: p.field ? '그 문장에 대해 든 생각을 남겨 보세요' : '떠오른 생각을 마음껏 적어 보세요',
  };
}

/** 마지막 대시보드(측정 가게 캐시)의 독서 서재 책 하나 — 읽는 중 · 다 읽음 · 읽고 싶어요를 다 본다(REQ-391). 없으면 null. */
export function cachedBook(
  d: Pick<Dashboard, 'readingBooks' | 'wantToReadBooks' | 'finishedBooks'> | null | undefined,
  bookId: number,
): BookOption | null {
  if (!d) return null;
  return [...d.readingBooks, ...(d.finishedBooks ?? []), ...d.wantToReadBooks].find((b) => b.id === bookId) ?? null;
}

/**
 * 서버 판 없이 그리는 머리(REQ-391 · 392) — 새 책 쪽지(재료를 기다리지 않는다)와 기기 칸으로 연 쪽지.
 * 책 쪽지인데 캐시에 책이 없으면 빈 제목 — 「책 없는 여백」이라 하지 않는다(noteHead는 null만 바꾼다).
 * 옮긴 문장 칸 = 책 쪽지 ∨ 칸에 옮긴 문장(hasQuoteField의 기기판 — quoteSource를 모른다).
 */
export function cachedHead(p: { bookId: number | null; book: { title: string; author: string | null } | null; quote: string }): {
  bookTitle: string | null;
  bookLine: string | null;
  field: boolean;
} {
  if (p.bookId === null) return { bookTitle: null, bookLine: null, field: p.quote !== '' };
  return { bookTitle: p.book?.title ?? '', bookLine: p.book ? bookSourceLine(p.book) : null, field: true };
}

/**
 * (Q1 가) 「둘 다 남기기」의 새 쪽지 몸 — 그쪽(서버) 판(REQ-394). 책 없는 쪽지는 옮긴 문장 · 출처를 생각 앞에(만들기 400). 남길 글이 없으면 null.
 * mine = 이 기기 칸 — 그쪽 판이 그와 같으면(응답을 잃은 저장의 재전송이 낸 거짓 409) 남길 다른 판이 없다 → null. 서버가 걷는 앞뒤 공백은 javaStrip으로 맞춘다(Story.normalizeQuote · normalizeText).
 */
export function copyOfNote(v: Pick<MarginView, 'bookId' | 'quote' | 'quoteSource' | 'text'>, mine?: { title: string; body: string } | null): CreateMargin | null {
  if (mine && (v.quote ?? '') === javaStrip(mine.title) && v.text === javaStrip(mine.body)) return null;
  const quote = has(v.quote) ? v.quote : null;
  if (quote === null && v.text.trim() === '') return null;
  if (v.bookId !== null) return { bookId: v.bookId, quote, text: v.text };
  return { bookId: null, quote: null, text: buildNoteExport({ quote, quoteSource: v.quoteSource, text: v.text }) };
}

/**
 * (Q1 가) 충돌 창 「이 기기 것 유지」 · 「둘 다 남기기」의 효과(REQ-394 — runLeave와 같은 꼴: 판정은 여기, 화면은 묶음만 넘긴다).
 * 둘 다면 그쪽 판을 새 쪽지로 **먼저** 만들고, 못 만들면 덮지 않는다 — keepMine을 부르지 않는다(칸은 dirty · 옛 판이라 다음 저장이 다시 묻는다 · 두 칸을 비우고 떠나도 지우지 않는다 — REQ-400).
 * both = 새 쪽지를 만들었고 덮기도 섰다(saved · mismatch) — 「새 쪽지로 남겼어요」를 띄울 때(설계 §5.4 ④).
 */
export async function keepConflict(
  which: 'mine' | 'both',
  v: Pick<MarginView, 'bookId' | 'quote' | 'quoteSource' | 'text'>,
  d: { mine: () => Promise<{ title: string; body: string } | null>; add: (b: CreateMargin) => Promise<unknown>; keepMine: () => Promise<SaveOutcome> },
): Promise<{ ok: true; r: SaveOutcome; both: boolean } | { ok: false; cause: unknown }> {
  const copy = which === 'both' ? copyOfNote(v, await d.mine()) : null;
  if (copy) {
    try {
      await d.add(copy);
    } catch (cause) {
      return { ok: false, cause };
    }
  }
  const r = await d.keepMine();
  return { ok: true, r, both: copy !== null && (r.kind === 'saved' || r.kind === 'mismatch') };
}

/**
 * 기존 쪽지 읽기(REQ-392 · P3 · P7) — 연결 실패(10초 상한 포함)만 null(이 기기 칸으로 연다).
 * 404(다른 곳에서 지움)면 칸을 버리고 던진다(J97 — 「돌아가기」). 그 밖(5xx · 401)은 칸을 두고 던진다(지금 그대로 — 「다시 불러오기」).
 */
export async function readNote(get: () => Promise<MarginView>, drop: () => Promise<unknown>): Promise<MarginView | null> {
  try {
    return await get();
  } catch (e) {
    if (isGone(e)) await drop();
    if (e instanceof NetworkError) return null;
    throw e;
  }
}

/**
 * 다른 기기가 고친 뒤의 빈 dirty 칸(REQ-398) — 오프라인에서 비우고 떠났거나(REQ-395 'none') 지우기가 연결 실패로 남은 칸.
 * 잃을 글이 없다. 그대로 그리면 빈 쪽지는 PUT하지 않으므로(판 대조 409가 서지 않는다) 떠날 때 판 대조 없는 DELETE가 그쪽 글을 지운다.
 * 받은 질문이 있으면 거짓 — 빈 PUT도 서버가 받아(원가 든 글 · Story.edit) 판 대조 409 → 충돌 창이 지금처럼 묻는다.
 */
export const staleEmpty = (slot: Pick<Slot, 'title' | 'body' | 'base' | 'dirty'>, revision: number, rounds: number): boolean =>
  slot.dirty && slot.base !== revision && shouldDiscard({ title: slot.title, body: slot.body, rounds });

/** 쪽지 화면 주소 — 책 없는 쪽지는 `none`, 기존 쪽지는 `?note=`(J75). */
export function noteHref(bookId: number | null, noteId?: number): string {
  return `/margin/${bookId ?? 'none'}${noteId === undefined ? '' : `?note=${noteId}`}`;
}

const positiveInt = (s: string | undefined) => (s !== undefined && /^\d+$/.test(s) ? Number(s) : null);

/** 라우트 매개변수 → 책(없으면 null) · 쪽지 id(없거나 깨지면 null = 새 쪽지). */
export function noteRoute(p: { bookId?: string; note?: string }): { bookId: number | null; noteId: number | null } {
  return { bookId: positiveInt(p.bookId), noteId: positiveInt(p.note) };
}

/** 목록의 쪽지 한 줄(F1 §4.3-3) — 옮긴 문장 · 생각 미리보기(서버가 접은 앞 80자) · 날짜 · 받은 질문이 있으면 AI 표시. */
export function noteRow(m: MarginSummary): { quote: string | null; thought: string | null; date: string; ai: boolean; label: string } {
  const quote = has(m.quote) ? m.quote : null;
  const thought = showsThought(m.preview) ? m.preview : null;
  const date = dateLabel(new Date(m.createdAt));
  const label = [quote && `옮긴 문장: ${quote}`, thought && `내 생각: ${thought}`, date, m.hasAi && 'AI 질문 받음'].filter(Boolean).join(', ');
  return { quote, thought, date, ai: m.hasAi, label };
}

/** 서재 맨 아래 「책 없는 여백」 — 0개면 단째 숨김(REQ-226). */
export function booklessSection(list: MarginSummary[]): { count: string } | null {
  return list.length === 0 ? null : { count: `${list.length}개` };
}

/** 남긴 뒤 돌아간 화면이 한 번 읽는 한 칸짜리 표시(「여백을 남겼어요」 — REQ-135). */
let saved = false;
export const savedNotice = {
  mark() {
    saved = true;
  },
  take() {
    const v = saved;
    saved = false;
    return v;
  },
};

export type LeaveAction = 'none' | 'drop-slot' | 'create' | 'delete-after-create' | 'delete' | 'flush';

/**
 * 쪽지 화면을 떠날 때 할 일(F1 §4.4-7). loaded = 기존 쪽지를 GET으로 불러왔는가(새 쪽지는 참) — 불러오기 전이면 지우지도 보내지도 않는다
 * (편집기가 빈 채라 「빈 쪽지」로 보인다 — A1 리뷰 1). empty = 두 칸이 비고 받은 질문 0 · waiting = 질문을 보내는 중(서버가 몫을 깎았다).
 * 새 쪽지 첫 POST가 나가는 중에 다 지우고 떠나면 만든 뒤 지운다(A1 리뷰 7).
 */
export function leaveAction(p: { loaded: boolean; empty: boolean; waiting: boolean; noteId: number | null; creating: boolean; device?: boolean }): LeaveAction {
  const discard = p.empty && !p.waiting;
  // 만드는 중이면 noteId가 섰어도(adopt 창) 새 쪽지 갈래 — 아직 없는 id 칸을 flush하지 않고 같은 약속을 기다린다(3차 5)
  if (p.noteId === null || p.creating) return discard ? (p.creating ? 'delete-after-create' : 'drop-slot') : 'create';
  if (!p.loaded) return 'none';
  if (p.device) return p.empty ? 'none' : 'flush'; // 기기 칸으로 연 쪽지 — 받은 질문 수를 몰라 빈 쪽지도 지우지 않는다(REQ-395 · REQ-142)
  return discard ? 'delete' : 'flush';
}

/**
 * 떠날 때 할 일을 실제로 부른다(재리뷰 A). 돌려준 약속 = 그 갈래의 마지막 효과의 끝 — 화면은 이것을 pendingLeave에 넣는다.
 * create는 만든 뒤 만드는 동안 더 쓴 것을 곧바로 보낸다(언마운트 뒤 1.5초 타이머에 맡기면 돌아간 목록이 옛 미리보기다 — 재리뷰 H).
 * delete는 서버가 지웠거나 404(이미 없음 — isGone)면 칸을 지운다. 그 밖의 실패는 삼키고 칸을 남긴다(다음에 다시 열 수 있다 — 3차 4).
 * delete는 지우기 전에 칸을 읽는다 — 칸의 판이 seen(이 기기가 아는 서버 판 · 409를 받고 아직 못 읽었으면 null)과 다르면 지우지 않고 칸을 남긴다. 칸 읽기가 거부되면 지우지 않고 거부로 끝난다(pendingLeave가 삼킨다).
 * 충돌을 풀지 못한 채 비우고 떠나면 판 대조 없는 DELETE가 다른 기기의 새 판을 지운다 — 남긴 칸은 다음 열기의 staleEmpty가 그 글로 바꾼다(REQ-400 · 401).
 */
export function runLeave(
  act: LeaveAction,
  d: {
    dropNewSlot: () => Promise<unknown>;
    createFirst: () => Promise<{ kind: string }>;
    del: (id: number) => Promise<unknown>;
    drop: (id: number) => Promise<unknown>;
    flush: (id: number) => Promise<unknown>;
    noteId: () => number | null;
    local: (id: number) => Promise<{ base: number } | null>;
    seen: number | null;
  },
): Promise<unknown> {
  const remove = (id: number) => d.del(id).then(() => d.drop(id), (e) => (isGone(e) ? d.drop(id) : undefined));
  const afterCreate = (f: (id: number) => Promise<unknown>) =>
    d.createFirst().then((r) => {
      const id = d.noteId();
      return r.kind === 'saved' && id !== null ? f(id) : undefined;
    });
  const id = d.noteId();
  if (act === 'drop-slot') return d.dropNewSlot();
  if (act === 'create') return afterCreate(d.flush);
  if (act === 'delete-after-create') return Promise.all([d.dropNewSlot(), afterCreate(remove)]);
  if (act === 'delete' && id !== null) return d.local(id).then((slot) => (slot && slot.base !== d.seen ? undefined : remove(id))); // 칸이 이 기기가 아는 서버 판 위에 없다 — 지우지 않는다(REQ-400 · 401)
  if (act === 'flush' && id !== null) return d.flush(id);
  return Promise.resolve();
}

export const LEAVE_WAIT_MS = 2_000;
let leaving: Promise<unknown> = Promise.resolve();
/** 떠날 때 보낸 저장 한 칸 — 돌아간 책 화면 · 서재가 목록을 읽기 전에 상한까지만 기다린다(떠나는 화면은 기다리지 않는다 — A1 리뷰 12). */
export const pendingLeave = {
  set(p: Promise<unknown>) {
    leaving = p.catch(() => undefined);
  },
  wait(ms = LEAVE_WAIT_MS): Promise<void> {
    let t: ReturnType<typeof setTimeout> | undefined;
    const cap = new Promise<void>((r) => {
      t = setTimeout(r, ms);
    });
    return Promise.race([leaving, cap]).then(() => clearTimeout(t));
  },
};
