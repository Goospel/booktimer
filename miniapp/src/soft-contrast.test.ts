import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

import { FIELD, OUTLINE_PILL } from './ui';

/**
 * Soft 대비 가드 — 글자·바탕 토큰 쌍이 WCAG AA(4.5:1) 아래로 내려가지 않는다.
 *
 * <p>색은 `global.css` 네 블록(낮 `html:root` · 밤 `body.reading-lamp main` · 등불 밑 페이지
 * `body.reading-lamp .lamp-page` · 공부 `body.study-mode`)에 흩어져 있고, 밤·공부는 낮을 <b>상속한 뒤</b>
 * 일부만 덮는다. 그래서 블록을 겹쳐 실제로 그 모드에서 풀리는 값끼리 잰다.
 *
 * <p>진짜 실패: 밤 grey600을 조금 어둡게 만진 커밋이 카드 위 4.44:1로 떨어진 것(2026-08-19에 실제로 났다 —
 * 눈으로는 멀쩡해 보였고 브라우저 실측으로 잡혔다). 이제 커밋 전에 잡는다.
 */

const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');

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

function background(selector: string): string {
  const m = /(?:^|[\s;{])background:\s*(#[0-9A-Fa-f]{6})/.exec(block(selector));
  if (!m) throw new Error(`배경 없음: ${selector}`);
  return m[1];
}

function luminance(hex: string): number {
  const h = hex.replace('#', '');
  const full = h.length === 3 ? [...h].map((c) => c + c).join('') : h;
  const [r, g, b] = [0, 2, 4].map((i) => {
    const c = parseInt(full.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function ratio(fg: string, bg: string): number {
  const [a, b] = [luminance(fg), luminance(bg)].sort((x, y) => y - x);
  return (a + 0.05) / (b + 0.05);
}

describe('계측기 자기검증 — 이게 틀리면 아래 초록은 아무 뜻이 없다', () => {
  it('검정/흰색 = 21', () => {
    expect(ratio('#000', '#fff')).toBeCloseTo(21, 5);
  });

  it('옛 흐린 글자/카드지 ≈ 5.16 (양성 대조 — 알려진 값을 알려진 대로 잰다)', () => {
    expect(ratio('#6F6A5E', '#FCFAF5')).toBeCloseTo(5.16, 1);
  });

  it('옛 밤 첫 시도 #9A9080/#322B22 = 4.44 — 이 가드가 실제로 빨갛게 되는 값이다(음성 대조)', () => {
    expect(ratio('#9A9080', '#322B22')).toBeLessThan(4.5);
  });
});

const day = decls('html:root');
const night = { ...day, ...decls('body.reading-lamp main') };
const page = { ...night, ...decls('body.reading-lamp .lamp-page') };
const study = { ...day, ...decls('body.study-mode') };

type Scope = { name: string; t: Record<string, string>; body?: string };
const scopes: Scope[] = [
  { name: '낮', t: day, body: background('html body') },
  { name: '밤', t: night, body: background('body.reading-lamp') },
  // 등불 밑 페이지는 미색 한 장이다 — 글자가 책상 위에 서지 않으므로 캔버스 쌍은 재지 않는다.
  // 에디토리얼 PR-2부터 그 페이지를 칠하는 것은 블록 리터럴이 아니라 아치·오늘 표의 `var(--adaptiveGrey100)`이고,
  // 블록이 그 토큰을 미색으로 다시 정의한다 — 그래서 블록의 토큰 그대로가 페이지의 바탕이다.
  { name: '등불 밑 페이지', t: page },
  { name: '공부', t: study, body: background('html body') },
];

const PAIRS: [string, string][] = [
  ['--adaptiveGrey600', '--adaptiveGrey100'], // muted / paper
  ['--adaptiveGrey600', 'body'],
  ['--adaptiveGrey600', '--softDent'], // muted / paper-2
  ['--adaptiveGrey700', '--adaptiveGrey100'], // text / paper
  ['--adaptiveGrey800', '--adaptiveGrey100'], // ink / paper
  ['--adaptiveBlue700', '--adaptiveGrey100'], // accent / paper
  ['--filledInk', '--adaptiveBlue700'], // 체크 배지 · 잉크 원 위 획
  ['--butterInk', '--butterBg'],
  ['--brandButtonInk', '--adaptiveGrey100'], // 외곽선 알약 글자
  // 오류·위험 빨강(ErrorMessage · Text red500 · 관리 시트 위험 행) — TDS 기본 #F04452는 종이 위 3.27이었다
  ['--adaptiveRed500', '--adaptiveGrey100'],
  ['--adaptiveRed500', 'body'],
];

/**
 * TDS 버튼 규칙에서 글자·바탕 토큰을 <b>뽑는다</b>(손으로 적은 쌍은 규칙이 어떤 토큰을 쓰는지 안 본다 — 리뷰 M9).
 * `var(--X` 꼴이 아니면 뽑기부터 실패한다 — 토큰 경유가 곧 밤·공부가 따라오는 조건이라 그것도 잠근다.
 * 검정 알약(primary)과 비활성 주 버튼(결정 ② — 옅은 면 + muted 글자) 두 규칙이다.
 */
function rulePair(selectorHead: string): [string, string] {
  const at = css.indexOf(selectorHead);
  const rule = at < 0 ? '' : css.slice(css.indexOf('{', at), css.indexOf('}', at));
  const ink = /--button-color:\s*var\((--[\w-]+)/.exec(rule)?.[1];
  const bg = /(?:^|[\s;{])background-color:\s*var\((--[\w-]+)/.exec(rule)?.[1];
  return [ink ?? `(${selectorHead} 글자 토큰 없음)`, bg ?? `(${selectorHead} 바탕 토큰 없음)`];
}
const PRIMARY = rulePair(".tds-mobile-button[style*='--button-background-color: #3182f6'] {");
const DISABLED = rulePair(':disabled {');
// TDS danger 버튼(채움 · weak) — TDS 기본은 흰 글자/#f04452 3.71 · #e42939/옅은 분홍 3.55로 AA 미달이었다.
const DANGER_FILL = rulePair(".tds-mobile-button[style*='--button-background-color: #f04452'] {");
const DANGER_WEAK = rulePair(".tds-mobile-button[style*='--button-background-color: rgba(251, 136, 144, 0.15)'] {");
// 비활성 danger — 위 DISABLED(`:disabled {`)는 주 버튼 규칙만 잡는다(이 규칙은 `:not(...) {`로 끝난다).
const DANGER_OFF = rulePair(".tds-mobile-button[style*='--button-background-color: #f04452']:disabled:not([aria-busy='true']) {");
PAIRS.push(PRIMARY, DISABLED, DANGER_FILL, DANGER_WEAK, DANGER_OFF);

/**
 * 홈 피드 「다 읽음」 배지(채움 톤) — 같은 이유로 `HomeFeed.tsx`의 `badgeStyle`에서 뽑는다.
 */
const feed = readFileSync(new URL('./screens/HomeFeed.tsx', import.meta.url), 'utf8');
const solidBadge = /tone === 'solid'\s*\?\s*\{([^}]*)\}/.exec(feed)?.[1] ?? '';
const BADGE_BG = /background:\s*'var\((--[\w-]+)/.exec(solidBadge)?.[1];
const BADGE_INK = /color:\s*'var\((--[\w-]+)/.exec(solidBadge)?.[1];
PAIRS.push([BADGE_INK ?? '(배지 글자 토큰 없음)', BADGE_BG ?? '(배지 바탕 토큰 없음)']);

describe('버튼 규칙·배지에서 글자·바탕 토큰을 뽑는다', () => {
  it('검정 알약 · 비활성 주 버튼 · danger 채움·weak · 피드 채움 배지 — 전부 var(--토큰)이다', () => {
    for (const [ink, bg] of [PRIMARY, DISABLED, DANGER_FILL, DANGER_WEAK, DANGER_OFF, [BADGE_INK, BADGE_BG]]) {
      expect(ink).toMatch(/^--/);
      expect(bg).toMatch(/^--/);
    }
  });
});

describe('REQ-02 · 글자·바탕 쌍이 4.5 이상(낮·밤·등불 페이지·공부)', () => {
  for (const { name, t, body } of scopes) {
    for (const [fg, bg] of PAIRS) {
      if (bg === 'body' && !body) continue;
      it(`${name}: ${fg} / ${bg}`, () => {
        const f = t[fg];
        const b = bg === 'body' ? body : t[bg];
        // 토큰이 빠지면 쌍 자체를 못 잰다 — 조용히 건너뛰지 않고 실패한다.
        expect(f, `${name}에서 ${fg} 미정의`).toMatch(/^#[0-9A-Fa-f]{3,6}$/);
        expect(b, `${name}에서 ${bg} 미정의`).toMatch(/^#[0-9A-Fa-f]{3,6}$/);
        expect(ratio(f, b!)).toBeGreaterThanOrEqual(4.5);
      });
    }
  }
});

/**
 * 비글자 경계(WCAG 1.4.11) — 입력칸·선택 상자·외곽선 알약의 테두리가 종이와 3:1 이상이다.
 *
 * <p>진짜 실패: 누가 입력칸 경계를 「가는 선이 더 단정하다」며 `--adaptiveGrey200`(종이 대비 1.33)로 정리하는 것 —
 * 카드 면이 사라진 미색 위에서 칸이 녹는다. 그래서 경계 토큰을 <b>실제 자리에서 뽑는다</b>(`FIELD.border` · TDS 입력칸
 * 덮기 규칙 · `OUTLINE_PILL.border`). 손으로 적은 이름을 재면 자리를 바꿔도 초록이다.
 */
describe('REQ-02 · 비글자 경계가 3:1 이상이다', () => {
  const tokenOf = (border: string) => /var\((--[\w-]+)/.exec(border)?.[1];
  const tdsInputRule = (() => {
    const at = css.indexOf('[style*="--text-field-box-background-color"]');
    return at < 0 ? '' : css.slice(css.indexOf('{', at), css.indexOf('}', at));
  })();
  const SOURCES: [string, string | undefined][] = [
    ['FIELD', tokenOf(FIELD.border)],
    ['TDS 입력칸 덮기', tokenOf(/border:\s*([^;]*)/.exec(tdsInputRule)?.[1] ?? '')],
    ['OUTLINE_PILL', tokenOf(OUTLINE_PILL.border)],
  ];

  it('뽑기 자기검증 — 셋 다 var(--토큰) 꼴이다', () => {
    for (const [, token] of SOURCES) expect(token).toMatch(/^--/);
  });

  it('음성 대조 — 가는 선 #D9D1C3 / 종이 = 1.33 < 3(계측기가 빨갛게 되는 값)', () => {
    expect(ratio('#D9D1C3', '#F5F0E6')).toBeLessThan(3);
  });

  for (const { name, t, body } of [scopes[0], scopes[3]]) {
    for (const [where, token] of SOURCES) {
      it(`${name}: ${where} 경계 / 종이 ≥ 3`, () => {
        const c = t[token ?? ''];
        expect(c, `${name}에서 ${token} 미정의`).toMatch(/^#[0-9A-Fa-f]{6}$/);
        expect(ratio(c, body!)).toBeGreaterThanOrEqual(3);
      });
    }
  }

  it('✓ 배지 — 체크(미색) / 원(잉크) ≥ 3(배지는 표지 위에 서므로 원과 표지가 아니라 체크와 원이 쌍이다)', () => {
    for (const { t } of [scopes[0], scopes[3]]) expect(ratio(t['--filledInk'], t['--adaptiveBlue700'])).toBeGreaterThanOrEqual(3);
  });
});
