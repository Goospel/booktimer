import { readdirSync, readFileSync, statSync } from 'fs';
import { join, relative, resolve, sep } from 'path';

import { fontsReady } from './fonts';
import { color, layout, line, radius, SERIF, space, stageStyle, type, type Stage } from './legacy';

// 앱 UI 개편 U2a — 옛 토큰(legacy.ts)의 옛 가드(REQ-49 · 51 · 52 · 53 · 55)를 그대로 옮겼다. 안 옮긴 화면이 옛 값을 쓰는 동안
// 옛 값을 지킨다 — U9가 legacy.ts와 함께 지운다. REQ-50(웹 app.css · books/pure.ts 대조)은 사용자 결정 「앱만 독자 디자인」대로 지웠다.
// 여기에 REQ-471(legacy를 import해도 되는 파일 목록 — 계획 7.2 · D17)을 둔다.

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

// ── 글꼴(REQ-55 — 시각 §F-3 ①) ───────────────────────────────────────────
test('REQ-55 · fontsReady는 성공이면 참 · 실패면 참 · 진행 중이면 거짓이다', () => {
  expect(fontsReady(true, null)).toBe(true);
  expect(fontsReady(false, new Error('x'))).toBe(true); // 실패해도 앱이 뜬다
  expect(fontsReady(false, null)).toBe(false);
});

// ── legacy를 import해도 되는 파일(REQ-471 · 계획 7.2 · D17) ─────────────────
// 화면 PR은 자기 파일 줄만 지운다 — `// Uxx` 주석 줄은 남긴다(계획 7.4 — 병렬 PR의 지움이 부딪히지 않게). 새 파일은 넣지 않는다
// (새 파일이 legacy를 읽으면 빨갛다 — 그것이 검사다). 경로는 app/ 기준.
const LEGACY_IMPORTERS = [
  // U2b
  // U3a-2
  'src/app/(tabs)/index.tsx',
  // U4
  'src/app/(tabs)/study.tsx',
  // U5a
  'src/app/(tabs)/library.tsx', 'src/components/NoteRow.tsx',
  // U5b
  'src/app/book/[id].tsx',
  // U6
  'src/app/margin/[bookId].tsx', 'src/components/QuestionBand.tsx', 'src/components/QuestionSheet.tsx',
  'src/components/QuoteOnlyBand.tsx', 'src/components/ConsentSheet.tsx',
  // U7
  'src/app/add.tsx', 'src/app/(tabs)/settings.tsx', 'src/app/(auth)/login.tsx', 'src/app/account-delete.tsx',
  // U8
  'widgets/widgetColors.test.ts',
  // U9 — 다리(BRIDGE_NAMES — U2b · U3a-1) · 옛 글꼴 · 자산 테스트
  'src/components/ui.tsx', 'src/components/Cover.tsx', 'src/theme/fonts.ts', 'assets.test.ts',
];

const APP = resolve(__dirname, '../..'); // app/src/theme → app/
const SELF = ['src/theme/legacy.ts', 'src/theme/legacy.test.ts'];
/** `import … from '…/theme/legacy'`(theme/ 안이면 `'./legacy'` · 앱 루트 테스트면 `'./src/theme/legacy'`) — `type` import도 센다 */
const LEGACY_IMPORT = /\bfrom\s+['"](?:\.{1,2}\/)+(?:src\/)?(?:theme\/)?legacy['"]/;

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) return files(p);
    return /\.tsx?$/.test(name) ? [p] : [];
  });
}
const rel = (p: string) => relative(APP, p).split(sep).join('/');
/** 스캔 범위 — app/src · app/widgets · app/*.test.ts(legacy 자신 둘은 뺀다) */
function scanned(): { path: string; text: string }[] {
  const top = readdirSync(APP).filter((n) => /\.test\.ts$/.test(n)).map((n) => join(APP, n));
  return [...files(join(APP, 'src')), ...files(join(APP, 'widgets')), ...top]
    .map((p) => ({ path: rel(p), text: readFileSync(p, 'utf8') }))
    .filter((f) => !SELF.includes(f.path));
}
/** 실제 소스와 심은 견본이 같이 지나는 판정 둘 */
const outsideList = (all: { path: string; text: string }[], list: string[]) =>
  all.filter((f) => LEGACY_IMPORT.test(f.text) && !list.includes(f.path)).map((f) => f.path);
const notImporting = (all: { path: string; text: string }[], list: string[]) =>
  list.filter((p) => !all.some((f) => f.path === p && LEGACY_IMPORT.test(f.text)));

describe('legacy importer 목록(REQ-471)', () => {
  test('REQ-471 · 스캔 범위가 실제로 읽힌다 — app/src · widgets · 앱 루트 테스트(빈 집합 초록 방지)', () => {
    const all = scanned();
    expect(all.length).toBeGreaterThanOrEqual(100);
    expect(all.some((f) => f.path.startsWith('widgets/'))).toBe(true);
    expect(all.some((f) => f.path === 'assets.test.ts')).toBe(true);
    expect(all.some((f) => f.path === 'src/theme/legacy.ts')).toBe(false);
  });

  test('REQ-471 · legacy를 import하는 파일은 LEGACY_IMPORTERS뿐이다 — 목록 밖 파일에 legacy import를 심은 견본은 잡힌다', () => {
    const all = scanned();
    expect(outsideList(all, LEGACY_IMPORTERS)).toEqual([]);
    const planted = [
      { path: 'src/components/New.tsx', text: "import { color } from '../theme/legacy';" },
      { path: 'src/app/new.tsx', text: "import type { Stage } from '../theme/legacy';" },
      { path: 'src/theme/x.ts', text: "import {\n  color,\n} from './legacy';" },
      { path: 'other.test.ts', text: "import { color } from './src/theme/legacy';" },
      { path: 'src/components/Ok.tsx', text: "import { color } from '../theme/tokens';" },
    ];
    expect(outsideList(planted, LEGACY_IMPORTERS)).toEqual(['src/components/New.tsx', 'src/app/new.tsx', 'src/theme/x.ts', 'other.test.ts']);
  });

  test('REQ-471 · LEGACY_IMPORTERS의 파일은 실제로 legacy를 import한다 — 옮기고도 남은 줄은 잡힌다(목록이 줄기만 한다)', () => {
    const all = scanned();
    expect(notImporting(all, LEGACY_IMPORTERS)).toEqual([]);
    const moved = all.map((f) => (f.path === 'src/app/add.tsx' ? { ...f, text: f.text.replace(LEGACY_IMPORT, "from '../theme/tokens'") } : f));
    expect(notImporting(moved, LEGACY_IMPORTERS)).toEqual(['src/app/add.tsx']);
  });

  test('REQ-471 · 다리 파일은 legacy에서 정해진 이름만 가져온다 — ui.tsx: type · stageStyle · Stage(ui.tsx에 color를 심은 견본은 잡힌다)', () => {
    const all = scanned();
    expect(Object.keys(BRIDGE_NAMES).every((p) => LEGACY_IMPORTERS.includes(p))).toBe(true);
    expect(bridgeLeaks(all)).toEqual([]);
    expect(Object.keys(BRIDGE_NAMES).every((p) => importedNames(all.find((f) => f.path === p)!.text).length > 0)).toBe(true); // 빈 이름 초록 방지
    const planted = all.map((f) =>
      f.path === 'src/components/ui.tsx' ? { ...f, text: f.text.replace(/import \{([^}]*)\} from '\.\.\/theme\/legacy'/, "import { color,$1} from '../theme/legacy'") } : f,
    );
    expect(bridgeLeaks(planted)).toEqual(['src/components/ui.tsx: color']);
    const extra = (line: string) => bridgeLeaks(all.map((f) => (f.path === 'src/components/ui.tsx' ? { ...f, text: `${line}\n${f.text}` } : f)));
    expect(extra("import * as L from '../theme/legacy';")).toEqual(['src/components/ui.tsx: *']); // namespace 꼴
    expect(extra("import legacy from '../theme/legacy';")).toEqual(['src/components/ui.tsx: default']); // default 꼴
  });
});

// 다리 파일(안 옮긴 화면의 옛 단계 · 옛 표지 크기를 그려 주는 파일)이 legacy에서 가져와도 되는 이름 — U3a-1이 Cover.tsx를 더한다 · U9에서 지운다
const BRIDGE_NAMES: Record<string, string[]> = { 'src/components/ui.tsx': ['type', 'stageStyle', 'Stage'] };
/** legacy import 문들이 가져오는 이름(`type X` · `X as Y`는 X · `* as X`는 '*' · default는 'default') */
function importedNames(text: string): string[] {
  const re = /import\s+(?:type\s+)?([^'";]*?)\s*from\s*['"](?:\.{1,2}\/)+(?:src\/)?(?:theme\/)?legacy['"]/g;
  return [...text.matchAll(re)].flatMap((m) => {
    const named = /\{([^}]*)\}/.exec(m[1])?.[1] ?? '';
    const rest = m[1].replace(/\{[^}]*\}/, '');
    return [
      ...(/^\s*[\w$]+/.test(rest) ? ['default'] : []),
      ...(rest.includes('*') ? ['*'] : []),
      ...named.split(',').map((n) => n.trim().replace(/^type\s+/, '').split(/\s+as\s+/)[0]).filter(Boolean),
    ];
  });
}
const bridgeLeaks = (all: { path: string; text: string }[]) =>
  Object.entries(BRIDGE_NAMES).flatMap(([p, ok]) =>
    importedNames(all.find((f) => f.path === p)?.text ?? '').filter((n) => !ok.includes(n)).map((n) => `${p}: ${n}`),
  );
