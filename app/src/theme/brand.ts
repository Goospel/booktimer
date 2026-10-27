// 각 사 공식 값(앱 토큰 밖 예외 — 범위 보충 :144 · 설계 §5 브랜드 값). REQ-54 가드는 theme/ 밖의 날 색만 막는다.
export const brand = {
  radius: 12, // 카카오 가이드 글 값 — F-A 안A 확정(2026-10-26)이라 Apple · Google도 12
  google: { fill: '#FFFFFF', stroke: '#747775', text: '#1F1F1F' },
  kakao: { fill: '#FEE500', stroke: null, text: 'rgba(0, 0, 0, 0.85)' },
} as const;
