import { palette } from './tokens';
import { fade, grain, shadow, type FadeSide, type ShadowLayer } from './surfaces';

// 앱 UI 개편 U2a — 그림자 · 가장자리 페이드 · 천 결 문자열(언어 1.6 · 1.7-26 · 1.7.1). 렌더(겹 · inset이 실제로 보이는가)는 기기 D1 U-267.
// 여기서는 렌더와 무관하게 틀릴 수 있는 둘만 본다 — 모드를 잘못 고른 바탕색 · 겹 수와 크기 목록이 어긋난 결 타일.

const LAYERS: ShadowLayer[] = ['housing', 'fill', 'key', 'pressed', 'cover', 'lamp'];
const SIDES: FadeSide[] = ['top', 'nav', 'bottom', 'left', 'right'];

test('가장자리 페이드는 그 모드의 바탕색으로 만든다 — 위 · 내비 가림 · 아래 = bg, 테이프 양 끝 = bg2(다른 모드 색이 섞이면 잡힌다)', () => {
  for (const s of ['light', 'dark'] as const) {
    const other = palette[s === 'light' ? 'dark' : 'light'];
    for (const side of SIDES) {
      const tone = side === 'left' || side === 'right' ? palette[s].bg2 : palette[s].bg;
      const g = fade(s, side);
      expect(g).toContain(tone);
      expect(g).toContain(`${tone}00`); // 같은 색의 투명 끝 — 'transparent'(검정 투명)로 가면 가운데가 회색으로 탁해진다
      expect(g).not.toContain(other.bg);
      expect(g).not.toContain(other.bg2);
    }
  }
});

test('그림자 층은 두 모드 모두 있고 서로 다르다(언어 1.6)', () => {
  for (const layer of LAYERS) {
    expect(shadow('light', layer)).toMatch(/px/);
    expect(shadow('dark', layer)).toMatch(/px/);
    expect(shadow('light', layer)).not.toBe(shadow('dark', layer));
  }
});

test('천 결은 겹 수 = backgroundSize 수 = backgroundRepeat 수 — S는 결 하나를 덜 쓴다(1.7.1 개정 3 AD2)', () => {
  const count = (g: ReturnType<typeof grain>) => [
    g.backgroundImage.split('linear-gradient(').length - 1,
    g.backgroundSize.split(',').length,
    g.backgroundRepeat.split(',').length,
  ];
  expect(count(grain('M'))).toEqual([3, 3, 3]);
  expect(count(grain('S'))).toEqual([2, 2, 2]);
});
