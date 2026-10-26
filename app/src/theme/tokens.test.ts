import { readdirSync, readFileSync, statSync } from 'fs';
import { join, resolve } from 'path';

import { fontsReady } from './fonts';
import { color, cover, layout, line, night, radius, SERIF, space, stageStyle, type, type Stage } from './tokens';

// 시각 보충 §B T1 — 토큰 불변식(REQ-49~55). 밤 팔레트는 night.desk 하나다(범위 보충 §0.3 · REQ-107).

const ROOT = resolve(__dirname, '../../..'); // 레포 루트(app/src/theme → ../../..)
const APP_SRC = resolve(__dirname, '..');

// ── 대비(WCAG 2.x 상대휘도) ───────────────────────────────────────────────
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

describe('대비(REQ-49)', () => {
  test('REQ-49 · 대비 계산기는 검정/흰색 21.00 · #6F6A5E/#FCFAF5 5.16 · #9A9080/#322B22 4.44를 낸다', () => {
    expect(round2(contrast('#000000', '#FFFFFF'))).toBe(21);
    expect(round2(contrast('#6F6A5E', '#FCFAF5'))).toBe(5.16);
    expect(round2(contrast('#9A9080', '#322B22'))).toBe(4.44); // 음성 대조 — 4.5 미만
  });

  test('REQ-49 · 글자·바탕 쌍은 전부 4.5 이상이다(낮 6쌍 — 밤 글자 토큰은 REQ-107로 없다)', () => {
    const pairs: [string, string][] = [
      [color.ink, color.paper],
      [color.text, color.paper],
      [color.muted, color.paper],
      [color.danger, color.paper],
      [color.onInk, color.ink],
      [color.muted, color.paper2],
    ];
    for (const [fg, bg] of pairs) expect(contrast(fg, bg)).toBeGreaterThanOrEqual(4.5);
  });

  test('REQ-49 · 입력 경계(muted/paper)는 3 이상이고 가는 선(line/paper)은 3 미만이라 경계 후보에서 빠진다', () => {
    expect(contrast(color.muted, color.paper)).toBeGreaterThanOrEqual(3);
    expect(contrast(color.line, color.paper)).toBeLessThan(3);
  });
});

// ── 웹과 대조(REQ-50) ────────────────────────────────────────────────────
/** CSS 문자열에서 selector로 시작하는 첫 블록의 `--이름: 값;` 선언을 읽는다. */
function cssVars(css: string, selector: string): Map<string, string> {
  const start = css.indexOf(selector);
  if (start < 0) return new Map();
  const open = css.indexOf('{', start);
  const close = css.indexOf('\n}', open);
  const vars = new Map<string, string>();
  for (const m of css.slice(open + 1, close).matchAll(/--([\w-]+)\s*:\s*([^;]+);/g)) vars.set(m[1], m[2].trim());
  return vars;
}
const DAY_SELECTOR = ':root, body.study-lamp .lamp-page {';
const NIGHT_SELECTOR = ':root[data-theme="dark"], body.study-lamp {';
const DAY_MAP: [keyof typeof color, string][] = [
  ['paper', 'bg'], ['paper2', 'paper-2'], ['ink', 'text'], ['text', 'ink-2'],
  ['muted', 'muted'], ['line', 'border'], ['onInk', 'on-accent'], ['danger', 'danger'],
];
/** 앱 값과 다른 웹 선언 이름 목록(빈 배열 = 같다). */
function mismatches(css: string): string[] {
  const day = cssVars(css, DAY_SELECTOR);
  const nightVars = cssVars(css, NIGHT_SELECTOR);
  const out = DAY_MAP.filter(([k, v]) => day.get(v)?.toUpperCase() !== color[k].toUpperCase()).map(([, v]) => `--${v}`);
  if (nightVars.get('bg')?.toUpperCase() !== night.desk.toUpperCase()) out.push('dark --bg');
  return out;
}
const appCss = () => readFileSync(join(ROOT, 'src/main/resources/static/css/app.css'), 'utf8');

describe('웹과 대조(REQ-50)', () => {
  test('REQ-50 · 낮 핵심 8값 · night.desk · 천 표지 12색이 웹 app.css·books/pure.ts와 같다', () => {
    expect(mismatches(appCss())).toEqual([]);
    const pure = readFileSync(join(ROOT, 'frontend/src/books/pure.ts'), 'utf8');
    const block = pure.slice(pure.indexOf('COVER_PALETTE'), pure.indexOf('];', pure.indexOf('COVER_PALETTE')));
    const webPalette = [...block.matchAll(/'(#[0-9A-Fa-f]{6})'/g)].map((m) => m[1].toUpperCase());
    expect(webPalette).toEqual(cover.palette.map((c) => c.toUpperCase()));
    expect(pure).toContain(`COVER_FG = '${cover.fg}'`);
  });

  test('REQ-50 · 대조기는 --muted 하나를 바꾼 CSS 문자열을 불일치로 잡고, 실제 파일에서 선언을 16개 이상 읽는다', () => {
    const css = appCss();
    expect(cssVars(css, DAY_SELECTOR).size).toBeGreaterThanOrEqual(16);
    const tampered = css.replace(/(--muted:\s*)#645E54/i, '$1#123456');
    expect(tampered).not.toBe(css);
    expect(mismatches(tampered)).toEqual(['--muted']);
  });

  test('REQ-50 · 앱 테마의 밤 토큰은 desk 하나다(REQ-107)', () => {
    expect(Object.keys(night)).toEqual(['desk']);
  });
});

// ── 글자 단계(REQ-51 · 52) ────────────────────────────────────────────────
const STAGES = Object.keys(type) as Stage[];
const SERIF_STAGES: Stage[] = ['D1', 'M1', 'D2', 'H1', 'H2', 'H3', 'Q1', 'Q2'];

describe('글자 단계(REQ-51 · 52)', () => {
  test('REQ-51 · 모든 단계는 13pt 이상이다', () => {
    expect(STAGES).toHaveLength(16);
    for (const s of STAGES) expect(type[s].style.fontSize).toBeGreaterThanOrEqual(13);
  });

  test('REQ-51 · 명조 단계는 fontFamily가 SERIF이고 fontWeight가 없다', () => {
    expect(SERIF).toBe('NotoSerifKR-Medium');
    for (const s of SERIF_STAGES) {
      expect(type[s].style.fontFamily).toBe(SERIF);
      expect(type[s].style).not.toHaveProperty('fontWeight');
    }
  });

  test('REQ-51 · 고딕 단계 굵기는 400·500뿐이다(기본값)', () => {
    for (const s of STAGES.filter((x) => !SERIF_STAGES.includes(x))) {
      expect(type[s].style).not.toHaveProperty('fontFamily');
      expect(['400', '500']).toContain(type[s].style.fontWeight);
    }
  });

  test('REQ-52 · 큰 글자 상한은 D1 1.0 · M1 1.2 · D2 1.3이고 나머지 단계는 전부 2.0이다', () => {
    const expected: Partial<Record<Stage, number>> = { D1: 1, M1: 1.2, D2: 1.3 };
    for (const s of STAGES) expect(type[s].maxScale).toBe(expected[s] ?? 2);
  });

  test('REQ-52 · 굵은 텍스트가 켜지면 Q1·Q2는 fontFamily 없이 fontWeight 600이 되고, 다른 단계는 기본 스타일 그대로다', () => {
    for (const s of STAGES) {
      const bold = stageStyle(s, { bold: true });
      if (s === 'Q1' || s === 'Q2') {
        expect(bold).not.toHaveProperty('fontFamily');
        expect(bold.fontWeight).toBe('600');
        expect(bold.fontSize).toBe(type[s].style.fontSize);
      } else {
        expect(bold).toEqual(type[s].style);
      }
      expect(stageStyle(s, { bold: false })).toEqual(type[s].style);
    }
  });
});

// ── 모서리·간격·선(REQ-53) ────────────────────────────────────────────────
test('REQ-53 · 모서리는 0·1·2·999 · 간격은 4의 배수 · 선은 1·2뿐이다', () => {
  expect(Object.values(radius).sort((a, b) => a - b)).toEqual([0, 1, 2, 999]);
  for (const v of [...Object.values(space), layout.gutter, layout.sectionGap, layout.hit, layout.readMax]) expect(v % 4).toBe(0);
  expect(Object.values(line).sort()).toEqual([1, 2]);
});

// ── 날 색(REQ-54) ────────────────────────────────────────────────────────
const RAW_COLOR = /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})\b|rgba?\(/;
function sourceFiles(dir: string, skip: (path: string) => boolean): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (skip(p)) return [];
    if (statSync(p).isDirectory()) return sourceFiles(p, skip);
    return /\.tsx?$/.test(name) ? [p] : [];
  });
}

/** 내용에 날 색이 있는 파일 경로 — 실제 소스와 심은 파일이 같은 이 함수를 지난다. */
function rawColorHits(files: { path: string; text: string }[]): string[] {
  return files.filter((f) => RAW_COLOR.test(f.text)).map((f) => f.path);
}

describe('날 색(REQ-54)', () => {
  test("REQ-54 · 날 색 탐지기는 '#123456'·'rgba(0,0,0,1)'을 심은 문자열을 잡는다", () => {
    expect(RAW_COLOR.test("const a = '#123456';")).toBe(true);
    expect(RAW_COLOR.test("const b = 'rgba(0,0,0,1)';")).toBe(true);
    expect(RAW_COLOR.test("const c = color.ink; // '#delete' 앵커는 색이 아니다")).toBe(false);
  });

  test("REQ-54 · 심은 '#ABCDEF' 파일은 같은 스캐너에서 잡힌다(양성 대조)", () => {
    expect(rawColorHits([{ path: join(APP_SRC, 'planted.tsx'), text: "const a = '#ABCDEF';" }])).toHaveLength(1);
  });

  test('REQ-54 · app/src의 theme/ 밖 .ts·.tsx에 날 색이 0개다', () => {
    const files = sourceFiles(APP_SRC, (p) => p === join(APP_SRC, 'theme'));
    expect(files.length).toBeGreaterThanOrEqual(3); // 빈 집합끼리 늘 초록인 상태를 막는다
    expect(rawColorHits(files.map((f) => ({ path: f, text: readFileSync(f, 'utf8') })))).toEqual([]);
  });
});

// ── 글꼴(REQ-55 — 시각 §F-3 ①) ───────────────────────────────────────────
test('REQ-55 · fontsReady는 성공이면 참 · 실패면 참 · 진행 중이면 거짓이다', () => {
  expect(fontsReady(true, null)).toBe(true);
  expect(fontsReady(false, new Error('x'))).toBe(true); // 실패해도 앱이 뜬다
  expect(fontsReady(false, null)).toBe(false);
});
