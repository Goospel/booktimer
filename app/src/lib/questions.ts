// 질문 띠 · 질문 시트의 보기 모양(PR-H 사양 REQ-137 · 139 · §8-9 · §8-11).
import type { Question, QuestionLog, Round, Step } from '../api/types';

/** 빈 띠 문구 — 라운드 0에서만 책 · 여백 사정을 알린다(꼬리 질문은 내 글을 읽는다). */
export function bandHint(p: { rounds: number; aiIncluded: number; hasBook: boolean }): string {
  if (p.rounds === 0 && !p.hasBook) return '책이 지워진 글이라 첫 질문은 받을 수 없어요';
  if (p.rounds === 0 && p.aiIncluded === 0) return '이 책에 여백을 하나 남기면 질문을 받을 수 있어요';
  return '질문을 고르거나, 그냥 써도 돼요';
}

/** 마지막 라운드는 펼치고, 지난 라운드는 최근 것부터 하나로 접는다. */
export function sheetRounds(log: QuestionLog): { latest: Round | null; past: Round[] } {
  const n = log.rounds.length;
  return { latest: n > 0 ? log.rounds[n - 1] : null, past: log.rounds.slice(0, -1).reverse() };
}

export function roundTitle(step: Step): '여백에서 받은 질문' | '내 글에서 받은 꼬리 질문' {
  return step === 'ASK' ? '여백에서 받은 질문' : '내 글에서 받은 꼬리 질문';
}

/** 띠에 있는 질문(글자가 같으면) — 「지금 답하는 질문」 표시. */
export function isPicked(q: Question, focus: string | null): boolean {
  return focus !== null && q.text === focus;
}

/** 「질문 다시 받기」 = 마지막 라운드의 단계 · 그 라운드의 focus로 한 번 더(라운드 하나를 쓴다). */
export function againArgs(log: QuestionLog): { step: Step; focus: string | null } | null {
  const last = sheetRounds(log).latest;
  return last ? { step: last.step, focus: last.focus } : null;
}
