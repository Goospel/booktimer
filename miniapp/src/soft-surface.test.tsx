import { readFileSync } from 'node:fs';

import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { bigShadow } from './soft-guard';
import { DENT, PUFF, SOFT_OUTLINE, SOFT_ROW, sectionStyle } from './ui';

/**
 * Soft 표면 상수 — 부푼(PUFF)·눌린(DENT) 면의 그림자는 <b>CSS 변수 경유</b>라야 한다.
 *
 * <p>리터럴로 적으면 밤(독서등)·공부 모드가 인라인을 이기려고 자리마다 `!important` 규칙을 둬야 한다 —
 * 연필선이 겪은 고질의 재판이다. 그리고 낮의 흰 하이라이트(`rgba(255,255,255,.95)`)가 밤 카드에 그대로
 * 새어 어두운 화면에 흰 테가 뜬다. 변수면 `body` 클래스 한 벌이 값만 갈아 끼운다.
 */
describe('Soft 표면 상수', () => {
  const markup = renderToStaticMarkup(<section style={sectionStyle} />);

  it('섹션 카드가 부푼 그림자를 변수로 싣는다', () => {
    expect(markup).toContain('box-shadow:'); // 그림자 자체가 있다(아래 부정 단언이 공허하지 않음)
    expect(markup).toContain('box-shadow:var(--puffShadow');
  });

  it('섹션 카드에 연필선이 되살아나지 않는다', () => {
    expect(markup).toContain('box-shadow:');
    expect(markup).not.toContain('border-image');
  });

  it('섹션 카드는 26px 둥근 면이다', () => {
    expect(sectionStyle.borderRadius).toBe(26);
    expect(sectionStyle.padding).toBe(18);
  });

  it('PUFF 그림자는 변수 경유다 — 리터럴이면 밤에 흰 빛이 샌다', () => {
    expect(PUFF.boxShadow.startsWith('var(--puffShadow')).toBe(true);
    expect(PUFF.background).toContain('var(--adaptiveGrey100');
  });

  it('DENT는 안으로 눌린 그림자다 — 폴백 값이 inset으로 시작한다', () => {
    expect(DENT.boxShadow.startsWith('var(--dentShadow, inset')).toBe(true);
    expect(DENT.background).toContain('var(--softDent');
  });

  it('보조 손잡이·시트 행은 1.5px 실선이다(규칙 1 「보조 버튼은 1.5px 테두리」)', () => {
    expect(SOFT_OUTLINE.border).toMatch(/^1\.5px solid var\(--adaptiveBlue700/);
    expect(SOFT_ROW.border).toMatch(/^1\.5px solid var\(--adaptiveGrey200/);
  });
});

/** 주석을 걷은 global.css — 주석이 속성 이름을 인용하면 가드가 거짓으로 갈린다. */
const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');

/** `head` 바로 뒤의 `{…}`를 중괄호 짝을 세어 통째로 꺼낸다(@keyframes·@media처럼 안에 블록이 또 있어도). */
function blockAfter(head: string): string {
  const start = css.indexOf('{', css.indexOf(head));
  if (css.indexOf(head) < 0 || start < 0) return '';
  let depth = 0;
  for (let i = start; i < css.length; i += 1) {
    if (css[i] === '{') depth += 1;
    if (css[i] === '}' && --depth === 0) return css.slice(start + 1, i);
  }
  return '';
}

/**
 * 메달 애니메이션 — 움직이는 속성은 `transform`·`opacity`뿐이다(T-176: 발광 `box-shadow` 무한 반복이
 * 실기기에서만 무너졌다). 데스크톱 크롬은 원리상 이 클래스를 못 잡으니 소스가 게이트다.
 */
describe('메달 애니메이션 가드 (T-176)', () => {
  const frames = blockAfter('@keyframes medal-pop');

  it('키프레임 안의 속성은 transform·opacity뿐이다', () => {
    expect(frames).not.toBe(''); // 추출이 됐다(아래 부재 단언이 공허하지 않음)
    const props = [...frames.matchAll(/([a-z-]+)\s*:/g)].map((m) => m[1]);
    expect(props.length).toBeGreaterThan(0);
    expect(props.filter((p) => p !== 'transform' && p !== 'opacity')).toEqual([]);
  });

  it('한 번만 돈다 — 무한 반복이 아니고 끝 프레임에 멈춘다', () => {
    const rule = blockAfter('.medal-pop');
    expect(rule).toContain('animation:');
    expect(rule).toContain('both');
    // 반복 횟수는 이름·시간·곡선 말고 남는 맨 숫자나 `infinite`다 — 「1」 말고는 없어야 한다(3도 무한과 같은 병).
    const value = rule.match(/animation:([^;]*)/)?.[1] ?? '';
    const tokens = value.replace(/cubic-bezier\([^)]*\)/g, '').trim().split(/\s+/);
    expect(tokens).toContain('medal-pop'); // 값을 제대로 쪼갰다(아래 부재 단언이 공허하지 않음)
    expect(tokens.filter((t) => /^(infinite|\d*\.?\d+)$/.test(t) && t !== '1')).toEqual([]);
    expect(rule).not.toMatch(/animation-iteration-count/);
  });

  it('움직임 줄이기 설정에서는 멈춘다', () => {
    // 경계(?![\w-]) — `.medal-popX` 같은 다른 이름이 부분 문자열로 통과하지 않게(돌연변이 실측에서 샜다).
    const selector = /\.medal-pop(?![\w-])[^{]*\{[^}]*animation:\s*none/;
    const reduced = [...css.matchAll(/@media \(prefers-reduced-motion: reduce\)/g)]
      .map((m) => blockAfter(css.slice(m.index)))
      .filter((b) => selector.test(b));
    expect(reduced).toHaveLength(1);
  });
});

/**
 * 독서등(측정 중 밤)의 히어로 — 리뷰(PR-1)가 남긴 경고 두 가지를 한 규칙이 닫는다.
 *
 * <p>히어로는 인라인 `box-shadow: var(--puffShadow)`를 싣는다. 그런데 ⓐ 인라인은 `.lamp-page`의 등불 글로우
 * 규칙을 덮고, ⓑ `.lamp-page`가 카드 안쪽 타일을 위해 `--puffShadow`를 <b>낮값으로 재선언</b>하므로 히어로
 * 자신도 낮 그림자(흰 .95 하이라이트)를 받아 밤 캔버스 위에 흰 테가 뜬다. 등불 글로우가 `!important`여야
 * 둘 다 풀린다 — 밤엔 글로우가 인라인을 이기고, 낮엔 규칙 자체가 안 걸린다.
 */
describe('독서등 히어로 그림자 — 등불 글로우가 부푼 그림자를 이긴다', () => {
  const lampPage = blockAfter('body.reading-lamp .lamp-page');
  const shadow = lampPage.match(/(^|[;\s])box-shadow:([^;]*);/)?.[2] ?? '';

  it('글로우 선언이 !important다 — 아니면 히어로의 인라인 var(--puffShadow)가 이긴다', () => {
    expect(shadow).toContain('rgba(255, 220, 160'); // 등불 글로우가 그 선언이다
    expect(shadow.trim().endsWith('!important')).toBe(true);
  });

  it('글로우엔 흰 하이라이트가 없다 — 밤 캔버스에 흰 테가 뜨지 않는다', () => {
    expect(shadow).not.toBe('');
    expect(shadow).not.toMatch(/255,\s*255,\s*255/);
  });
});

/**
 * `bigShadow` 계측기 자기검증 — 화면 테스트들이 반복 행에 「큰 그림자 없음」을 걸 때 쓰는 판정이다. 부정
 * 단언에만 쓰이므로 <b>양성 대조</b>가 없으면 「언제나 false」인 고장이 초록으로 숨는다.
 */
describe('bigShadow 계측기 자기검증', () => {
  const tag = (style: object) => renderToStaticMarkup(<div style={style} />);

  it('부푼 그림자(변수 경유)와 blur 8px 이상 리터럴을 잡는다', () => {
    expect(bigShadow(tag(sectionStyle))).toBe(true);
    expect(bigShadow(tag({ boxShadow: 'var(--puffShadow)' }))).toBe(true);
    expect(bigShadow(tag({ boxShadow: '5px 5px 12px rgba(94,122,90,.18)' }))).toBe(true);
    expect(bigShadow(tag({ boxShadow: 'inset 0 1px 0 #fff, 0 2px 8px rgba(0,0,0,.2)' }))).toBe(true);
  });

  // 리뷰 돌연변이에서 살아남은 네 갈래 — 판정은 닫힌 쪽이다(모르면 크다).
  it.each([
    ['다른 변수의 폴백 속 큰 그림자', 'var(--ring, 0 8px 24px rgba(0,0,0,.1))'],
    ['부푼 그림자가 아닌 다른 그림자 변수', 'var(--cardShadow)'],
    ['그림자 변수의 폴백(이름은 dent가 아님)', 'var(--softShadow, 0 1px 2px #000)'],
    ['rem 단위(0.75rem = 12px)', '0 0.25rem 0.75rem rgba(0,0,0,.1)'],
    ['em 단위(1em = 16px)', '0 0 1em #000'],
    ['calc()', '0 2px calc(4px + 6px) #000'],
    ['대문자 PX', '0 2PX 12PX rgba(0,0,0,.1)'],
  ])('%s → 크다', (_, boxShadow) => {
    expect(bigShadow(tag({ boxShadow }))).toBe(true);
  });

  it.each([
    ['눌린 그림자 변수(허용표)', 'var(--dentShadow)'],
    ['rem이지만 작다(0.25rem = 4px)', '0 1px 0.25rem #000'],
    ['색 변수만 있는 링', '0 0 0 2px var(--adaptiveGrey100), 0 0 0 4.5px var(--adaptiveBlue700)'],
  ])('%s → 작다', (_, boxShadow) => {
    expect(bigShadow(tag({ boxShadow }))).toBe(false);
  });

  it('링·눌린 면·그림자 없음은 통과한다', () => {
    expect(bigShadow(tag({ boxShadow: '0 0 0 3px var(--adaptiveBlue700)' }))).toBe(false);
    expect(bigShadow(tag(DENT))).toBe(false);
    expect(bigShadow(tag(SOFT_ROW))).toBe(false);
    expect(bigShadow(tag({}))).toBe(false);
  });
});
