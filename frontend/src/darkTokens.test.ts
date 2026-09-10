import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * 다크 팔레트는 <b>라이트 토큰과 짝이 맞아야 한다</b>.
 *
 * <p>다크는 `:root`의 색 토큰을 `:root[data-theme="dark"]` 블록 하나로 덮는 방식이다. 그래서 라이트에
 * 토큰을 새로 추가하고 다크를 잊으면 그 자리만 <b>크림색이 다크 위에 남는다</b> — 화면을 다크로 열어
 * 그 페이지까지 가봐야 보이고, 대개는 안 간다. 양방향으로 세어 한쪽만 늘어난 순간 여기서 운다.
 *
 * <p>판정은 <b>블록 안에서만</b> 한다. 파일 전체에 `toContain('--bg: #1E1C18')` 같은 부분문자열
 * 단언을 걸면 어디에 적혀 있어도 통과하는 죽은 계측기가 된다(T-205) — 공용 CSS 값에 걸려 늘 초록인
 * 단언에 이 레포는 이미 세 번 당했다.
 */

const HERE = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const APP_CSS = join(HERE, '..', '..', 'src', 'main', 'resources', 'static', 'css', 'app.css');

const LIGHT_SELECTOR = ':root';
const DARK_SELECTOR = ':root[data-theme="dark"]';

/** 색을 실제로 갖는 값 — hex·rgb(a)·data URI. `var(--x)` 별칭과 `14px`류는 짝 맞춤 대상이 아니다. */
const HAS_COLOR = /#[0-9a-fA-F]{3,8}\b|rgba?\(|url\(/;

/**
 * `app.css`를 규칙 블록으로 쪼갠다 — 주석을 먼저 걷고, `@media` 안쪽은 안쪽 블록이 잡힌다.
 *
 * <p>⚠️ 셀렉터 캡처에는 <b>앞선 세미콜론 at-rule이 통째로 딸려온다</b>. 파일 첫 규칙의 셀렉터가
 * `@import url(…); :root`로 잡혀 라이트 토큰이 0개가 됐고, 「계측기 생존」 단언이 그걸 잡았다
 * (그 단언이 없었으면 짝 맞춤이 빈 집합끼리 비교되며 <b>영원히 초록</b>이었다). 마지막 `;` 뒤만 남긴다.
 */
function rules(): Array<{ selector: string; body: string }> {
    const css = readFileSync(APP_CSS, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    const out: Array<{ selector: string; body: string }> = [];
    const re = /([^{}]+)\{([^{}]*)\}/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(css))) {
        const selector = m[1].replace(/^[\s\S]*;/, '').trim().replace(/\s+/g, ' ');
        out.push({ selector, body: m[2] });
    }
    return out;
}

function blockBody(selector: string): string {
    return rules()
        .filter((r) => r.selector === selector)
        .map((r) => r.body)
        .join(';');
}

/** 블록 안에서 색 값을 갖는 커스텀 속성 이름들. */
function colorTokens(selector: string): string[] {
    return blockBody(selector)
        .split(';')
        .map((decl) => /^\s*(--[\w-]+)\s*:\s*(.+)$/s.exec(decl))
        .filter((m): m is RegExpExecArray => !!m && HAS_COLOR.test(m[2]))
        .map((m) => m[1])
        .sort();
}

/** 블록 안의 일반 선언(커스텀 속성이 아닌 것) 값 — `color-scheme` 같은 것. */
function declaration(selector: string, property: string): string | null {
    const m = new RegExp(`(^|;)\\s*${property}\\s*:\\s*([^;]+)`).exec(blockBody(selector));
    return m ? m[2].trim() : null;
}

describe('다크 토큰 짝 맞춤', () => {
    const light = colorTokens(LIGHT_SELECTOR);
    const dark = colorTokens(DARK_SELECTOR);

    it('파서가 라이트 토큰을 실제로 잡는다 — 0개면 늘 통과하는 빈 가드다 (계측기 생존)', () => {
        expect(light.length).toBeGreaterThanOrEqual(35);
    });

    it('파서가 다크 토큰을 실제로 잡는다 (계측기 생존)', () => {
        expect(dark.length).toBeGreaterThanOrEqual(35);
    });

    it('다크가 안 덮은 라이트 색 토큰이 없다', () => {
        expect(light.filter((t) => !dark.includes(t))).toEqual([]);
    });

    it('라이트에 없는 토큰을 다크가 만들지 않는다 (오타·유령 토큰 차단)', () => {
        expect(dark.filter((t) => !light.includes(t))).toEqual([]);
    });
});

describe('color-scheme 선언 — 브라우저 강제 다크 차단', () => {
    /**
     * `only light`의 `only`가 핵심이다. 크롬 Auto Dark Theme는 「다크를 지원하지 않는 페이지」만
     * 뒤집는데, `light`만 선언하면 그건 여전히 「라이트만 그린다」는 뜻이라 대상으로 남는다.
     * `only`가 붙어야 UA 자동 변환에 대한 opt-out이 된다(§7 U-1에서 실측).
     */
    it(':root가 color-scheme: only light 로 강제 다크를 거부한다', () => {
        expect(declaration(LIGHT_SELECTOR, 'color-scheme')).toBe('only light');
    });

    it('다크 블록은 color-scheme: dark 로 네이티브 폼·스크롤바까지 다크로 넘긴다', () => {
        expect(declaration(DARK_SELECTOR, 'color-scheme')).toBe('dark');
    });
});

describe('인쇄는 항상 라이트', () => {
    it('다크 블록이 @media not print 안에 있다', () => {
        const css = readFileSync(APP_CSS, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
        const at = css.indexOf(DARK_SELECTOR);
        const guard = css.lastIndexOf('@media not print', at);

        expect(at).toBeGreaterThan(0);
        expect(guard).toBeGreaterThan(0);
        // 가드와 다크 블록 사이에 닫는 `}`만 있고 다른 `@media`가 끼지 않았는지 — 여는 중괄호 이후 구간에
        // 다른 at-rule이 없어야 한다.
        expect(css.slice(guard, at)).not.toMatch(/@media(?! not print)/);
    });
});
