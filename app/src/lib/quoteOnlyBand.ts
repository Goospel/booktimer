// 인용만 있는 쪽지의 띠(책생글 「여백 + 블로그」 C2 · REQ-225) — 쪽지 화면 안(생각 입력칸 위)에 「이 문장에서 무엇이 남았나요? · 질문 받기」.
// 누를 때만 ASK를 부른다(화면 몫). 보이는 조건은 [질문 받기]와 같은 함수(aiButton)다 — 11/23 전 · 몫 소진도 그 화면 규칙 그대로.
import { aiButton, type NoteAiInput } from './aiStep';
import { showsThought } from './marginSave';

/**
 * 생각이 비었거나 문장부호뿐(서버 NO_THOUGHT와 같은 정의 — showsThought)이고 옮긴 문장이나 건 블로그 문장 박스가 있으며
 * [질문 받기]가 켜진 쪽지(J98 — 책 유무 · 같은 책 다른 쪽지 수와 무관). boxes는 B1이 채운다(A1은 0).
 */
export function quoteOnlyBand(p: NoteAiInput): boolean {
  if (showsThought(p.thought) || ((p.quote ?? '').trim() === '' && p.boxes === 0)) return false;
  const ask = aiButton(p);
  return ask.step === 'ASK' && !ask.disabled;
}
