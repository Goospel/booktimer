// 인용만 있는 쪽지의 띠(책생글 「여백 + 블로그」 C2 · REQ-225) — 쪽지 화면 안(생각 입력칸 위)에 「이 문장에서 무엇이 남았나요? · 질문 받기」.
// 누를 때만 ASK를 부른다(화면 몫). 보이는 조건은 [질문 받기]와 같은 함수(aiButton)다 — 11/23 전 · 몫 소진도 그 화면 규칙 그대로.
import { aiButton } from './aiStep';
import { showsThought } from './marginSave';

/**
 * 생각이 비었거나 문장부호뿐(서버 NO_THOUGHT와 같은 정의 — showsThought)이고 옮긴 문장이 있으며 [질문 받기]가 켜진 쪽지.
 * ponytail: 건 블로그 문장(B1)은 아직 없다 — B1이 박스 수를 이 입력에 더한다.
 */
export function quoteOnlyBand(p: { quote: string | null; thought: string; rounds: number; aiIncluded: number; hasBook: boolean }): boolean {
  if (showsThought(p.thought) || (p.quote ?? '').trim() === '') return false;
  const ask = aiButton({ rounds: p.rounds, body: p.thought, aiIncluded: p.aiIncluded, hasBook: p.hasBook });
  return ask.step === 'ASK' && !ask.disabled;
}
