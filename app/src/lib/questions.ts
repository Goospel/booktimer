// 질문 띠 · 질문 시트의 보기 모양(PR-H 사양 REQ-137 · 139 · §8-9 · §8-11).
import type { Question, QuestionLog, Round, Step } from '../api/types';
import { hasMaterial, type NoteAiInput } from './aiStep';

/** 빈 띠 문구 — 라운드 0에 지금 쪽지에 읽을 것이 없을 때만 알린다(J98 · F1 §4.5 — 꼬리 질문은 내 생각을 읽는다). */
export function bandHint(p: NoteAiInput): string {
  if (p.rounds === 0 && !hasMaterial(p)) return '문장이나 생각을 적으면 질문을 받을 수 있어요';
  return '질문을 고르거나, 그냥 써도 돼요';
}

/** 마지막 라운드는 펼치고, 지난 라운드는 최근 것부터 하나로 접는다. */
export function sheetRounds(log: QuestionLog): { latest: Round | null; past: Round[] } {
  const n = log.rounds.length;
  return { latest: n > 0 ? log.rounds[n - 1] : null, past: log.rounds.slice(0, -1).reverse() };
}

export function roundTitle(step: Step): '여백에서 받은 질문' | '내 생각에서 받은 꼬리 질문' {
  return step === 'ASK' ? '여백에서 받은 질문' : '내 생각에서 받은 꼬리 질문';
}

/** 질문 띠 기다림 문구(F1 §4.5) — ASK는 쪽지를, FOLLOW는 내 생각을 읽는다. */
export function waitingText(step: Step | null): string | null {
  if (step === 'ASK') return '쪽지를 읽고 질문을 고르는 중이에요';
  if (step === 'FOLLOW') return '내 생각을 읽고 질문을 고르는 중이에요';
  return null;
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
