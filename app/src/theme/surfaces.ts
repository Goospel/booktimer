// 그림자 층 · 가장자리 페이드 · 천 결(언어 1.6 · 1.7-26 · 1.7.1 값 그대로) — RN 0.88 boxShadow · backgroundImage 문자열.
// rgba · hex 문자열이라 theme/ 안에만 둔다(REQ-54) — 부품 · 화면은 이 함수를 부르고 색 글자를 쓰지 않는다. 유리는 시스템 층이라 없다(REQ-476).
// RN은 repeating-linear-gradient를 받지 않아 결은 한 주기 linear 타일 + backgroundSize · backgroundRepeat로 쓴다(계획 2.4 · 9절).
// 렌더가 평면이면(문자열이 안 먹으면) 기기 D1 U-267에서 shadow* 한 겹으로 물러난다.
import { palette, type Scheme } from './tokens';

export type ShadowLayer = 'housing' | 'fill' | 'key' | 'pressed' | 'cover' | 'lamp';

const SHADOW: Record<Scheme, Record<ShadowLayer, string>> = {
  light: {
    // 1 하우징 · 로그인 각인 판
    housing:
      'inset 0 1px 0 rgba(255,255,255,.95), 0 1px 1px rgba(17,19,21,.04), 0 2px 6px rgba(17,19,21,.04), 0 16px 32px -20px rgba(17,19,21,.25)',
    fill: 'inset 0 0.5px 0 rgba(255,255,255,.18), 0 1px 2px rgba(17,19,21,.3)', // 2 채움 키
    key: 'inset 0 1px 0 #FFFFFF, 0 1px 1px rgba(17,19,21,.06)', // 2′ 보조 키 · 분할 키 고르지 않은 칸
    pressed: 'inset 0 1px 2px rgba(17,19,21,.10)', // 2″ 분할 키 고른 칸 · 입력 칸(오목)
    cover: '0 0.5px 0 rgba(0,0,0,.22), 0 1px 2px rgba(17,19,21,.16), 0 8px 14px -8px rgba(17,19,21,.5), inset 0 0 0 0.5px rgba(0,0,0,.2)',
    lamp: '0 0 0 3px rgba(194,86,0,.22)', // 램프 켜짐 고리(signal 22%)
  },
  dark: {
    housing: 'inset 0 1px 0 rgba(255,255,255,.05), 0 1px 1px rgba(0,0,0,.45), 0 18px 32px -20px rgba(0,0,0,.85)',
    fill: 'inset 0 0.5px 0 rgba(255,255,255,.6), 0 1px 2px rgba(0,0,0,.4)',
    key: 'inset 0 1px 0 rgba(255,255,255,.05), 0 1px 1px rgba(0,0,0,.5)',
    pressed: 'inset 0 1px 2px rgba(0,0,0,.5)',
    cover: '0 0 0 0.5px rgba(255,255,255,.05), 0 1px 2px rgba(0,0,0,.5), 0 8px 14px -8px rgba(0,0,0,.9), inset 0 0 0 0.5px rgba(0,0,0,.2)',
    lamp: '0 0 0 3px rgba(241,131,54,.22)',
  },
};

export function shadow(scheme: Scheme, layer: ShadowLayer): string {
  return SHADOW[scheme][layer];
}

/** top = 위 71(47까지 꽉) · nav = 내비 가림 115(91까지 꽉 — opacity는 부품이) · bottom = 아래 120(96부터 꽉 — 쌓는 화면만, 탭 화면은 시스템)
 *  · left · right = 테이프 양 끝 56(하우징 면 bg2 → 투명). 높이 · 폭은 부품이 그 값으로 둔다(1.7-26 · 1.7-7) */
export type FadeSide = 'top' | 'nav' | 'bottom' | 'left' | 'right';

export function fade(scheme: Scheme, side: FadeSide): string {
  const { bg, bg2 } = palette[scheme];
  // 투명 끝은 같은 색의 알파 0 — 'transparent'(검정 투명)로 가면 가운데가 탁해진다
  switch (side) {
    case 'top':
      return `linear-gradient(to bottom, ${bg} 0px, ${bg} 47px, ${bg}00 71px)`;
    case 'nav':
      return `linear-gradient(to bottom, ${bg} 0px, ${bg} 91px, ${bg}00 115px)`;
    case 'bottom':
      return `linear-gradient(to bottom, ${bg}00 0px, ${bg} 96px, ${bg} 120px)`;
    case 'left':
      return `linear-gradient(to right, ${bg2}, ${bg2}00)`;
    case 'right':
      return `linear-gradient(to left, ${bg2}, ${bg2}00)`;
  }
}

const LIGHT_160 = 'linear-gradient(160deg, rgba(255,255,255,.12), rgba(255,255,255,0) 42%, rgba(0,0,0,.16))';

/** 천 표지 결(모드 무관 — 표지 색 위 덧칠). 가로 1px 흰 3.5% 2px 주기 + 세로 1px 검정 7% 3px 주기 + 160° 빛.
 *  S(폭 44)는 세로 결 없이 가로 0.5px 흰 2%만(개정 3 AD2 — 주기가 고정값이라 작은 표지에서 주사선처럼 거칠었다) */
export function grain(size: 'S' | 'M' | 'L' | 'XL' | 'XXL') {
  if (size === 'S') {
    return {
      backgroundImage: `linear-gradient(to top, rgba(255,255,255,.02) 0px, rgba(255,255,255,.02) 0.5px, rgba(255,255,255,0) 0.5px, rgba(255,255,255,0) 2px), ${LIGHT_160}`,
      backgroundSize: '100% 2px, 100% 100%',
      backgroundRepeat: 'repeat, no-repeat',
    };
  }
  return {
    backgroundImage:
      'linear-gradient(to top, rgba(255,255,255,.035) 0px, rgba(255,255,255,.035) 1px, rgba(255,255,255,0) 1px, rgba(255,255,255,0) 2px), ' +
      `linear-gradient(to right, rgba(0,0,0,.07) 0px, rgba(0,0,0,.07) 1px, rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px), ${LIGHT_160}`,
    backgroundSize: '100% 2px, 3px 100%, 100% 100%',
    backgroundRepeat: 'repeat, repeat, no-repeat',
  };
}

/** 표지 왼쪽 등 그림자(폭 = 표지 폭 × 0.117 — 부품이 둔다) */
export const spine = 'linear-gradient(to right, rgba(0,0,0,.30), rgba(0,0,0,.08) 55%, rgba(255,255,255,.13) 78%, rgba(0,0,0,.14) 100%)';
