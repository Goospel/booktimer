// 인용만 있는 쪽지의 띠(책생글 「여백 + 블로그」 C2 · REQ-225) — 판정만. 띠는 쪽지 화면 안에 있고 누를 때만 ASK를 부른다(화면 몫).
import { aiButton } from './aiStep';
import { quoteOnlyBand } from './quoteOnlyBand';

const base = { quote: '새는 알에서 나오려고 투쟁한다.' as string | null, thought: '', rounds: 0, boxes: 0 };

test('REQ-225 · 생각이 비었거나 문장부호뿐 + 옮긴 문장 → 띠', () => {
  for (const thought of ['', '  \n', '.', '…', '?!']) expect([thought, quoteOnlyBand({ ...base, thought })]).toEqual([thought, true]);
});

test('REQ-225 · 생각이 있으면 띠 없음 · 옮긴 문장이 없어도 띠 없음', () => {
  for (const thought of ['ㅎ', '좋다.', '😊']) expect([thought, quoteOnlyBand({ ...base, thought })]).toEqual([thought, false]);
  expect(quoteOnlyBand({ ...base, quote: null })).toBe(false);
  expect(quoteOnlyBand({ ...base, quote: '   ' })).toBe(false);
});

// J98 — 책이 없거나 그 책의 첫 쪽지(같은 책 다른 쪽지 0)여도 지금 쪽지에 옮긴 문장 · 건 문장이 있으면 띠. 입력에 책 · 다른 쪽지 수가 없다.
test('REQ-225 · 건 블로그 문장 박스만 있어도 띠(B1부터) · 박스도 옮긴 문장도 없으면 띠 없음', () => {
  expect(quoteOnlyBand({ ...base, quote: null, boxes: 1 })).toBe(true);
  expect(quoteOnlyBand({ ...base, quote: null, boxes: 0 })).toBe(false);
});

test('REQ-225 · 띠 조건 = 질문 받기 조건(같은 함수) — 질문 받기가 꺼지거나 이미 받았으면 띠도 없다', () => {
  const cases = [
    base,
    { ...base, quote: null, boxes: 1 },
    { ...base, rounds: 1 }, // 받은 뒤엔 꼬리 질문 차례 — 「질문 받기」 띠가 아니다
    { ...base, rounds: 8 },
  ];
  for (const c of cases) {
    const ask = aiButton(c);
    expect([c, quoteOnlyBand(c)]).toEqual([c, ask.step === 'ASK' && !ask.disabled]);
  }
  expect(quoteOnlyBand({ ...base, rounds: 1 })).toBe(false); // 양성 대조 — 위 대조가 늘 같은 값이면 공허하다
  expect(quoteOnlyBand(base)).toBe(true);
});
