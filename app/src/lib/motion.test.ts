import { springOf, transitionFor } from './motion';

// 언어 1.8 — response · ζ 쌍을 Reanimated withSpring(mass 1 · stiffness = (2π/response)² · damping = 4πζ/response)으로

test('REQ-448 · 스프링 표 — 누름 816 · 57.1 · 바늘 504 · 35.0 · 판 386 · 39.3 · 테이프 1218 · 69.8(소수 첫째 자리)', () => {
  const want: [Parameters<typeof springOf>[0], number, number][] = [
    ['press', 816, 57.1],
    ['needle', 504, 35.0],
    ['plate', 386, 39.3],
    ['tape', 1218, 69.8],
    ['detent', 273, 29.1],
    ['zoomOpen', 158, 23.1],
  ];
  for (const [name, stiffness, damping] of want) {
    const s = springOf(name);
    expect(Math.round(s.stiffness)).toBe(stiffness);
    expect(Math.round(s.damping * 10) / 10).toBe(damping);
    expect(s.mass).toBe(1);
  }
});

test('REQ-435 · 움직임 줄이기면 transform은 끝 값 · opacity는 150ms linear · 줄이기가 아니면 스프링', () => {
  expect(transitionFor('plate', { reduced: true, prop: 'transform' })).toEqual({ type: 'instant' });
  expect(transitionFor('plate', { reduced: true, prop: 'opacity' })).toEqual({ type: 'timing', duration: 150, easing: 'linear' });
  expect(transitionFor('needle', { reduced: false, prop: 'transform' })).toEqual({ type: 'spring', config: springOf('needle') });
  expect(transitionFor('plate', { reduced: false, prop: 'opacity' })).toEqual({ type: 'spring', config: springOf('plate') });
});
