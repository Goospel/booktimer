import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

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
const DAY_BRAND_INK = '#3F5A3C'; // TDS 버튼 규칙의 `var(--brandButtonInk, …)` 폴백 = 낮 값

type Scope = { name: string; t: Record<string, string>; body?: string };
const scopes: Scope[] = [
  { name: '낮', t: { '--brandButtonInk': DAY_BRAND_INK, ...day }, body: background('html body') },
  { name: '밤', t: night, body: background('body.reading-lamp') },
  // 등불 밑 페이지는 카드 한 장이다 — 글자가 밤 캔버스 위에 서지 않으므로 캔버스 쌍은 재지 않는다.
  // ⚠️ 그 카드를 실제로 칠하는 것은 토큰이 아니라 블록의 `background: … !important` 리터럴이다(인라인
  // `var(--adaptiveGrey100)`은 자기 요소의 재정의를 못 본다). 토큰만 재면 리터럴이 어긋나도 초록이다.
  { name: '등불 밑 페이지', t: { ...page, '--adaptiveGrey100': background('body.reading-lamp .lamp-page') } },
  { name: '공부', t: study, body: background('html body') },
];

const PAIRS: [string, string][] = [
  ['--adaptiveGrey600', '--adaptiveGrey100'],
  ['--adaptiveGrey600', 'body'],
  ['--adaptiveGrey600', '--softDent'],
  ['--adaptiveGrey800', '--adaptiveGrey100'],
  ['--adaptiveBlue700', '--adaptiveGrey100'],
  ['--filledInk', '--adaptiveBlue700'],
  ['--butterInk', '--butterBg'],
  ['--brandButtonInk', '--adaptiveGrey100'],
  ['--brandButtonInk', '--adaptiveBlue50'], // primary 버튼 = 옅은 채움 위 잉크 — 공부 모드가 4.58로 문턱 바로 위다
];

/**
 * 밤의 부풂에는 흰 외곽이 없다(설계 §3-3의 약속) — 낮 그림자의 흰 하이라이트(.95)가 밤에 새면 어두운 화면에
 * 흰 테가 뜬다. 변수가 빠져도 낮 값이 상속돼 같은 결과가 나므로 「있다」와 「희지 않다」를 함께 잰다.
 */
describe('Soft 그림자 값', () => {
  it('밤 --puffShadow가 따로 있고 강한 흰 하이라이트를 품지 않는다', () => {
    const shadow = decls('body.reading-lamp main')['--puffShadow'];
    expect(shadow).toBeDefined();
    expect(shadow).not.toMatch(/255,\s*255,\s*255,\s*\.9/);
  });

  it('채움 버튼이 부푼 그림자를 싣는다 — 3단 위계의 맨 윗단', () => {
    expect(block(".tds-mobile-button[style*='--btn-filled']")).toMatch(/box-shadow:\s*var\(--puffShadow\)/);
  });
});

describe('Soft 글자·바탕 쌍이 4.5:1 이상이다', () => {
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
