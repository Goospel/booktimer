import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { COVER_FG, COVER_PALETTE, SERIF_VALUE } from './ui';

/**
 * 에디토리얼 재테마 PR-1 — 팔레트·서체가 「미색 종이 + 잉크 하나 + 명조/고딕 두 벌」이다(warm-palette.test를 대체).
 *
 * <p>진짜 실패: 토큰 블록만 바꾸고 폴백·의사요소·휠 안개·시트 딤에 옛 값이 남는 것 — 톤 조율 A가 겪은 그 실패다.
 * 목록으로만 막으면 세대가 다른 옛 폴백(`#6F6A5E` 등)을 못 잡아서, 테마 정의 파일엔 「폴백 = 낮 정의값」 <b>규칙</b>을 건다.
 *
 * <p>스캔 대상은 `src`의 제품 소스(.ts·.tsx·.css) + `index.html`. 테스트 파일은 뺀다(이 파일이 옛 값 목록을 든다).
 */
const SRC = fileURLToPath(new URL('.', import.meta.url));
const INDEX_HTML = fileURLToPath(new URL('../index.html', import.meta.url));

function productFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) productFiles(full, out);
    else if (/\.(tsx?|css)$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) out.push(full);
  }
  return out;
}

const files = [...productFiles(SRC), INDEX_HTML].map((path) => ({
  name: path === INDEX_HTML ? 'index.html' : path.slice(SRC.length).replace(/\\/g, '/'),
  text: readFileSync(path, 'utf8'),
}));

/** 테마 정의 파일 — PR-1이 옷을 정한 자리(계측기 자기검증의 양성 대조용). 부재 단언은 PR-4부터 제품 소스 전부를 본다. */
const THEME_FILES = ['global.css', 'ui.tsx', 'fonts.ts', 'index.html', 'App.tsx', 'coachmark.tsx'];
const theme = files.filter((f) => THEME_FILES.includes(f.name));

function hits(pattern: RegExp, scope = files): string[] {
  const out: string[] = [];
  for (const { name, text } of scope) {
    text.split('\n').forEach((line, i) => {
      if (pattern.test(line)) out.push(`${name}:${i + 1}  ${line.trim()}`);
    });
  }
  return out;
}

const css = readFileSync(join(SRC, 'global.css'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
function block(selector: string): string {
  const start = css.indexOf(`${selector} {`);
  if (start < 0) throw new Error(`블록 없음: ${selector}`);
  return css.slice(start, css.indexOf('}', start));
}
function decls(selector: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const m of block(selector).matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) out[m[1]] = m[2].trim();
  return out;
}
const root = decls('html:root');

/** `var(--토큰, #hex)` 폴백 중 그 토큰의 낮 정의값과 다른 것 — 정의 없는 토큰(`--adaptiveRed500`)은 묻지 않는다. */
function driftedFallbacks(scope: { name: string; text: string }[], defs: Record<string, string>): string[] {
  const out: string[] = [];
  for (const { name, text } of scope) {
    for (const m of text.matchAll(/var\((--[\w-]+),\s*(#[0-9A-Fa-f]{3,6})\)/g)) {
      const def = defs[m[1]];
      if (def !== undefined && /^#/.test(def) && def.toUpperCase() !== m[2].toUpperCase()) out.push(`${name}  ${m[0]} ≠ ${def}`);
    }
  }
  return out;
}

function luminance(hex: string): number {
  const h = hex.replace('#', '');
  const [r, g, b] = [0, 2, 4].map((i) => {
    const c = parseInt(h.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
const ratio = (a: string, b: string) => {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
};

describe('계측기 자기검증 — 스캔이 비면 아래 부재 단언은 전부 공허하다', () => {
  it('테마 정의 파일을 전부 실제로 읽고, 테스트 파일은 안 읽는다', () => {
    expect(theme.map((f) => f.name).sort()).toEqual([...THEME_FILES].sort());
    expect(files.some((f) => f.name.includes('.test.'))).toBe(false);
  });

  it('새 종이색(#F5F0E6)은 잡힌다 — 양성 대조', () => {
    expect(hits(/#F5F0E6/i, theme).length).toBeGreaterThan(0);
  });

  it('정의값과 다른 폴백을 일부러 넣으면 규칙이 잡는다 — 음성 대조', () => {
    const planted = [{ name: 'x.tsx', text: "color: 'var(--adaptiveGrey600, #6F6A5E)'" }];
    expect(driftedFallbacks(planted, root)).toHaveLength(1);
    const clean = [{ name: 'x.tsx', text: `color: 'var(--adaptiveGrey600, ${root['--adaptiveGrey600']})'` }];
    expect(driftedFallbacks(clean, root)).toEqual([]);
  });
});

describe('REQ-01 · 낮 팔레트가 에디토리얼 값이다', () => {
  it('종이 · 잉크 · 글자 3단 · 가는 선 · 강조 = 잉크', () => {
    expect(block('html body')).toMatch(/background:\s*#F5F0E6/);
    expect(root['--adaptiveGrey100']).toBe('#F5F0E6');
    expect(root['--adaptiveBackground']).toBe('#F5F0E6');
    expect(root['--adaptiveGrey800']).toBe('#1D1B18');
    expect(root['--adaptiveGrey700']).toBe('#4A453E');
    expect(root['--adaptiveGrey600']).toBe('#645E54');
    expect(root['--adaptiveGrey200']).toBe('#D9D1C3');
    expect(root['--adaptiveBlue700']).toBe('#1D1B18');
    expect(root['--softDent']).toBe('#ECE5D8');
    expect(root['--filledInk']).toBe('#F5F0E6');
  });

  it('잔디는 잉크 농담 5단이다(D4=A)', () => {
    expect([0, 1, 2, 3, 4].map((i) => root[`--grass${i}`])).toEqual(['#E6DFD2', '#CDC3B2', '#A29785', '#625A4F', '#1D1B18']);
  });

  it('그림자 변수 셋이 어디에도 정의되지 않는다 — 이펙트 0', () => {
    expect(css).not.toMatch(/--(puff|dent|tile)Shadow\s*:/);
  });
});

// PR-4 — 스캔 범위를 테마 정의 파일 → 제품 소스 전부로 넓혔다(화면 파일 구석의 옛 폴백·리터럴).
describe('REQ-01 · 제품 소스 전부에 옛 값이 0', () => {
  it('규칙 — var(--토큰, #hex) 폴백이 그 토큰의 낮 정의값과 같다', () => {
    expect(driftedFallbacks(files, root)).toEqual([]);
  });

  it('목록 — Soft·톤 조율 A의 바탕·면·잉크·세이지·밤 리터럴이 0회다', () => {
    const OLD = [
      '#F3EFE5', '#FBF9F4', '#EAE5D9', '#DED8CA', '#5B5A4D', '#43423A', '#2A2921', '#1E1E18',
      '#E1E8D4', '#3F5A3C', '#5B7F55', '#26231D', '#322E27',
      // PR-4 — 잉크 위 글자의 옛 크림(검정 위 글자는 --filledInk = 종이 #F5F0E6) · 여백 인용 세로선의 옛 베이지
      '#FFFDF8', '#DCD4C2',
    ];
    expect(hits(new RegExp(OLD.join('|'), 'i'))).toEqual([]);
  });

  it('목록 — 옛 갈색 그림자 틴트와 시트 딤(rgba(30, 30, 24)이 0회다', () => {
    expect(hits(/rgba\(\s*112,\s*96,\s*64/)).toEqual([]);
    expect(hits(/rgba\(\s*30,\s*30,\s*24/)).toEqual([]);
  });

  it.each(['--accentPill', '--accentRing'])('%s의 인라인 폴백이 전부 낮 정의값과 같다(제품 소스 전부)', (name) => {
    const norm = (s: string) => s.replace(/\s+/g, '').replace(/,0\./g, ',.');
    const value = norm(root[name]);
    expect(value).toMatch(/^rgba\(/);
    const fallbacks = files.flatMap(({ name: file, text }) =>
      [...text.matchAll(new RegExp(`var\\(${name},\\s*(rgba\\([^)]*\\))\\)`, 'g'))].map((m) => `${file}  ${m[1]}`),
    );
    expect(fallbacks.filter((f) => norm(f.split('  ')[1]) !== value)).toEqual([]);
  });
});

describe('REQ-01 · 옛 세대 값 재유입 가드(제품 소스 전부)', () => {
  it('손글씨(Gaegu)가 0회다', () => {
    expect(hits(/Gaegu/i)).toEqual([]);
    expect(hits(/HANDWRITING/)).toEqual([]);
  });

  it('차가운 Soft 초록 바탕·잉크·밤 hex가 0회다', () => {
    const OLD = [
      '#EEF2EB', '#F9FBF7', '#E6ECE3', '#D6DFD2', '#4E5A4B', '#3A4637', '#232C21', '#1B221A',
      '#DCE8D6', '#C9DAC4', '#A9C7A1', '#A3C09B', '#E3E9E0', '#F2E8C6', '#EFF3F6',
      '#1F251E', '#2A3128', '#232A22', '#3A443A', '#DCE4D6', '#A8B4A3', '#C2CCBE', '#E8EEE3',
    ];
    expect(hits(new RegExp(OLD.join('|'), 'i'))).toEqual([]);
  });

  it('그라데이션 막대가 없다 — 게이지·하루 막대는 단색이다', () => {
    expect(hits(/linear-gradient\(90deg/)).toEqual([]);
  });
});

describe('REQ-03 · 서체가 두 패밀리뿐이고 고운 서체가 0이다', () => {
  it('값·제목은 Noto Serif KR, 본문은 Noto Sans KR로 시작한다', () => {
    expect(SERIF_VALUE.fontFamily.startsWith("'Noto Serif KR'")).toBe(true);
    expect(block('html body')).toMatch(/font-family:\s*'Noto Sans KR'/);
  });

  it('제품 소스·index.html에 Gowun이 0회다 — 양성 대조: Noto는 잡힌다', () => {
    expect(hits(/Noto Serif KR/).length).toBeGreaterThan(0);
    expect(hits(/Gowun/)).toEqual([]);
  });
});

describe('REQ-01 · 무표지 팔레트 12색 전부 미색 이니셜과 4.5 이상(D4=A)', () => {
  it('이니셜은 미색 리터럴이고 12색이다', () => {
    expect(COVER_FG).toBe('#F5F0E6');
    expect(COVER_PALETTE).toHaveLength(12);
  });

  it.each([...COVER_PALETTE])('%s / 미색 ≥ 4.5', (bg) => {
    expect(ratio(bg, COVER_FG)).toBeGreaterThanOrEqual(4.5);
  });

  it('옛 파스텔 한 색은 4.5 아래다 — 음성 대조', () => {
    expect(ratio('#B8C2A6', '#F5F0E6')).toBeLessThan(4.5);
  });
});

describe('REQ-14 · 밤은 책상 + 미색 페이지(D2=A)', () => {
  it('캔버스 = 책상 #1C1A17, 등불 원뿔(::after)이 없다', () => {
    expect(block('body.reading-lamp')).toMatch(/background:\s*#1C1A17/);
    expect(css).not.toMatch(/body\.reading-lamp::after/);
  });

  it('main 밤 값 — 면·글자 3단·강조', () => {
    const night = decls('body.reading-lamp main');
    expect(night['--adaptiveGrey100']).toBe('#1C1A17');
    expect(night['--adaptiveGrey600']).toBe('#A39B8E');
    expect(night['--adaptiveGrey700']).toBe('#D6CFC2');
    expect(night['--adaptiveGrey800']).toBe('#F2EDE3');
    expect(night['--adaptiveBlue700']).toBe('#F2EDE3');
    expect(night['--filledInk']).toBe('#1C1A17');
  });

  // PR-2 — 사각 배경 리터럴을 걷고 페이지 토큰만 미색으로 되돌린다(아치·오늘 표가 그 토큰으로 칠해 아치 모양 페이지가 된다).
  it('등불 밑 페이지는 미색 아치 페이지(토큰)이고 등불 글로우가 없다', () => {
    const page = block('body.reading-lamp .lamp-page');
    expect(page).toMatch(/--adaptiveGrey100:\s*#F5F0E6/);
    expect(page).not.toMatch(/(?:^|[\s;{])background\s*:/);
    expect(css).not.toMatch(/rgba\(\s*255,\s*220,\s*160/);
    expect(page).not.toMatch(/box-shadow/);
  });
});
