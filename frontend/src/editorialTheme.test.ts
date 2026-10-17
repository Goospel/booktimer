import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * 웹 「에디토리얼」 재테마 가드 — 미니앱 에디토리얼(2026-10-14 출시)과 같은 옷을 웹이 입었는지 본다.
 *
 * <p>PR-1(이 파일을 만든 PR)은 <b>글꼴 축</b>만 본다 — 두 패밀리(Noto Serif KR 500 · Noto Sans KR 400·500) ·
 * 굵기 400·500 · 글자 바닥 13px. 색·선·면(PR-2)과 홈 아치(PR-3)는 뒤 PR이 이 파일에 묶음을 더한다.
 * 설계: `claude-docs/plans/2026-10-16-web-editorial-retheme.md` §5-5 · §8 PR-1.
 *
 * <p>판정은 <b>주석을 걷은</b> CSS에서 한다 — 주석이 옛 값을 인용하면 단언이 공허해진다(T-205).
 * 판정 함수마다 <b>음성 대조</b>(일부러 틀린 문자열을 넣으면 빨강)와 <b>양성 대조</b>(읽은 선언 수 하한)를 같이 둔다 —
 * 정규식이 아무것도 못 읽어 빈 집합끼리 비교되면 영원히 초록이기 때문이다.
 */

const HERE = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const CSS_DIR = join(HERE, '..', '..', 'src', 'main', 'resources', 'static', 'css');
const APP_CSS = join(CSS_DIR, 'app.css');
const LANDING_CSS = join(CSS_DIR, 'landing.css');
/** 미니앱 글꼴 URL의 단일 출처 — 웹이 같은 문자열을 싣는지 파일을 읽어 대조한다(교차 채널 — 한쪽만 바뀌면 운다). */
const MINIAPP_FONTS = join(HERE, '..', '..', 'miniapp', 'src', 'fonts.ts');

const strip = (css: string) => css.replace(/\/\*[\s\S]*?\*\//g, '');
const read = (file: string) => strip(readFileSync(file, 'utf8'));
const BOTH = () => [read(APP_CSS), read(LANDING_CSS)].join('\n');

interface Rule { selector: string; body: string }

/** 규칙 블록 — `darkTokens.test.ts`의 `rules()`와 같은 꼴(`@media` 안쪽 규칙까지 꺼내고, 앞선 `@import …;`는 떼어 낸다). */
function rules(css: string): Rule[] {
    const out: Rule[] = [];
    const re = /([^{}]+)\{([^{}]*)\}/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(css))) {
        for (const selector of m[1].replace(/^[\s\S]*;/, '').split(',')) {
            out.push({ selector: selector.trim().replace(/\s+/g, ' '), body: m[2] });
        }
    }
    return out;
}

function decl(body: string, prop: string): string | null {
    const m = new RegExp(`(^|;)\\s*${prop}\\s*:\\s*([^;]+)`).exec(body);
    return m ? m[2].trim() : null;
}

/** 한 속성의 모든 선언 값(파일 전체 — 규칙·`@media` 안 가리지 않는다). */
const values = (css: string, prop: string) =>
    [...css.matchAll(new RegExp(`(?:^|[;{\\s])${prop}\\s*:\\s*([^;}]+)`, 'g'))].map((m) => m[1].trim());

/** `@import url('…')`의 주소. */
function importUrl(css: string): string {
    const m = /@import\s+url\(\s*['"]([^'"]+)['"]\s*\)/.exec(css);
    if (!m) throw new Error('app.css에 @import url(…)이 없다');
    return m[1];
}

/** 루트 글자 크기 — `html { font-size: 112.5% }` = 18px(2026-09-03 사용자 확정). rem 환산의 단일 출처. */
const ROOT_PX = 18;

/**
 * 글자 크기 값 하나가 13px 바닥 아래인가. px·rem 리터럴 · `clamp()` 첫 인자(하한) · `min()`·`max()` 최소 인자만 판정한다 —
 * em·%·`var()`·`calc()`는 계산값이라 여기선 모른다(화면에서 잰다 — 원장 U-19). 판정 못 하면 `false`(건너뜀).
 */
function belowFloor(value: string): boolean {
    const v = value.trim().replace(/\s*!important$/i, '');
    const lit = /^([\d.]+)(px|rem)$/.exec(v);
    if (lit) return (lit[2] === 'rem' ? Number(lit[1]) * ROOT_PX : Number(lit[1])) < 13;
    const cl = /^clamp\(\s*([\d.]+)(px|rem)\s*,/.exec(v);
    if (cl) return (cl[2] === 'rem' ? Number(cl[1]) * ROOT_PX : Number(cl[1])) < 13;
    // min()·max()는 인자 중 가장 작은 px·rem 리터럴로 본다(보수적 — max의 실제 값은 그보다 클 수 있다).
    if (/^(min|max)\(/.test(v)) {
        const px = [...v.matchAll(/([\d.]+)(px|rem)\b/g)].map((m) => (m[2] === 'rem' ? Number(m[1]) * ROOT_PX : Number(m[1])));
        return px.length > 0 && Math.min(...px) < 13;
    }
    return false;
}

/** 굵기 값 하나가 허용 목록 밖인가 — 실린 face가 400·500뿐이라 그 밖의 숫자는 합성 볼드로 번진다. */
const badWeight = (v: string) => !['400', '500', 'normal', 'inherit'].includes(v.trim());

describe('에디토리얼 글꼴 — 두 패밀리 · 굵기 400·500 · 글자 바닥 13', () => {
    it('REQ-04 · 글꼴 URL은 명조 500 + 고딕 400·500만 부른다', () => {
        const url = importUrl(read(APP_CSS));
        expect(url).toContain('family=Noto+Serif+KR:wght@500');
        expect(url).toContain('family=Noto+Sans+KR:wght@400;500');
        expect(url).toContain('display=swap');
        expect(url.match(/family=/g)).toHaveLength(2);

        const mini = /FONT_CSS_URL\s*=\s*'([^']+)'/.exec(readFileSync(MINIAPP_FONTS, 'utf8'));
        expect(mini, '미니앱 fonts.ts의 FONT_CSS_URL 리터럴을 못 읽음').not.toBeNull();
        expect(url).toBe(mini![1]);
    });

    it('REQ-03 · 고운 서체가 0이고 명조는 토큰 하나로만 쓴다', () => {
        const css = BOTH();
        expect(css.split('\n').filter((l) => l.includes('Gowun'))).toEqual([]);

        const root = rules(read(APP_CSS)).filter((r) => r.selector === ':root').map((r) => r.body).join(';');
        expect(decl(root, '--font-serif') ?? '').toMatch(/^'Noto Serif KR'/);

        // 토큰 정의를 뺀 나머지 font-family 값에 명조 리터럴이 0 — 한 자리에 직접 적으면 토큰이 둘로 갈린다.
        const families = values(css, 'font-family');
        expect(families.filter((v) => v.includes('Noto Serif KR'))).toEqual([]);
        // 양성 대조: 명조를 쓰던 40자리(app 39 + landing 1)가 전부 토큰으로 왔다.
        expect(families.filter((v) => v === 'var(--font-serif)').length).toBeGreaterThanOrEqual(40);
    });

    it('REQ-03 · 굵기는 400·500뿐이고 가짜 볼드가 없다', () => {
        // 음성 대조 — 판정 함수가 700을 실제로 빨강으로 친다.
        expect(values('a { font-weight: 700; }', 'font-weight').some(badWeight)).toBe(true);

        // `font:` 단축 선언은 굵기·크기를 함께 싣는다 — inherit 말고는 금지(가드 우회로 차단).
        expect(values('.x { font: 700 12px/1 sans-serif; font-size: 13px; }', 'font')).toEqual(['700 12px/1 sans-serif']);
        const shorthands = values(BOTH(), 'font');
        expect(shorthands.length, '`font:` 선언을 못 읽었다 — 정규식 고장').toBeGreaterThanOrEqual(18);
        expect(shorthands.filter((v) => v !== 'inherit')).toEqual([]);

        const weights = values(BOTH(), 'font-weight');
        expect(weights.length, '굵기 선언을 거의 못 읽었다 — 정규식 고장').toBeGreaterThanOrEqual(120);
        expect(weights.filter(badWeight)).toEqual([]);

        const app = rules(read(APP_CSS));
        const body = app.filter((r) => r.selector === 'body').map((r) => r.body).join(';');
        // 굵기 합성만 끈다 — Noto KR엔 italic face가 없어 `none`이면 편집기 `<em>`(Ctrl+I)·명언 기울임이 똑바로 그려진다.
        expect(decl(body, 'font-synthesis')).toBe('style');
        expect(decl(body, 'font-family') ?? '').toMatch(/^"Noto Sans KR"/);
        const strong = app.filter((r) => r.selector === 'b' || r.selector === 'strong');
        expect(strong.map((r) => r.selector).sort()).toEqual(['b', 'strong']);
        for (const r of strong) expect(decl(r.body, 'font-weight')).toBe('500');
    });

    it('REQ-24 · 글자 크기는 13px 이상이다', () => {
        // 음성 대조 — 바닥 바로 아래 값들이 빨강, 경계값 13px은 초록.
        expect(belowFloor('12.5px')).toBe(true);
        expect(belowFloor('.72rem')).toBe(true); // 12.96px
        expect(belowFloor('clamp(.72rem, 1vw, 1rem)')).toBe(true);
        expect(belowFloor('min(11px, 3vw)')).toBe(true);
        expect(belowFloor('max(.6rem, 1vw)')).toBe(true);
        expect(belowFloor('min(13px, 3vw)')).toBe(false);
        expect(belowFloor('13px')).toBe(false);
        expect(belowFloor('.85em')).toBe(false); // 계산값 — 건너뜀(U-19)
        expect(belowFloor('12px !important')).toBe(true); // !important가 판정을 비켜 가지 않는다
        expect(belowFloor('13px !important')).toBe(false);

        const sizes = values(BOTH(), 'font-size');
        expect(sizes.length, 'font-size 선언을 거의 못 읽었다 — 정규식 고장').toBeGreaterThanOrEqual(280);
        expect(sizes.filter(belowFloor)).toEqual([]);
    });

    it('REQ-24 · 명언 작가 이름(em)은 좁은 화면에서도 13px 아래로 안 내려간다', () => {
        // `.brand-quote`가 clamp로 줄어드는 폭(640 미만)에서 .78em이 13px 아래로 계산됐다(재검토 실측) — max()로 바닥을 함께 건다.
        const m = /\.brand-quote-author\s*\{([^}]*)\}/.exec(read(APP_CSS));
        expect(m, '.brand-quote-author 규칙을 못 찾음').not.toBeNull();
        expect(decl(m![1], 'font-size') ?? '').toMatch(/^max\(13px,\s*[\d.]+em\)$/);
    });
});
