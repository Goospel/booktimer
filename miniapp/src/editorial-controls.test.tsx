import { Button, TDSMobileProvider, TextField } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { filterChipStyle } from './screens/Profile';
import { userAgent } from './test-fixtures';

/**
 * 에디토리얼 PR-1 — 누를 수 있는 것의 옷(버튼 2단 · 입력칸 · 고르기 칩).
 *
 * <p>TDS 버튼·입력칸은 색·경계를 <b>인라인 커스텀 프로퍼티</b>로 박는다 — 인라인을 이기는 것은 저자 규칙 + `!important`뿐이고,
 * 정적 렌더 하니스는 css를 적용하지 않으므로 규칙은 소스로, 「키가 실제 마크업에 있는가」는 렌더로 잰다(둘이 한 쌍이라야
 * 「규칙은 있는데 조용히 안 먹는다」를 잡는다 — U-19).
 */
const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');

/** 머리(선택자 끝 조각)로 시작하는 규칙의 본문 — 없으면 `''`. 호출부는 먼저 비지 않았음을 단언한다. */
function rule(head: string): string {
  const at = css.indexOf(head);
  if (at < 0) return '';
  return css.slice(css.indexOf('{', at) + 1, css.indexOf('}', at));
}

const render = (node: ReactNode) => renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);

describe('REQ-06 · TDS 버튼 2단', () => {
  const primary = rule(".tds-mobile-button[style*='--button-background-color: #3182f6'] {");
  const weak = rule(".tds-mobile-button[style*='--button-background-color: rgba(100, 168, 255, 0.15)'] {");

  it('검정 알약(primary) — 잉크 바탕 · 미색 글자 · 999 · 테두리 없음', () => {
    expect(primary).not.toBe('');
    expect(primary).toMatch(/(^|[\s;])background-color:\s*var\(--adaptiveBlue700[^;]*!important/);
    expect(primary).toMatch(/--button-color:\s*var\(--filledInk[^;]*!important/);
    expect(primary).toMatch(/--button-border-radius:\s*999px\s*!important/);
    expect(primary).toMatch(/(^|[\s;])border:\s*0\s*!important/);
  });

  it('외곽선 알약(weak) — 강조 1px · 999 · 투명', () => {
    expect(weak).not.toBe('');
    expect(weak).toMatch(/(^|[\s;])border:\s*1px solid var\(--adaptiveBlue700[^;]*!important/);
    expect(weak).toMatch(/--button-border-radius:\s*999px\s*!important/);
    expect(weak).toMatch(/(^|[\s;])background-color:\s*transparent\s*!important/);
  });

  it('채움 마커 규칙(--btn-filled)이 없다 — FilledButton은 주 동작 표지로만 남는다', () => {
    expect(css).not.toContain('--btn-filled');
  });

  /**
   * 결정 ② — 비활성 주 버튼은 옅은 면(paper-2) + muted 글자, 테두리 없음. TDS는 비활성 fill 버튼 전체에 불투명도 .3을
   * 인라인으로 거는데, 그대로 두면 잉크 위에 겹쳐 「진한 회색 알약」이 된다 — 화면당 검정 알약 ≤ 1 규칙을 흐린다.
   */
  it('결정 ② · 비활성 주 버튼 = paper-2 면 + muted 글자 + 불투명도 1, 테두리 없음', () => {
    const disabled = rule(":disabled {");
    expect(disabled).not.toBe('');
    expect(css).toMatch(/\.tds-mobile-button\[style\*='--button-background-color: #3182f6'\]:disabled\s*\{/);
    expect(disabled).toMatch(/(^|[\s;])background-color:\s*var\(--softDent[^;]*!important/);
    expect(disabled).toMatch(/--button-color:\s*var\(--adaptiveGrey600[^;]*!important/);
    expect(disabled).toMatch(/--button-disabled-text-color:\s*var\(--adaptiveGrey600[^;]*!important/);
    expect(disabled).toMatch(/(^|[\s;])opacity:\s*1\s*!important/);
    expect(disabled).toMatch(/(^|[\s;])border:\s*0\s*!important/);
  });

  it('결정 ② · 비활성 TDS 버튼이 실제로 disabled 속성과 primary 키를 함께 든다 — 선택자가 닿는 마크업이다', () => {
    const markup = render(<Button disabled>저장</Button>);
    const tag = markup.slice(markup.lastIndexOf('<button', markup.indexOf('tds-mobile-button')), markup.indexOf('>', markup.indexOf('tds-mobile-button')) + 1);
    expect(tag).toContain('disabled=""');
    expect(tag).toMatch(/--button-background-color:\s*#3182f6/);
  });
});

/**
 * TDS danger 버튼(「정말 삭제」·로그아웃·탈퇴·차단 6곳) — TDS 기본 빨강은 종이 위 AA 미달이다(채움 흰 글자/#f04452 3.71 ·
 * weak #e42939/rgba(251,136,144,.15) 3.55). 검정 알약과 같은 수법으로 인라인 값을 키로 잡아 토큰 빨강으로 덮는다.
 * 대비 자체는 soft-contrast.test가 이 규칙에서 토큰을 뽑아 잰다 — 여기는 「규칙이 토큰을 쓴다」와 「키가 마크업에 있다」.
 */
describe('TDS danger 버튼 — 빨강 면·글자를 토큰으로', () => {
  const FILL = ".tds-mobile-button[style*='--button-background-color: #f04452']";
  const WEAK = ".tds-mobile-button[style*='--button-background-color: rgba(251, 136, 144, 0.15)']";
  const fill = rule(`${FILL} {`);
  const weak = rule(`${WEAK} {`);
  const off = rule(`${FILL}:disabled:not([aria-busy='true']) {`);

  it('채움 — 빨강 토큰 면 · filledInk 글자·로더 · TDS 그라디언트 끔', () => {
    expect(fill).not.toBe('');
    expect(fill).toMatch(/(^|[\s;])background-color:\s*var\(--adaptiveRed500\)\s*!important/);
    expect(fill).toMatch(/--button-background-color:\s*transparent\s*!important/);
    expect(fill).toMatch(/--button-color:\s*var\(--filledInk\)\s*!important/);
    expect(fill).toMatch(/--button-loader-color:\s*var\(--filledInk\)\s*!important/);
    expect(fill).toMatch(/--button-gradient-color:\s*transparent\s*!important/);
  });

  // 버튼은 전부 알약(사양 §5.3 「버튼·칩·탭바·원 999」) — TDS danger만 각진 8·14·16px로 남으면 같은 화면에서 혼자 튄다.
  it('채움·weak 모두 알약 모서리 999', () => {
    expect(fill).toMatch(/--button-border-radius:\s*999px\s*!important/);
    expect(weak).toMatch(/--button-border-radius:\s*999px\s*!important/);
  });

  it('weak — 옅은 빨강 면 · 빨강 글자 · 로더 빨강 토큰 · 눌림·로딩 딤은 끈다', () => {
    expect(weak).not.toBe('');
    expect(weak).toMatch(/(^|[\s;])background-color:\s*var\(--dangerSoft\)\s*!important/);
    expect(weak).toMatch(/--button-background-color:\s*transparent\s*!important/);
    expect(weak).toMatch(/--button-color:\s*var\(--adaptiveRed500\)\s*!important/);
    // 딤 레이어는 글자 위에 그려진다 — 빨강 딤(.26)이면 눌린 동안 빨강 글자/섞인 면 3.72로 AA 아래다. 눌림 피드백은 TDS scale만.
    expect(weak).toMatch(/--button-dimmer-pressed-background-color:\s*transparent\s*!important/);
    // 안 덮으면 loading 때 TDS 날 빨강 rgba(240, 68, 82, .26)이 얹힌다.
    expect(weak).toMatch(/--button-dimmer-loading-background-color:\s*transparent\s*!important/);
    expect(weak).toMatch(/--button-loader-color:\s*var\(--adaptiveRed500\)\s*!important/);
    expect(weak).toMatch(/--button-gradient-color:\s*transparent\s*!important/);
  });

  it('비활성 채움 — 주 버튼과 같은 옅은 면 + muted 글자 + 불투명도 1(로딩 중은 빨강 유지)', () => {
    expect(off).not.toBe('');
    expect(off).toMatch(/(^|[\s;])background-color:\s*var\(--softDent\)\s*!important/);
    expect(off).toMatch(/--button-color:\s*var\(--adaptiveGrey600\)\s*!important/);
    expect(off).toMatch(/--button-disabled-text-color:\s*var\(--adaptiveGrey600\)\s*!important/);
    expect(off).toMatch(/(^|[\s;])opacity:\s*1\s*!important/);
  });

  it('공백 두 벌 — 서버 렌더(`:#f04452`)·CSSOM(`: #f04452`) 어느 쪽도 빠지지 않는다', () => {
    // 규칙마다 두 선택자가 쉼표로 이어진 실제 목록을 단언한다 — 접두사 toContain은 비활성 규칙 선택자가 대신 채워 줬다.
    const esc = (v: string) => v.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const pair = (value: string, tail = '') =>
      new RegExp(
        `\\.tds-mobile-button\\[style\\*='--button-background-color:${esc(value)}'\\]${esc(tail)},\\s*` +
          `\\.tds-mobile-button\\[style\\*='--button-background-color: ${esc(value)}'\\]${esc(tail)}\\s*\\{`,
      );
    expect(css).toMatch(pair('#f04452'));
    expect(css).toMatch(pair('#f04452', ":disabled:not([aria-busy='true'])"));
    expect(css).toMatch(pair('rgba(251, 136, 144, 0.15)'));
  });

  it('danger 키를 든 규칙의 값에 날 색(hex·rgb)이 0이다 — 빨강은 토큰 한 곳에서만 정해진다', () => {
    const bodies = [...css.matchAll(/([^{}]+)\{([^}]*)\}/g)]
      .filter(([, sel]) => sel.includes('#f04452') || sel.includes('251, 136, 144'))
      .map(([, , body]) => body);
    expect(bodies.length).toBeGreaterThanOrEqual(3); // 채움 · weak · 비활성 — 뽑기가 실제로 잡는다
    for (const body of bodies) expect(body).not.toMatch(/#[0-9a-f]{3,8}\b|rgba?\(/i);
  });

  it('키가 실제 마크업에 있다 — 채움·weak·비활성 danger 버튼이 그 인라인 값을 든다', () => {
    const tagOf = (markup: string) => {
      const at = markup.lastIndexOf('<button', markup.indexOf('tds-mobile-button'));
      return markup.slice(at, markup.indexOf('>', at) + 1);
    };
    expect(tagOf(render(<Button color="danger">삭제</Button>))).toContain('--button-background-color:#f04452;');
    expect(tagOf(render(<Button color="danger" variant="weak">탈퇴</Button>))).toContain(
      '--button-background-color:rgba(251, 136, 144, 0.15);',
    );
    const disabled = tagOf(render(<Button color="danger" disabled>삭제</Button>));
    expect(disabled).toContain('disabled=""');
    expect(disabled).toContain('aria-busy="false"');
    expect(disabled).toContain('--button-background-color:#f04452;');
  });
});

describe('REQ-13 · TDS 입력칸은 면 없이 muted 경계다', () => {
  const KEY = '[style*="--text-field-box-background-color"]';
  const base = rule(`html ${KEY} {`);
  const focus = rule(`html ${KEY}:focus-within {`);
  const off = rule(`html ${KEY}:has(input:disabled) {`);

  it('기본 — 투명 면 · muted 1px · 반경 2, 전부 !important', () => {
    expect(base).not.toBe('');
    expect(base).toMatch(/--text-field-box-background-color:\s*transparent\s*!important/);
    expect(base).toMatch(/(^|[\s;])border:\s*1px solid var\(--adaptiveGrey600[^;]*!important/);
    expect(base).toMatch(/border-radius:\s*2px\s*!important/);
  });

  it('포커스 — 경계가 잉크로, TDS 파란 덧칠(::before) 끔', () => {
    expect(focus).not.toBe('');
    expect(focus).toMatch(/border-color:\s*var\(--adaptiveGrey800[^;]*!important/);
    expect(focus).toMatch(/--text-field-box-before-opacity:\s*0\s*!important/);
  });

  it('비활성 — 옅은 면 + muted 글자(TDS가 읽는 --adaptiveGrey400은 앱에 없다)', () => {
    expect(off).not.toBe('');
    expect(off).toMatch(/--text-field-box-background-color:\s*var\(--softDent[^;]*!important/);
    expect(off).toMatch(/--text-field-box-color:\s*var\(--adaptiveGrey600[^;]*!important/);
    expect(off).toMatch(/--text-field-box-webkit-text-fill-color:\s*var\(--adaptiveGrey600[^;]*!important/);
  });

  it('키가 실제 TDS 마크업에 인라인으로 있다 — 없으면 규칙이 조용히 안 먹는다(U-19 정적 판)', () => {
    const markup = render(<TextField variant="box" aria-label="검색" value="" onChange={() => {}} />);
    expect(markup).toMatch(/style="[^"]*--text-field-box-background-color/);
  });
});

/**
 * 결정 ① — 고른 칩(설정 「매일」·책방 필터 「전체」)은 잉크 채움이 아니라 <b>잉크 1px 테두리 + 잉크 글자 500</b>이다.
 * 검정 채움은 화면 주 동작(검정 알약) 하나뿐이어야 한다(REQ-06 — 화면당 검정 알약 ≤ 1). 안 고른 칩은 가는 선 테 + 보조 글자.
 * 고르기가 바뀌어도 칩 크기가 안 변하게 테 두께는 둘 다 1px이다.
 */
describe('결정 ① · 고른 칩은 채우지 않고 잉크 테 + 500이다', () => {
  const on = filterChipStyle(true);
  const off = filterChipStyle(false);

  it('고른 칩 — 투명 바탕 · 잉크 1px · 잉크 글자 500', () => {
    expect(on.background).toBe('transparent');
    expect(on.border).toMatch(/^1px solid var\(--adaptiveGrey800/);
    expect(on.color).toMatch(/^var\(--adaptiveGrey800/);
    expect(on.fontWeight).toBe(500);
  });

  it('안 고른 칩 — 투명 바탕 · 가는 선 1px · 보조 글자 400', () => {
    expect(off.background).toBe('transparent');
    expect(off.border).toMatch(/^1px solid var\(--adaptiveGrey200/);
    expect(off.color).toMatch(/^var\(--adaptiveGrey700/);
    expect(off.fontWeight).toBe(400);
  });

  it('어느 쪽도 잉크·강조 토큰으로 채우지 않는다 — 검정 채움은 주 버튼 하나뿐', () => {
    for (const chip of [on, off]) expect(String(chip.background)).not.toMatch(/adaptiveBlue|adaptiveGrey800/);
  });
});
