// 옛 에디토리얼 토큰(시각 보충 §2) — 앱 UI 개편 동안 안 옮긴 화면 · 부품만 읽는다(strangler · 계획 4절 D2 · REQ-471).
// 화면 PR이 자기 화면을 새 tokens.ts로 옮기며 legacy.test.ts의 LEGACY_IMPORTERS에서 자기 줄을 지운다. U9가 이 파일을 지운다.
// 값은 개편 전 tokens.ts 그대로다(옛 가드 REQ-49 · 51 · 52 · 53 · 55가 legacy.test.ts에서 지킨다).
// 밤은 위젯 다크 배경 desk 하나다 — 측정 중 밤 화면은 없앴다(범위 보충 REQ-107).
import type { TextStyle } from 'react-native';

// 옛 importer가 { color, space } · { color, cover, layout, space }처럼 함께 가져온다 — space는 같은 값, 새 layout은 옛 네 키의 상위 집합
export { layout, space } from './tokens';

export const color = {
  paper: '#F5F0E6',
  paper2: '#ECE5D8',
  ink: '#1D1B18',
  text: '#4A453E',
  muted: '#645E54',
  line: '#D9D1C3',
  onInk: '#F5F0E6',
  danger: '#A8322A',
  pressed: 'rgba(29, 27, 24, 0.06)',
} as const;

export const night = { desk: '#1C1A17' } as const;

/** Noto Serif KR Medium의 PostScript 이름 = useFonts 키(§6.3). 등록 전·실패 때는 RN이 시스템 글꼴로 그린다. */
export const SERIF = 'NotoSerifKR-Medium';

export type Stage = 'D1' | 'M1' | 'D2' | 'H1' | 'H2' | 'H3' | 'Q1' | 'Q2' | 'E1' | 'B1' | 'B2' | 'BT' | 'L1' | 'L2' | 'T1' | 'T2';

const serif = (fontSize: number, lineHeight: number, letterSpacing = 0): TextStyle => ({
  fontFamily: SERIF,
  fontSize,
  lineHeight,
  letterSpacing,
});
const gothic = (fontWeight: '400' | '500', fontSize: number, lineHeight: number): TextStyle => ({
  fontWeight,
  fontSize,
  lineHeight,
});

/** 글자 단계(§2.2). maxScale = 큰 글자 상한 — 이미 32pt 이상인 표시 3단계만 2.0 아래(§4.1). */
export const type: Record<Stage, { style: TextStyle; maxScale: number }> = {
  D1: { style: serif(84, 88, -1.7), maxScale: 1 },
  M1: { style: serif(44, 52, -0.4), maxScale: 1.2 },
  D2: { style: serif(32, 40, -0.3), maxScale: 1.3 },
  H1: { style: serif(20, 28), maxScale: 2 },
  H2: { style: serif(22, 30), maxScale: 2 },
  H3: { style: serif(18, 26), maxScale: 2 },
  Q1: { style: serif(16, 26), maxScale: 2 },
  Q2: { style: serif(14, 22), maxScale: 2 },
  E1: { style: gothic('400', 17, 26), maxScale: 2 },
  B1: { style: gothic('500', 16, 24), maxScale: 2 },
  B2: { style: gothic('400', 16, 24), maxScale: 2 },
  BT: { style: gothic('500', 16, 22), maxScale: 2 },
  L1: { style: gothic('400', 14, 20), maxScale: 2 },
  L2: { style: gothic('500', 14, 20), maxScale: 2 },
  T1: { style: gothic('500', 13, 18), maxScale: 2 },
  T2: { style: gothic('400', 13, 18), maxScale: 2 },
};

/** 굵은 텍스트가 켜지면 18pt 미만 명조(Q1·Q2)만 시스템 고딕 600으로 — 명조는 한 face라 굵어지지 않는다(§4.1). */
export function stageStyle(v: Stage, o: { bold: boolean }): TextStyle {
  const style = type[v].style;
  if (!o.bold || (v !== 'Q1' && v !== 'Q2')) return style;
  const { fontFamily: _serif, ...rest } = style;
  return { ...rest, fontWeight: '600' };
}

export const radius = { none: 0, hair: 1, sm: 2, pill: 999 } as const;
export const line = { thin: 1, bold: 2 } as const;

export const cover = {
  palette: [
    '#7A2E28', '#233A57', '#2F4B3A', '#5B3A55', '#2C5C5E', '#A0522D',
    '#3E4A56', '#5A4632', '#4F5A2E', '#6B2C3E', '#2E3F66', '#3F5B45',
  ],
  fg: '#F5F0E6',
  row: { width: 36, height: 50 },
  card: { width: 64, height: 90 },
  head: { width: 80, height: 112 },
} as const;
