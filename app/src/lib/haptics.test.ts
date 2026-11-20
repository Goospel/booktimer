import { kindOf, pickOne } from './haptics';

// 언어 1.9 햅틱 지도

test('1.9 · 순간 → 세기 지도(측정 Rigid · 다이얼 새 칸 selection · 목표 달성 success · 질문 도착 Light · 디텐트 · 질문 고르기 · 목표 다이얼 selection)', () => {
  expect(kindOf('measure')).toBe('rigid');
  expect(kindOf('dialSegment')).toBe('selection');
  expect(kindOf('goalMet')).toBe('success');
  expect(kindOf('questionArrive')).toBe('light');
  expect(kindOf('detent')).toBe('selection');
  expect(kindOf('questionPick')).toBe('selection');
  expect(kindOf('goalDial')).toBe('selection');
});

test('1.9 · 한 순간 하나 — 목표 달성이 같은 순간의 selection을 덮는다 · 빈 순간이면 null', () => {
  expect(pickOne(['dialSegment', 'goalMet'])).toBe('goalMet');
  expect(pickOne(['goalMet', 'dialSegment'])).toBe('goalMet');
  expect(pickOne(['dialSegment'])).toBe('dialSegment');
  expect(pickOne([])).toBeNull();
});
