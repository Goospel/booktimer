import { coverColor, initialOf } from './cover';

// 시각 보충 §B T2(theme/에 둔다 — 회귀 벡터가 날 색이라 REQ-54 스캔 밖) — 천 표지는 웹 books/pure.ts와 같은 해시(제목 → 팔레트 12색). 값은 웹 해시로 계산한 회귀 벡터다.

test("REQ-50 · coverColor('데미안')은 #4F5A2E · coverColor('미움받을 용기')는 #2F4B3A · 빈 제목은 #7A2E28이다", () => {
  expect(coverColor('데미안')).toBe('#4F5A2E');
  expect(coverColor('미움받을 용기')).toBe('#2F4B3A');
  expect(coverColor('')).toBe('#7A2E28');
});

test('REQ-50 · 표지 이니셜은 앞뒤 공백을 뺀 첫 글자이고 빈 제목은 ?이다', () => {
  expect(initialOf('  데미안')).toBe('데');
  expect(initialOf('   ')).toBe('?');
});
