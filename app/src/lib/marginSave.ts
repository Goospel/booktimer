// 여백 쪽지 판정(책생글 「여백 + 블로그」 A1 — 설계 §5.1 · F1 사양 §4.3 · §4.4). 화면은 이 함수들의 결과를 그대로 그린다.
// 「공개 책이에요」 확인 단계는 없다 — 여백은 나만 본다(D1 · REQ-223). 저장은 자동이라 [남기기]도 없다(F1 §8-3).
import type { EditMargin, MarginSummary } from '../api/types';
import { dateLabel } from './format';

const has = (s: string | null | undefined) => (s ?? '').trim() !== '';

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
export function leaveAction(p: { loaded: boolean; empty: boolean; waiting: boolean; noteId: number | null; creating: boolean }): LeaveAction {
  const discard = p.empty && !p.waiting;
  if (p.noteId === null) return discard ? (p.creating ? 'delete-after-create' : 'drop-slot') : 'create';
  if (!p.loaded) return 'none';
  return discard ? 'delete' : 'flush';
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
