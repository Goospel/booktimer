// 앱 UI 개편 토큰(언어 확정본 1.2 · 1.3 · 1.4 · 1.5 — 회색 금속 + 신호 주황 한 점). 옛 에디토리얼 토큰은 legacy.ts(안 옮긴 화면만 — U9까지).
// 색은 DynamicColorIOS — 정적 StyleSheet가 모드를 그대로 따라간다(계획 D4). 문자열이 필요한 곳(그라데이션 · 그림자 · 계산 · 대비 테스트)만 palette[scheme].
// 대비 증가 칸(highContrastLight/Dark)은 개편 뒤 작은 PR(사용자 결정 16절 질문 6 (가)).
import { DynamicColorIOS, type TextStyle } from 'react-native';

// 라이트 mark = #7A7F85(사용자 결정 2026-11-20 질문 5 (가)). app.config.ts의 BG_LIGHT · BG_DARK가 bg와 같다(app-config.test.ts가 대조)
export const palette = {
  light: {
    // 1.2.1 기본
    bg: '#E7E9EB', bg2: '#F4F5F6', ink: '#111315', text2: '#565B61', mark: '#7A7F85',
    line: '#CBCFD3', tick1: '#B9B7B0', signal: '#C25600', danger: '#9E1B32',
    // 1.2.2 파생 — 채움 키 글자 · 램프 꺼짐 · 출처 통과 · 주의 · 문장 강조 · 유리(시스템 층 근사 — 앱 콘텐츠는 그리지 않는다, REQ-476) · 눌림 덮개
    onFill: '#F4F5F6', lampOff: '#8C9196', pass: '#2A7449', warn: '#8A6E08', highlight: '#E1D1C5',
    glass: 'rgba(231,233,235,.55)', glassLine: 'rgba(255,255,255,.5)', pressFill: 'rgba(0,0,0,.14)', pressInk: 'rgba(17,19,21,.06)',
  },
  dark: {
    bg: '#0E0F10', bg2: '#18191B', ink: '#ECEBE6', text2: '#A3A099', mark: '#6F6C66',
    line: '#2A2C2F', tick1: '#4A4844', signal: '#F18336', danger: '#FF6B7A',
    onFill: '#0F0F0E', lampOff: '#8A877F', pass: '#74C692', warn: '#D9BB66', highlight: '#402918',
    glass: 'rgba(14,15,16,.55)', glassLine: 'rgba(255,255,255,.12)', pressFill: 'rgba(0,0,0,.14)', pressInk: 'rgba(236,235,230,.06)',
  },
} as const;

export type Scheme = keyof typeof palette;
type Tone = keyof typeof palette.light;
export const color = Object.fromEntries(
  (Object.keys(palette.light) as Tone[]).map((k) => [k, DynamicColorIOS({ light: palette.light[k], dark: palette.dark[k] })]),
) as Record<Tone, ReturnType<typeof DynamicColorIOS>>;

/** Plex 넷의 PostScript 이름(OTF name 표 nameID 6 — 파일 이름과 다르다: Medm · SmBld · Regular는 접미 없음) — expo-font 플러그인이
 *  바이너리에 싣는다(app.config.ts). 굵기는 이 이름으로만 고른다(REQ-470). assets.test.ts가 OTF에서 읽어 대조한다. */
export const font = {
  sans400: 'IBMPlexSansKR',
  sans500: 'IBMPlexSansKR-Medm',
  sans600: 'IBMPlexSansKR-SmBld',
  mono500: 'IBMPlexMono-Medm',
} as const;

// 단계 style엔 fontWeight를 두지 않는다 — 굵기 = face(fontFamily). 같이 두면 RN이 그 패밀리에서 가까운 굵기를 다시 고른다(RCTFontUtils.mm:419 · 426-441)
export type Stage =
  | 'display' | 'title' | 'housing' | 'row' | 'question' | 'body' | 'quote' | 'preview'
  | 'aux' | 'key' | 'foot' | 'readout' | 'bigClock' | 'gauge' | 'tick';

const st = (fontFamily: string, fontSize: number, lineHeight: number, maxScale: number, letterSpacing?: number) => ({
  style: { fontFamily, fontSize, lineHeight, ...(letterSpacing === undefined ? {} : { letterSpacing }) } as TextStyle,
  maxScale,
});

/** 글자 단계(언어 1.3.2). maxScale = 큰 글자 상한. 굵기 갈래(보조 500 · 600 · 각주 500 · 키 600)는 Txt의 w 칸(U2b)이 고른다 */
export const type: Record<Stage, { style: TextStyle; maxScale: number }> = {
  display: st(font.sans600, 34, 41, 1.3, -0.8), // 큰 제목
  title: st(font.sans600, 22, 30, 2, -0.45), // 제목
  housing: st(font.sans600, 20, 28, 2, -0.45), // 하우징 제목
  row: st(font.sans500, 17, 24, 2, -0.3), // 행 제목
  question: st(font.sans500, 17, 26, 2), // 질문
  body: st(font.sans400, 17, 28, 2), // 본문
  quote: st(font.sans400, 15, 22, 2), // 옮긴 문장
  preview: st(font.sans400, 17, 26, 2), // 쪽지 미리보기 생각
  aux: st(font.sans400, 15, 22, 2), // 보조
  key: st(font.sans500, 15, 20, 2), // 키
  foot: st(font.sans400, 13, 18, 2), // 각주
  readout: st(font.mono500, 48, 52, 1, -1), // 판독값
  bigClock: st(font.mono500, 104, 104, 1, -2.5), // 큰 시계
  gauge: st(font.mono500, 17, 20, 1.3), // 계기 숫자
  tick: st(font.mono500, 13, 16, 2), // 눈금 숫자
};

// 굵은 텍스트 = 한 단 굵게 — face를 바꾼다(Plex 700은 싣지 않아 600 그대로 · Mono 500 그대로)
const BOLDER: Record<string, string> = { [font.sans400]: font.sans500, [font.sans500]: font.sans600 };

const SANS = { 400: font.sans400, 500: font.sans500, 600: font.sans600 } as const;
export type Weight = keyof typeof SANS;

/** w = 단계 안 굵기 갈래(Sans 단계만 — Mono는 500 하나). 굵은 텍스트(시스템 설정)가 켜지면 다음 face로 — fontWeight를 더하지 않는다(REQ-433 · 470) */
export function stageStyle(v: Stage, o: { bold: boolean; w?: Weight }): TextStyle {
  const style = type[v].style;
  const face = o.w && style.fontFamily !== font.mono500 ? SANS[o.w] : (style.fontFamily as string);
  const next = (o.bold && BOLDER[face]) || face;
  return next === style.fontFamily ? style : { ...style, fontFamily: next };
}

export const radius = { s: 2, m: 8, l: 12, round: 999 } as const; // 캡슐 · 47 · 38 · 24 · 22 · 5는 시스템 몫이라 토큰에 없다(언어 1.5)
export const space = { 1: 4, 2: 8, 3: 12, 4: 16, 5: 20, 6: 24, 8: 32, 12: 48 } as const;
// readMax = iPad 본문 한 단(REQ-104) · keyFull = 폭 가득 키 높이 · studyTwoCol = 공부 iPad 두 칸 문턱(REQ-457)
export const layout = { gutter: 20, sectionGap: 32, hit: 44, keyFull: 48, readMax: 640, studyTwoCol: 1144 } as const;

export const cover = {
  palette: [
    '#7A2E28', '#233A57', '#2F4B3A', '#5B3A55', '#2C5C5E', '#A0522D',
    '#3E4A56', '#5A4632', '#4F5A2E', '#6B2C3E', '#2E3F66', '#3F5B45',
  ],
  fg: '#F5F0E6',
  // 크기 다섯(언어 1.7.1 — 모두 1 : 1.45)
  S: { width: 44, height: 64 },
  M: { width: 60, height: 87 },
  L: { width: 84, height: 122 },
  XL: { width: 120, height: 174 },
  XXL: { width: 160, height: 232 },
} as const;
