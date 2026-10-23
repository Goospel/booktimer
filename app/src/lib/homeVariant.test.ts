import { HOME_VARIANT, homeButtons } from './homeVariant';

// 시각 보충 §1.4-2 · 앞당김 보충 §6.4 — 홈-안A/B는 상수 하나로 바꾼다(미리보기 두 번 뒤 고른 쪽만 남긴다).

test('REQ-61 · 홈-안A는 쓰기가 검정 알약 · 읽기 시작이 외곽선이고 기본값이다', () => {
  expect(HOME_VARIANT).toBe('A');
  expect(homeButtons('A')).toEqual({ write: 'ink', start: 'outline', writingStage: 'H3' });
});

test('REQ-61 · 홈-안B는 읽기 시작이 검정 알약이고 이어 쓰기를 크게 둔다', () => {
  expect(homeButtons('B')).toEqual({ write: 'outline', start: 'ink', writingStage: 'H2' });
});
