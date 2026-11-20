import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';
import { DynamicColorIOS } from 'react-native';

import { color, font, layout, palette, radius, space, stageStyle, type, type Stage } from './tokens';

// 앱 UI 개편 U2a — 새 토큰 불변식(언어 확정본 1.2 · 1.3 · 1.5 · REQ-431 · 432 · 433 · 452 · 470)과 날 색 가드(REQ-54 — 그대로).
// 옛 토큰의 옛 가드는 legacy.test.ts(U9까지).

const APP_SRC = join(__dirname, '..');

// ── 색 토큰(REQ-431) ─────────────────────────────────────────────────────
type Tone = keyof typeof palette.light;
const TONES = Object.keys(palette.light) as Tone[];
const VALUE = /^#[0-9A-F]{6}$|^rgba\(\d{1,3},\d{1,3},\d{1,3},0?\.\d+\)$/;

test('REQ-431 · 색 토큰은 라이트 · 다크 두 값을 가진다(palette 두 판의 키 집합이 같고 모든 값이 #RRGGBB 또는 rgba)', () => {
  expect(Object.keys(palette.dark).sort()).toEqual([...TONES].sort());
  for (const scheme of ['light', 'dark'] as const) for (const k of TONES) expect(palette[scheme][k]).toMatch(VALUE);
  // color는 같은 키의 동적 색이다 — 시스템이 모드에 따라 바꾼다(정적 StyleSheet가 그대로 따라간다 — 계획 D4)
  expect(Object.keys(color).sort()).toEqual([...TONES].sort());
  for (const k of TONES) expect(color[k]).toEqual(DynamicColorIOS({ light: palette.light[k], dark: palette.dark[k] }));
  expect(color.bg).not.toEqual(color.bg2); // 객체끼리라 키 하나를 다른 값과 바꾼 것도 toEqual이 가른다
});

// ── 대비(REQ-432 — WCAG 2.x 상대휘도) ──────────────────────────────────────
function luminance(hex: string): number {
  const n = hex.replace('#', '');
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(n.slice(i, i + 2), 16) / 255);
  const lin = (c: number) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}
function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}
const round2 = (x: number) => Math.round(x * 100) / 100;

type Pal = Record<Tone, string>;
/** 글자로 쓰는 쌍(1.2.1 「글자에」 칸이 「된다」인 셋 + 채움 키 위 글자) — 4.5 이상 */
const TEXT_PAIRS: [Tone, Tone][] = [
  ['ink', 'bg'], ['ink', 'bg2'], ['text2', 'bg'], ['text2', 'bg2'], ['danger', 'bg'], ['danger', 'bg2'], ['onFill', 'ink'],
];
/** 그래픽 표식(뜻을 지는 그림) — 3 이상. 장식(line · tick1)은 어느 목록에도 없다(1.3.3 — 3 미만 허용) */
const GRAPHIC_PAIRS: [Tone, Tone][] = [['mark', 'bg'], ['mark', 'bg2'], ['signal', 'bg'], ['signal', 'bg2']];
/** 실제 판과 심은 판이 같이 지나는 판정 — 기준 미달 쌍 이름 */
const below = (p: Pal, pairs: [Tone, Tone][], min: number) =>
  pairs.filter(([fg, bg]) => contrast(p[fg], p[bg]) < min).map(([fg, bg]) => `${fg}/${bg}`);

describe('대비(REQ-432)', () => {
  test('REQ-432 · 대비 계산기는 검정/흰색 21.00 · #6F6A5E/#FCFAF5 5.16 · #9A9080/#322B22 4.44를 낸다', () => {
    expect(round2(contrast('#000000', '#FFFFFF'))).toBe(21);
    expect(round2(contrast('#6F6A5E', '#FCFAF5'))).toBe(5.16);
    expect(round2(contrast('#9A9080', '#322B22'))).toBe(4.44); // 음성 대조 — 4.5 미만
  });

  test('REQ-432 · 글자 쌍은 라이트 · 다크 모두 4.5 이상 — ink · text2 · danger × bg · bg2, onFill × ink', () => {
    expect(below(palette.light, TEXT_PAIRS, 4.5)).toEqual([]);
    expect(below(palette.dark, TEXT_PAIRS, 4.5)).toEqual([]);
  });

  test('REQ-432 · 그래픽 표식은 3 이상 — mark · signal × bg · bg2 · 장식(line · tick1)은 글자 쌍 목록에 없다', () => {
    expect(below(palette.light, GRAPHIC_PAIRS, 3)).toEqual([]);
    expect(below(palette.dark, GRAPHIC_PAIRS, 3)).toEqual([]);
    const textTones = new Set(TEXT_PAIRS.map(([fg]) => fg));
    for (const t of ['mark', 'signal', 'line', 'tick1'] as Tone[]) expect(textTones.has(t)).toBe(false);
  });

  test('REQ-432 · 양성 대조 — text2를 #6B7076으로 낮춘 판은 text2 × bg 쌍에서 잡힌다(4.10 — bg2 쌍은 4.58이라 지나는 것이 맞다)', () => {
    const planted: Pal = { ...palette.light, text2: '#6B7076' };
    expect(round2(contrast(planted.text2, planted.bg))).toBe(4.1);
    expect(below(planted, TEXT_PAIRS, 4.5)).toEqual(['text2/bg']);
    // 옛 라이트 mark(#8C9196)는 그래픽 3에 못 미친다 — 사용자 결정 질문 5 (가)가 #7A7F85인 이유
    expect(below({ ...palette.light, mark: '#8C9196' }, GRAPHIC_PAIRS, 3)).toEqual(['mark/bg', 'mark/bg2']);
  });
});

// ── 글자 단계(REQ-433) ───────────────────────────────────────────────────
const STAGES = Object.keys(type) as Stage[];
const FACES = Object.values(font) as string[];

describe('글자 단계(REQ-433)', () => {
  test('REQ-433 · 글자 단계는 13pt 이상 · fontFamily는 font 넷 중 하나 · style에 fontWeight 키 0 · 큰 글자 상한은 언어 1.3.2 표', () => {
    expect(STAGES).toHaveLength(15);
    const maxScale: Partial<Record<Stage, number>> = { readout: 1, bigClock: 1, display: 1.3, gauge: 1.3 };
    for (const s of STAGES) {
      const { style } = type[s];
      expect(style.fontSize).toBeGreaterThanOrEqual(13);
      expect(FACES).toContain(style.fontFamily);
      expect(style).not.toHaveProperty('fontWeight');
      expect(type[s].maxScale).toBe(maxScale[s] ?? 2);
    }
    // 숫자만 있는 단계는 Mono(1.1-7) — 다른 단계는 Sans
    for (const s of ['readout', 'bigClock', 'gauge', 'tick'] as Stage[]) expect(type[s].style.fontFamily).toBe(font.mono500);
  });

  test('REQ-433 · 굵은 텍스트면 한 단 굵게 — fontFamily가 다음 face(400→500→600 · 600 그대로 · Mono 그대로) · fontWeight 0', () => {
    const next: Record<string, string> = {
      [font.sans400]: font.sans500, [font.sans500]: font.sans600, [font.sans600]: font.sans600, [font.mono500]: font.mono500,
    };
    for (const s of STAGES) {
      const plain = type[s].style;
      const bold = stageStyle(s, { bold: true });
      expect(bold).toEqual({ ...plain, fontFamily: next[plain.fontFamily as string] });
      expect(bold).not.toHaveProperty('fontWeight');
      expect(stageStyle(s, { bold: false })).toEqual(plain);
    }
    // 네 face가 단계 표에 실제로 다 있다(빈 갈래 초록 방지)
    expect(new Set(STAGES.map((s) => type[s].style.fontFamily))).toEqual(new Set(FACES));
  });

  test('REQ-433 · 굵기 갈래 w는 Sans 단계 안에서 face를 고르고(fontWeight 0) 굵은 텍스트면 그 위 한 단 · Mono 단계는 w를 무시한다', () => {
    expect(stageStyle('aux', { bold: false, w: 600 }).fontFamily).toBe(font.sans600); // 섹션 라벨
    expect(stageStyle('foot', { bold: false, w: 500 }).fontFamily).toBe(font.sans500); // 배지 · 칸 라벨
    expect(stageStyle('row', { bold: false, w: 400 }).fontFamily).toBe(font.sans400); // 설정 줄
    expect(stageStyle('aux', { bold: true, w: 500 }).fontFamily).toBe(font.sans600);
    expect(stageStyle('aux', { bold: true, w: 600 }).fontFamily).toBe(font.sans600);
    expect(stageStyle('tick', { bold: false, w: 400 }).fontFamily).toBe(font.mono500);
    expect(stageStyle('aux', { bold: true, w: 400 })).not.toHaveProperty('fontWeight');
    expect(stageStyle('aux', { bold: false, w: 600 }).fontSize).toBe(type.aux.style.fontSize); // 크기 · 줄은 단계 그대로
  });
});

// ── 모서리 · 간격(REQ-452) ────────────────────────────────────────────────
test('REQ-452 · radius 값은 2 · 8 · 12 · 999뿐이고 space · layout은 4의 배수', () => {
  expect(Object.values(radius).sort((a, b) => a - b)).toEqual([2, 8, 12, 999]);
  for (const v of [...Object.values(space), ...Object.values(layout)]) expect(v % 4).toBe(0);
});

// ── 날 색(REQ-54) · 굵기(REQ-470) — theme/ 밖 소스 스캔 ─────────────────────
const RAW_COLOR = /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})\b|rgba?\(/;
const FONT_WEIGHT = /\bfontWeight\b/;
function sourceFiles(dir: string, skip: (path: string) => boolean): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (skip(p)) return [];
    if (statSync(p).isDirectory()) return sourceFiles(p, skip);
    return /\.tsx?$/.test(name) ? [p] : [];
  });
}
/** 내용이 탐지기에 걸리는 파일 경로 — 실제 소스와 심은 파일이 같은 이 함수를 지난다. */
function hits(files: { path: string; text: string }[], re: RegExp): string[] {
  return files.filter((f) => re.test(f.text)).map((f) => f.path);
}
const outsideTheme = () =>
  sourceFiles(APP_SRC, (p) => p === join(APP_SRC, 'theme')).map((f) => ({ path: f, text: readFileSync(f, 'utf8') }));

describe('날 색(REQ-54)', () => {
  test("REQ-54 · 날 색 탐지기는 '#123456'·'rgba(0,0,0,1)'을 심은 문자열을 잡는다", () => {
    expect(RAW_COLOR.test("const a = '#123456';")).toBe(true);
    expect(RAW_COLOR.test("const b = 'rgba(0,0,0,1)';")).toBe(true);
    expect(RAW_COLOR.test("const c = color.ink; // '#delete' 앵커는 색이 아니다")).toBe(false);
  });

  test("REQ-54 · 심은 '#ABCDEF' 파일은 같은 스캐너에서 잡힌다(양성 대조)", () => {
    expect(hits([{ path: join(APP_SRC, 'planted.tsx'), text: "const a = '#ABCDEF';" }], RAW_COLOR)).toHaveLength(1);
  });

  test('REQ-54 · app/src의 theme/ 밖 .ts·.tsx에 날 색이 0개다', () => {
    const files = outsideTheme();
    expect(files.length).toBeGreaterThanOrEqual(3); // 빈 집합끼리 늘 초록인 상태를 막는다
    expect(hits(files, RAW_COLOR)).toEqual([]);
  });
});

test('REQ-470 · theme/ 밖 앱 소스에 fontWeight 0 — 굵기는 font 넷의 face로만(심은 견본은 잡힌다)', () => {
  // fontWeight가 face 이름과 같이 오면 RN이 그 패밀리에서 가까운 굵기를 다시 골라 소리 없이 다른 face가 된다(계획 2.4)
  const planted = [
    { path: 'a.tsx', text: "const s = { fontFamily: font.sans600, fontWeight: '600' };" },
    { path: 'b.tsx', text: 'const s = { fontFamily: font.sans600 };' },
  ];
  expect(hits(planted, FONT_WEIGHT)).toEqual(['a.tsx']);
  const files = outsideTheme();
  expect(files.length).toBeGreaterThanOrEqual(3);
  expect(hits(files, FONT_WEIGHT)).toEqual([]);
});
