// 홈-안A / 홈-안B(시각 보충 §1.4-2 · 앞당김 보충 §6.4). 이 상수 하나만 바꿔 미리보기를 두 번 게시하고,
// 사용자가 고르면 고른 쪽만 남기고 다른 안 갈래는 지운다.
// A(추천) = 쓰기 쪽(여백 남기기)이 검정 알약 · 읽기 시작이 외곽선. B = 읽기 시작이 검정 알약 · 이어 쓰기를 크게.
export type HomeVariant = 'A' | 'B';

export const HOME_VARIANT: HomeVariant = 'A';

export function homeButtons(v: HomeVariant) {
  return v === 'A'
    ? ({ write: 'ink', start: 'outline', writingStage: 'H3' } as const)
    : ({ write: 'outline', start: 'ink', writingStage: 'H2' } as const);
}
