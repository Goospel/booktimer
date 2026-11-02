// 글쓰기 AI 한 번(v1 REQ-36 · 47 · 62 · PR-H 사양 §5.4 · REQ-138 · 140).
// 순서: 입력 확정(blur) → 다음 틱 → 자동저장 비우기 → 그 revision으로 POST. 응답을 못 받으면 글을 다시 읽어 AI 필드만 줍는다.
import { ApiError, NetworkError } from '../api/client';
import type { AiView, MarginView, QuestionLog, Step } from '../api/types';
import { showsThought } from './marginSave';

export type { Step };

export type AiOutcome =
  | { kind: 'done'; questions: QuestionLog; aiUpdatedAt: string; aiRemainingToday: number } // 본문 없음(REQ-47)
  | { kind: 'consent' } // /ai 403 — 동의 시트를 연다(REQ-140)
  | { kind: 'retry'; message: string } // 안전망 끝
  | { kind: 'error'; message: string };

/** 끊긴 뒤 글을 다시 읽기 전에 기다리는 시간(v1 §4.4 — 셋 다 그대로면 「다시 시도」). */
export const SAFETY_NET_MS = [5_000, 15_000, 30_000] as const;

/** 한 글이 받을 수 있는 라운드 · FOLLOW 최소 본문 — 서버 WritingAiService ROUNDS_MAX · FOLLOW_MIN_BODY와 같다. */
export const ROUNDS_MAX = 8;
export const FOLLOW_MIN_BODY = 30;

const NET = '인터넷에 연결되지 않았어요 — 쓴 쪽지는 이 기기에 남아 있어요. 연결되면 다시 눌러 주세요';
const NOT_YET = '질문을 아직 받지 못했어요';
const SAVE_FAIL = '쪽지가 아직 저장되지 않아 질문을 보내지 못했어요 — 잠시 뒤 다시 눌러 주세요';
const GENERIC = '질문을 보내지 못했어요 — 잠시 뒤 다시 눌러 주세요';

/** 한 번에 하나 — 돌고 있는 동안 또 부르면 아무것도 하지 않는다(새 쪽지 첫 POST 중 두 번 누르면 AI 요청이 두 번 나간다 — A1 리뷰 13). */
export function singleFlight() {
  let busy = false;
  return async (f: () => Promise<void>): Promise<void> => {
    if (busy) return;
    busy = true;
    try {
      await f();
    } finally {
      busy = false;
    }
  };
}

const nextTick = () => new Promise<void>((r) => setTimeout(r, 0));
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/** 서버 평문 > 연결 문구 > (저장 중이었으면) 저장 실패 > 일반 — 「요청에 실패했어요 (N)」은 서버 문구로 치지 않는다. */
export function messageFor(e: unknown, ctx: 'save' | 'send'): string {
  if (e instanceof ApiError && !/^요청에 실패했어요 \(\d+\)$/.test(e.message)) return e.message;
  if (e instanceof NetworkError) return NET;
  return ctx === 'save' ? SAVE_FAIL : GENERIC;
}

const done = (v: { questions: QuestionLog; aiUpdatedAt: string; aiRemainingToday: number }): AiOutcome => ({
  kind: 'done',
  questions: v.questions,
  aiUpdatedAt: v.aiUpdatedAt,
  aiRemainingToday: v.aiRemainingToday,
});

export async function sendAiStep(p: {
  blur: () => void;
  flush: () => Promise<{ revision: number }>;
  post: (revision: number) => Promise<AiView>;
  reload: () => Promise<Pick<MarginView, 'questions' | 'aiUpdatedAt' | 'aiRemainingToday'>>;
  /** 보내기 전 라운드 수 — 서버는 라운드를 덧붙이기만 한다. 시각 문자열 비교는 서버 정밀도(나노초 응답 vs datetime(6))에 깨진다 */
  roundsBefore: number;
  wait?: (ms: number) => Promise<void>;
}): Promise<AiOutcome> {
  const wait = p.wait ?? sleep;
  p.blur();
  await nextTick(); // 조합 중이던 마지막 음절이 확정된 뒤의 값(U-05a)

  const flushThenPost = async (): Promise<AiView | AiOutcome> => {
    let revision: number;
    try {
      revision = (await p.flush()).revision;
    } catch (e) {
      return { kind: 'error', message: messageFor(e, 'save') };
    }
    return p.post(revision);
  };

  try {
    let r: AiView | AiOutcome;
    try {
      r = await flushThenPost();
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 409)) throw e;
      r = await flushThenPost(); // 판이 달랐다 — 저장을 한 번 더 비우고 한 번만 다시
    }
    return 'kind' in r ? r : done(r);
  } catch (e) {
    if (e instanceof ApiError && e.status === 403) return { kind: 'consent' };
    if (!(e instanceof NetworkError)) return { kind: 'error', message: messageFor(e, 'send') };
  }

  // 끊김 안전망 — 서버는 끊긴 요청도 끝까지 처리해 AI 열만 저장한다. 다시 읽은 글에서 AI 필드만 낸다(REQ-47).
  let reached = false;
  for (const ms of SAFETY_NET_MS) {
    await wait(ms);
    try {
      const d = await p.reload();
      reached = true;
      if (d.aiUpdatedAt !== null && d.questions.rounds.length > p.roundsBefore) return done({ ...d, aiUpdatedAt: d.aiUpdatedAt });
    } catch {
      // 다시 읽기도 못 했다 — 다음 차례에 한 번 더
    }
  }
  return reached ? { kind: 'retry', message: NOT_YET } : { kind: 'error', message: NET };
}

/** 쪽지 AI 단추 입력 — 지금 쪽지만(J98). boxes = 재료로 가는 건 블로그 문장 박스 수(B1부터 · 가린 박스 제외 — A1은 0). */
export interface NoteAiInput {
  rounds: number;
  quote: string | null;
  thought: string;
  boxes: number;
}

/** 지금 쪽지에 읽을 것이 있다(J98) — 옮긴 문장 ∨ 생각(showsThought — 서버 NO_THOUGHT) ∨ 건 문장 박스. 책 유무 · 다른 쪽지 수는 보지 않는다. */
export const hasMaterial = (p: Omit<NoteAiInput, 'rounds'>) => (p.quote ?? '').trim() !== '' || showsThought(p.thought) || p.boxes > 0;

/** 아래 막대 검정 알약 — busy는 화면이 얹는다. 꼬리 질문 30자는 서버 strip()처럼 앞뒤 공백을 뺀 길이. */
export function aiButton(p: NoteAiInput): {
  step: Step;
  label: '질문 받기' | '꼬리 질문';
  disabled: boolean;
} {
  const full = p.rounds >= ROUNDS_MAX;
  if (p.rounds === 0) return { step: 'ASK', label: '질문 받기', disabled: full || !hasMaterial(p) };
  return { step: 'FOLLOW', label: '꼬리 질문', disabled: full || p.thought.trim().length < FOLLOW_MIN_BODY };
}
