import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { fileURLToPath } from 'node:url';

import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { bigShadow, smallShadow } from './soft-guard';
import { sourceFiles, stripComments } from './source-scan';
import * as ui from './ui';
import { FIELD, FLOAT, OUTLINE_PILL, RULE_ROW, TRACK, sectionStyle } from './ui';

/**
 * 에디토리얼 표면 상수 — 면·그림자가 없고 경계는 선과 여백이다(spec §5.3). 떠 있는 것(탭바·토스트·스트립·말풍선)만
 * 미색 + 잉크 1px이다.
 *
 * <p>진짜 실패: 카드 면·부푼 그림자가 상수 한 줄로 되살아나는 것. 옛 이름(`PUFF`·`DENT`·`SOFT_*`)이 남아 있으면 「부푼 면」
 * 이름의 상수가 면 없는 것을 뜻해 주석·가드가 거짓말을 하므로 export 자체가 없어야 한다.
 */
describe('REQ-05 · 표면 상수', () => {
  it('FLOAT = 미색 + 잉크 1px, 그림자 없음', () => {
    expect(FLOAT.background).toContain('var(--adaptiveGrey100');
    expect(FLOAT.border).toMatch(/^1px solid var\(--adaptiveGrey800/);
    expect('boxShadow' in FLOAT).toBe(false);
  });

  it('OUTLINE_PILL = 강조 토큰 1px 알약(공부 모드에서 파란 잉크로 따라온다)', () => {
    expect(OUTLINE_PILL.border).toMatch(/^1px solid var\(--adaptiveBlue700/);
    expect(OUTLINE_PILL.borderRadius).toBe(999);
    expect(OUTLINE_PILL.background).toBe('transparent');
  });

  it('RULE_ROW는 아래 가는 선만 — 면도 사방 테도 없다', () => {
    expect(RULE_ROW.borderBottom).toMatch(/^1px solid var\(--adaptiveGrey200/);
    expect(RULE_ROW.border).toBe(0);
    expect(RULE_ROW.background).toBe('transparent');
  });

  it('TRACK은 가는 선 색 막대, FIELD는 muted 1px 경계 + 투명', () => {
    expect(TRACK.background).toContain('var(--adaptiveGrey200');
    expect(FIELD.border).toMatch(/^1px solid var\(--adaptiveGrey600/);
    expect(FIELD.background).toBe('transparent');
  });

  it('sectionStyle에 면·그림자·반경 키가 없다', () => {
    for (const key of ['background', 'boxShadow', 'borderRadius']) expect(key in sectionStyle).toBe(false);
    expect(renderToStaticMarkup(<section style={sectionStyle} />)).not.toContain('box-shadow');
  });

  it('옛 표면 상수(PUFF·DENT·SOFT_OUTLINE·SOFT_ROW)가 export되지 않는다', () => {
    for (const name of ['PUFF', 'DENT', 'SOFT_OUTLINE', 'SOFT_ROW']) expect(name in ui).toBe(false);
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
 * `bigShadow` 계측기 자기검증 — 화면 테스트들이 반복 행에 「큰 그림자 없음」을 걸 때 쓰는 판정이다. 부정
 * 단언에만 쓰이므로 <b>양성 대조</b>가 없으면 「언제나 false」인 고장이 초록으로 숨는다.
 */
describe('bigShadow 계측기 자기검증', () => {
  const tag = (style: object) => renderToStaticMarkup(<div style={style} />);

  it('부푼 그림자(변수 경유)와 blur 8px 이상 리터럴을 잡는다', () => {
    expect(bigShadow(tag({ boxShadow: 'var(--puffShadow)' }))).toBe(true);
    expect(bigShadow(tag({ boxShadow: '5px 5px 12px rgba(112,96,64,.14)' }))).toBe(true);
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
    expect(bigShadow(tag(FLOAT))).toBe(false);
    expect(bigShadow(tag(RULE_ROW))).toBe(false);
    expect(bigShadow(tag(TRACK))).toBe(false);
    expect(bigShadow(tag({}))).toBe(false);
  });
});

/**
 * 그림자 예산(설계 §6 · PR-5 테스트 #5) — 큰 흐림(blur ≥ 8px 또는 부푼 그림자 변수)이 서는 자리를 파일별로 센다.
 *
 * <p>판정은 `bigShadow` 하나다(설계 원시 정규식은 `var()` 경유와 단위 없는 0을 놓친다). 대상 키는 `boxShadow:`와
 * 타이머 원의 `ring:`(원 그림자가 `timerActionView`에 산다). 상수 펼치기(`...PUFF`)는 키가 없어 여기선 안
 * 세고 — 상수의 정의 자체(ui.tsx)가 한 번 세진다 — 아래 「부푼 면 사용처」 표가 따로 센다.
 *
 * <p>진짜 실패: 목록 행·격자 칸·피드 행에 큰 흐림이 새는 것(요소 수 × blur → 저사양 안드로이드 스크롤 중
 * 타일 재래스터, 안드로이드 72%). 새 자리가 생기면 이 표를 근거와 함께 고친다.
 *
 * <p>ponytail: 키 이름으로 찾으므로 `const X = '0 8px 24px …'` 뒤 `boxShadow: X`처럼 값을 딴 상수로 빼면 못 본다
 * (지금 그런 자리는 `TILE_HIGHLIGHT`·`TODAY_RING`뿐이고 둘 다 작다). 그 꼴이 늘면 문자열 리터럴 전수로 넓힌다.
 */
describe('REQ-05 · 큰 흐림 그림자가 0이다', () => {
  // 에디토리얼 PR-1 — 큰 흐림은 0자리다(REQ-05 이펙트 0). 새 자리가 생기면 근거와 함께 이 표를 고친다.
  const ALLOWED: Record<string, number> = {};


  const root = fileURLToPath(new URL('.', import.meta.url));
  const files = sourceFiles(root);

  /** 키 뒤의 값 — 줄 끝까지, 줄이 비면 다음 줄(`boxShadow:\n  cond ? … : …`). 따옴표는 걷는다. */
  function values(src: string): string[] {
    return [...src.matchAll(/\b(?:boxShadow|ring)\s*:[ \t]*([^\n]*)(?:\n([^\n]*))?/g)].map(
      (m) => (m[1].trim() === '' ? m[2] ?? '' : m[1]).replace(/['"`]/g, ''),
    );
  }

  it('파일을 실제로 훑는다 — 스캔 0개는 공허한 초록', () => {
    expect(files.length).toBeGreaterThan(10);
    // 양성 대조 — 스캐너가 이미 아는 큰 그림자를 잡는다(아래 표 비교가 「언제나 0」 고장이 아님)
    expect(values("boxShadow: '0 6px 20px rgba(0, 0, 0, 0.25)',").map((v) => bigShadow(`box-shadow:${v}`))).toEqual([true]);
    expect(values('boxShadow:\n  on ? "0 0 0 2px var(--x)" : undefined,').map((v) => bigShadow(`box-shadow:${v}`))).toEqual([false]);
  });

  it('파일별 큰 흐림 수가 허용표와 같다', () => {
    const counts: Record<string, number> = {};
    for (const file of files) {
      const n = values(stripComments(readFileSync(file, 'utf8'))).filter((v) => bigShadow(`box-shadow:${v}`)).length;
      if (n > 0) counts[basename(file)] = n;
    }
    expect(counts).toEqual(ALLOWED);
  });

  /**
   * 작은 흐림(blur 1~7px)도 0자리다 — 큰 흐림 표만 있으면 「살짝 뜬」 그림자가 초록으로 샌다(리뷰 돌연변이 M31 코치마크
   * 말풍선 `0 2px 4px` · M32 시트 패널 `0 -2px 6px`가 둘 다 살아남았다). 링(`0 0 0 Npx`, blur 0)은 선이라 세지 않는다
   * — 잔디 오늘 칸·격자 고른 칸 outline이 그 자리다. 새 자리가 생기면 근거와 함께 이 표를 고친다.
   */
  it('파일별 작은 흐림 수가 허용표({})와 같다 — 링은 제외', () => {
    const SMALL_ALLOWED: Record<string, number> = {};
    const small = (v: string) => smallShadow(`box-shadow:${v}`);
    // 양성 대조 — M31·M32 꼴과 rem 환산(0.25rem = 4px)을 잡는다
    expect(['0 2px 4px rgba(29, 27, 24, 0.12)', '0 -2px 6px rgba(0,0,0,.1)', '0 1px 0.25rem #000'].map(small)).toEqual([
      true,
      true,
      true,
    ]);
    // 음성 대조 — 링(blur 0)·inset 하이라이트·흐림 없음·큰 흐림(큰 흐림 표 몫)은 작은 흐림이 아니다
    expect(
      ['0 0 0 2px var(--adaptiveBlue700)', 'inset 0 1px 0 #fff', 'none', '', '0 8px 24px rgba(0,0,0,.2)'].map(small),
    ).toEqual([false, false, false, false, false]);
    const counts: Record<string, number> = {};
    for (const file of files) {
      const n = values(stripComments(readFileSync(file, 'utf8'))).filter(small).length;
      if (n > 0) counts[basename(file)] = n;
    }
    expect(counts).toEqual(SMALL_ALLOWED);
  });

  /**
   * 떠 있는 면(FLOAT) 사용처 — `...FLOAT` 펼치기와 `style={FLOAT}` 직접 참조를 파일별로 센다. 떠 있는 것은 하나뿐인 면
   * (탭바·시작 토스트·액션/잠금 스트립·게스트 안내 스트립·코치마크 말풍선)에만 선다 — 반복 행·화면 카드에 새면 이 표가 어긋난다.
   */
  it('떠 있는 면(FLOAT) 사용처가 허용표와 같다', () => {
    const FLOAT_USES: Record<string, number> = {
      'App.tsx': 4, // 에러 스트립 · 잠금 안내 · 시작 토스트 · 탭바
      'GuestHome.tsx': 1, // 안내 스트립
      'coachmark.tsx': 1, // 말풍선
    };
    const USE = /(\.\.\.|=\{\s*)FLOAT\b/g;
    // 양성 대조 — 식이 펼치기와 직접 참조를 둘 다 잡는다
    expect('{ ...FLOAT, a: 1 } style={FLOAT} const FLOAT = {'.match(USE)).toHaveLength(2);
    const counts: Record<string, number> = {};
    for (const file of files) {
      const n = (stripComments(readFileSync(file, 'utf8')).match(USE) ?? []).length;
      if (n > 0) counts[basename(file)] = n;
    }
    expect(counts).toEqual(FLOAT_USES);
  });
});
