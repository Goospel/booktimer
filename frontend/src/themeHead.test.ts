import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * 테마 부트 조각은 <b>모든 페이지의 head에 있어야 한다</b>.
 *
 * <p>다크 저장값을 첫 페인트 전에 심는 인라인 한 줄이 빠진 페이지는 <b>라이트로 떴다가 뒤집힌다</b>
 * (FOUC). 이 앱엔 공통 레이아웃이 없어 36개 템플릿이 각자 head를 쓰므로, 새 페이지를 만드는 사람이
 * 한 줄을 잊는 순간 그 페이지만 조용히 깜빡인다 — 눈으로는 새 페이지를 열어봐야만 보인다.
 * `analytics :: gtag`가 36곳에 손으로 들어가 있는 것과 같은 구조의 함정이라, 여기서 전수 스캔한다.
 *
 * <p>두 번째 단언은 <b>키 리터럴 동기</b>다. 부트 스니펫은 `theme.js`를 import 하지 않고
 * `localStorage.getItem('bt-theme')`를 직접 읽는다(모듈 로드를 기다리면 FOUC가 나므로 어쩔 수 없다).
 * 즉 같은 문자열이 두 파일에 <b>따로</b> 적혀 있어, 한쪽만 바꾸면 저장은 되는데 부팅이 안 읽는
 * 무성 고장이 된다.
 */

const HERE = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const RESOURCES = join(HERE, '..', '..', 'src', 'main', 'resources');
const TEMPLATES = join(RESOURCES, 'templates');
const THEME_JS = join(RESOURCES, 'static', 'js', 'theme.js');
const THEME_HEAD = join(TEMPLATES, 'fragments', 'theme-head.html');

/** include 한 줄. Thymeleaf 표현식 안의 공백은 자유라 조각 이름만 집는다. */
const INCLUDE = /fragments\/theme-head\s*::\s*boot/;

/**
 * 실제 페이지 = `<meta charset>`이 있는 템플릿.
 *
 * <p>`fragments/`·`analytics.html`·`ads.html`은 조각 컨테이너라 head가 없다 — 그쪽까지 강제하면
 * 규칙이 아니라 예외 목록이 커진다.
 *
 * <p>⚠️ `=`까지 요구한다. 처음엔 `&lt;meta charset`만 봤는데, 그러면 <b>주석에서 그 태그를 언급한
 * 조각</b>이 페이지로 잡힌다 — `theme-head.html`이 「charset 메타 바로 다음에 넣어라」라고 스스로
 * 적어 두는 바람에 자기 자신을 위반으로 신고했다.
 */
function pageTemplates(dir: string = TEMPLATES): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = join(dir, entry.name);
        if (entry.isDirectory()) return pageTemplates(full);
        if (!entry.name.endsWith('.html')) return [];
        return /<meta\s+charset\s*=/i.test(readFileSync(full, 'utf8')) ? [full] : [];
    });
}

describe('테마 부트 조각 전수 가드', () => {
    const pages = pageTemplates();

    it('스캔이 페이지를 실제로 잡는다 — 0개면 늘 통과하는 빈 가드다 (계측기 생존)', () => {
        expect(pages.length).toBeGreaterThanOrEqual(30);
    });

    it('모든 페이지 head가 theme-head :: boot 를 include 한다', () => {
        const missing = pages
            .filter((file) => !INCLUDE.test(readFileSync(file, 'utf8')))
            .map((file) => file.split(/[\\/]/).slice(-2).join('/'));

        expect(missing).toEqual([]);
    });

    it("부트 스니펫과 theme.js가 같은 저장 키 리터럴을 쓴다", () => {
        const key = /export const KEY\s*=\s*'([^']+)'/.exec(readFileSync(THEME_JS, 'utf8'))?.[1];

        expect(key).toBeTruthy();
        expect(readFileSync(THEME_HEAD, 'utf8')).toContain(`'${key}'`);
    });

    it('부트 스크립트에 CSP nonce가 붙어 있다 — 없으면 strict CSP가 통째로 막는다', () => {
        const html = readFileSync(THEME_HEAD, 'utf8');
        const inline = /<script(?![^>]*\bsrc)[^>]*>/.exec(html)?.[0] ?? '';

        expect(inline).toMatch(/nonce=\$\{cspNonce\}/);
    });
});
