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

// ═══════════════════════════════════════════════════════════════════════════════════════════
// PR-2 「옷 전역」 — 색 · 선 · 면 · 버튼 · 입력칸 · 떠 있는 것 · PWA (설계 §5-2~5-9 · §8 PR-2).
// 토큰 값 자체(낮·밤·공부·독서등)와 대비는 darkTokens.test.ts가 같은 블록 파서로 잰다 — 여기는 「규칙」이다.
// ═══════════════════════════════════════════════════════════════════════════════════════════

const ROOT_DIR = join(HERE, '..', '..');
const STATIC_DIR = join(ROOT_DIR, 'src', 'main', 'resources', 'static');
const MINI_CSS = join(ROOT_DIR, 'miniapp', 'src', 'global.css');
const MINI_UI = join(ROOT_DIR, 'miniapp', 'src', 'ui.tsx');
/** 인라인 스타일로 칠하는 정적 JS — darkTokens.test.ts 「정적 JS 인라인 스타일의 raw 색」과 같은 목록. */
const INLINE_JS = ['pwa-install.js', join('js', 'theme.js'), join('js', 'confirm-submit.js')].map((f) => join(STATIC_DIR, f));
const jsCode = (f: string) => readFileSync(f, 'utf8').replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '');

/** 괄호 안 콤마를 쪼개지 않는 목록 분리(`:is(a, b)`·`rgba(…)`) — darkTokens.test.ts `splitSelectors`와 같은 꼴. */
function splitTop(list: string): string[] {
    const out: string[] = [];
    let depth = 0;
    let cur = '';
    for (const ch of list) {
        if (ch === '(') depth++;
        else if (ch === ')') depth--;
        if (ch === ',' && depth === 0) { out.push(cur); cur = ''; } else cur += ch;
    }
    out.push(cur);
    return out.map((s) => s.trim().replace(/\s+/g, ' ')).filter(Boolean);
}

/** 셀렉터 목록을 항목마다 한 규칙으로(괄호 안 콤마는 그대로). */
function rulesP(css: string): Rule[] {
    const out: Rule[] = [];
    const re = /([^{}]+)\{([^{}]*)\}/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(css))) {
        for (const selector of splitTop(m[1].replace(/^[\s\S]*;/, ''))) out.push({ selector, body: m[2] });
    }
    return out;
}

/** 선언 목록 — [속성, 값]. 커스텀 속성은 뺀다(토큰 블록은 darkTokens.test.ts 몫이다). */
function declsOf(body: string): Array<[string, string]> {
    return body.split(';')
        .map((d) => /^\s*([\w-]+)\s*:\s*(.+)$/s.exec(d))
        .filter((m): m is RegExpExecArray => !!m && !m[1].startsWith('--'))
        .map((m) => [m[1], m[2].trim().replace(/\s+/g, ' ')]);
}

/** `@media` 블록을 통째로 걷은 CSS — 최상위 규칙만 남는다(다크 절도 `@media not print` 안이라 함께 빠진다). */
function topLevel(css: string): string {
    let out = '';
    let i = 0;
    while (i < css.length) {
        const at = css.indexOf('@media', i);
        if (at < 0) { out += css.slice(i); break; }
        out += css.slice(i, at);
        let depth = 0;
        let j = css.indexOf('{', at);
        for (; j < css.length; j++) {
            if (css[j] === '{') depth++;
            else if (css[j] === '}' && --depth === 0) break;
        }
        i = j + 1;
    }
    return out;
}

/** 같은 셀렉터의 최상위 규칙을 순서대로 합친 최종 선언(뒤가 이긴다). */
function merged(css: string, selector: string): Map<string, string> {
    const out = new Map<string, string>();
    for (const r of rulesP(topLevel(css)).filter((x) => x.selector === selector)) for (const [p, v] of declsOf(r.body)) out.set(p, v);
    return out;
}

const APP = () => read(APP_CSS);
const LAND = () => read(LANDING_CSS);
const ALL_RULES = () => [...rulesP(APP()), ...rulesP(LAND())];

/** `box-shadow` 층 하나가 흐림 0인가 — `none` 또는 길이 2~4개 중 셋째(흐림)가 0. */
function blurFree(layer: string): boolean {
    const v = layer.trim();
    if (v === 'none') return true;
    const lens = v.replace(/rgba?\([^)]*\)|var\([^)]*\)/g, ' ').split(/\s+/).filter((t) => /^-?[\d.]+(px|em|rem)?$/.test(t));
    return lens.length >= 2 && (lens.length < 3 || parseFloat(lens[2]) === 0);
}

/** 잉크(강조) 채움 값 — 토큰이 잉크가 되면 이 값으로 칠한 면은 전부 검정이 된다. */
const INK_FILL = /^var\(--(accent|accent-fill|accent-hover|accent-hover-2|ok|ok-fill|ok-ink|text)\)$/;
/** 잉크 채움이 허락된 자리 — 검정 알약 3 · 데이터 표식 9 · 대표 배지 · 체크박스 · 다 읽음 배지 2(설계 §5-6 「채움 자리」). */
const INK_FILL_ALLOWED = new Set([
    '.btn-primary', '.dash-btn-fill', '.auth-page .auth-btn-primary',
    '.dash-progress-fill', '.dash-progress-fill-ok', '.dash-pulse-dot', '.margin-fresh-dot', '.pbti-dot.is-active',
    '.study-mark.is-kept', '.study-legend-mark.is-kept', '.study-dot', '.lp-grass-cell.g5',
    '.personality-badge-rep',
    '.settings-page .set-check input:checked ~ .set-check-box',
    '.shop-badge-finished', 'body.bookdetail-page .bd-status-finished',
]);
function inkFillLeaks(rs: Rule[]): string[] {
    return rs.filter((r) => !r.selector.startsWith(':root') && !INK_FILL_ALLOWED.has(r.selector))
        .filter((r) => declsOf(r.body).some(([p, v]) => (p === 'background' || p === 'background-color') && INK_FILL.test(v)))
        .map((r) => r.selector);
}

/** 카드·패널 판정 대상 — 마지막 복합 선택자의 클래스 이름이 card·panel·entry로 끝난다(의사 요소 제외). */
const CARD_TAIL = /\.[\w-]*(card|panel|entry)(?![\w-])/;
function isCardTarget(sel: string): boolean {
    if (sel.includes('::')) return false;
    const last = sel.split(/\s*[\s>+~]\s*/).filter(Boolean).pop() ?? '';
    return CARD_TAIL.test(last);
}
/** 네 변 테두리 또는 그림자를 그리는가(한 변 선은 테가 아니다). */
function drawsFrame(body: string): boolean {
    return declsOf(body).some(([p, v]) =>
        (p === 'border' && !/^(0|none)(\s|$)/.test(v) && !/\btransparent\b/.test(v))
        || (p === 'border-width' && !/^0(px)?$/.test(v))
        || (p === 'border-style' && v !== 'none')
        || (p === 'box-shadow' && v !== 'none'));
}
const CARD_FRAME_ALLOWED = new Set([
    '.margin-card.story-bg-paper', '.personality-entry.is-selected', '.shop-search-entry',
    '.book-sheet-panel', '.margin-panel', '.story-composer-panel', '.shop-report-modal-panel',
]);
function cardFrameLeaks(rs: Rule[]): string[] {
    return rs.filter((r) => isCardTarget(r.selector) && !CARD_FRAME_ALLOWED.has(r.selector) && drawsFrame(r.body)).map((r) => r.selector);
}

/** 입력칸 판정 대상 — 폼 태그 셀렉터(옛 pencilFrame.test.ts의 FORM_TAG) 또는 `-input`·`-select`로 끝나는 클래스 · 편집기 상자. */
const FORM_TAG = /(^|[\s,>+~(])(input|select|textarea)(?=[\s,:.[)]|$)/i;
const INPUT_CLASS = /\.[\w-]+-(input|select)(?![\w-])|\.study-editor(?![\w-])/;
const INPUT_BORDER_OK = new Set(['var(--muted)', 'var(--text)', 'var(--danger)']);
function inputBorderColors(rs: Rule[]): Array<[string, string]> {
    const out: Array<[string, string]> = [];
    for (const r of rs) {
        if (r.selector.includes('::') || !(FORM_TAG.test(r.selector) || INPUT_CLASS.test(r.selector))) continue;
        for (const [p, v] of declsOf(r.body)) {
            if (p === 'border' && /\bsolid\b/.test(v)) out.push([r.selector, v.replace(/^.*\bsolid\s+/, '')]);
            if (p === 'border-color') out.push([r.selector, v]);
        }
    }
    return out;
}

/** 떠 있는 것 — 종이 + 잉크 1px(그림자가 사라진 뒤 경계는 선 하나다). */
const FLOATING = [
    '.buy-menu-items', '.shop-buy-menu-items', '.affiliate-pop', '.pbti-help-pop', '.shop-menu-list', '.dash-header-menu',
    '.study-editor-slash', '.study-notes-recent-menu', '.grass-tip', '.shop-report-modal-panel', '.book-sheet-panel',
    '.margin-panel', '.story-composer-panel', '.dash-mode-toggle', '#side-rails .rail.is-open',
];

describe('에디토리얼 옷 — 색 · 선 · 면 · 버튼 (PR-2)', () => {
    it('REQ-01 · 종이·잉크·글자·선·잔디·천 장정·빨강이 미니앱과 같다', () => {
        const mini = strip(readFileSync(MINI_CSS, 'utf8'));
        const block = (sel: string) => rulesP(mini).filter((r) => r.selector === sel).map((r) => r.body).join(';');
        const mv = (sel: string, name: string) => {
            const v = decl(block(sel), name);
            if (!v) throw new Error(`미니앱 ${sel}에 ${name}이 없다 — 계측기 고장`);
            return v.toUpperCase();
        };
        const web = rulesP(APP());
        const webBody = (sel: string) => web.filter((r) => r.selector === sel).map((r) => r.body).join(';');
        const wv = (sel: string, name: string) => (decl(webBody(sel), name) ?? '').toUpperCase();
        const L = ':root';
        const D = ':root[data-theme="dark"]';
        const pairs: Array<[string, string]> = [
            [wv(L, '--bg'), mv('html body', 'background')],
            [wv(L, '--text'), mv('html:root', '--adaptiveGrey800')],
            [wv(L, '--ink-2'), mv('html:root', '--adaptiveGrey700')],
            [wv(L, '--muted'), mv('html:root', '--adaptiveGrey600')],
            [wv(L, '--border'), mv('html:root', '--adaptiveGrey200')],
            [wv(L, '--sage-soft'), mv('html:root', '--softDent')],
            [wv(L, '--danger'), mv('html:root', '--adaptiveRed500')],
            [wv(L, '--danger-soft'), mv('html:root', '--dangerSoft')],
            ...[0, 1, 2, 3, 4].map((i): [string, string] => [wv(L, `--grass-${i}`), mv('html:root', `--grass${i}`)]),
            [wv(D, '--bg'), mv('body.reading-lamp', 'background')],
            [wv(D, '--text'), mv('body.reading-lamp main', '--adaptiveGrey800')],
            [wv(D, '--ink-2'), mv('body.reading-lamp main', '--adaptiveGrey700')],
            [wv(D, '--muted'), mv('body.reading-lamp main', '--adaptiveGrey600')],
            [wv(D, '--border'), mv('body.reading-lamp main', '--adaptiveGrey200')],
            [wv(D, '--sage-soft'), mv('body.reading-lamp main', '--softDent')],
            [wv(D, '--danger'), mv('body.reading-lamp main', '--adaptiveRed500')],
            [wv(D, '--danger-soft'), mv('body.reading-lamp main', '--dangerSoft')],
            [wv('.is-study', '--accent'), mv('body.study-mode', '--adaptiveBlue500')],
            ...[1, 2, 3, 4].map((i): [string, string] => [wv('.is-study', `--grass-${i}`), mv('body.study-mode', `--grass${i}`)]),
        ];
        expect(pairs.length).toBe(26);
        expect(pairs.filter(([w, m]) => w !== m)).toEqual([]);

        // 천 장정 12색(순서까지) · 이니셜 · 새싹 세 획 — 소스 리터럴을 읽어 대조한다(계측기 자기검증: 실제로 12개·3개를 뽑는다).
        const ui = readFileSync(MINI_UI, 'utf8');
        const pure = readFileSync(join(HERE, 'books', 'pure.ts'), 'utf8');
        const timer = readFileSync(join(HERE, 'dashboard', 'TimerCard.vue'), 'utf8');
        const arr = (src: string, name: string) => {
            const m = new RegExp(`${name}[^=]*=\\s*\\[([\\s\\S]*?)\\]`).exec(src);
            return m ? [...m[1].matchAll(/'([^']+)'/g)].map((x) => x[1]) : [];
        };
        const miniPalette = arr(ui, 'COVER_PALETTE');
        expect(miniPalette).toHaveLength(12);
        expect(arr(pure, 'COVER_PALETTE')).toEqual(miniPalette);
        const fg = (src: string) => /COVER_FG[^=]*=\s*'([^']+)'/.exec(src)?.[1];
        expect(fg(ui)).toBe('#F5F0E6');
        expect(fg(pure)).toBe(fg(ui));
        const sprout = arr(ui, 'SPROUT_PATHS');
        expect(sprout).toHaveLength(3);
        expect(arr(timer, 'SPROUT_PATHS')).toEqual(sprout);
    });

    it('REQ-01 · 옛 세이지·파스텔 값이 테마 파일에 0', () => {
        const OLD = ['#6E8A6A', '#4F6B4C', '#E7EEE2', '#F7F2E8', '#FCFAF5', '#8FAE86', '#B8C2A6', '#EFF3F6', '#E6ECF0', '#47657C', '#5F7E96',
            'rgba(110, 138, 106', 'rgba(255, 220, 160'];
        const files = [
            APP(), LAND(),
            strip(readFileSync(join(HERE, 'books', 'pure.ts'), 'utf8')),
            readFileSync(join(STATIC_DIR, 'manifest.json'), 'utf8'),
            readFileSync(join(ROOT_DIR, 'src', 'main', 'resources', 'templates', 'fragments', 'pwa-head.html'), 'utf8'),
        ].join('\n').toUpperCase().replace(/\s+/g, ' ');
        // 양성 대조 — 같은 방법으로 새 종이 값은 잡힌다.
        expect(files).toContain('#F5F0E6');
        expect(OLD.filter((o) => files.includes(o.toUpperCase()))).toEqual([]);
    });

    it('REQ-06 · 연필테·종이 결이 없다', () => {
        const css = BOTH();
        expect(ALL_RULES().length, '규칙을 거의 못 읽었다 — 파서 고장').toBeGreaterThanOrEqual(1000);
        expect(values(css, 'border-image')).toEqual([]);
        expect(css.match(/--(pencil-frame|pencil-frame-soft|grain|fiber|shadow)\s*:/g) ?? []).toEqual([]);
        expect(css.match(/var\(--(pencil-frame|pencil-frame-soft|grain|fiber|shadow)\)/g) ?? []).toEqual([]);
        expect(ALL_RULES().filter((r) => r.selector === 'body::before').map((r) => r.selector)).toEqual([]);
    });

    it('REQ-06 · 그림자는 흐림 0 링뿐이다(CSS 밖 정적 JS 인라인 포함)', () => {
        // 음성·양성 대조 — 판정 함수가 흐림을 빨강으로, 링을 초록으로 친다.
        expect(blurFree('0 2px 4px rgba(0, 0, 0, .1)')).toBe(false);
        expect(blurFree('0 0 0 3px var(--focus-ring)')).toBe(true);
        expect(blurFree('inset 0 -2px 0 var(--text)')).toBe(true);

        const layers = values(BOTH(), 'box-shadow').flatMap(splitTop);
        expect(layers.filter((l) => l !== 'none').length, '링 층을 거의 못 읽었다').toBeGreaterThanOrEqual(8);
        expect(layers.filter((l) => !blurFree(l))).toEqual([]);

        for (const f of INLINE_JS) {
            const code = jsCode(f);
            const inline = [...code.matchAll(/box-shadow\s*:\s*([^'";]+)/g)].flatMap((m) => splitTop(m[1]));
            expect(inline.filter((l) => !blurFree(l)), f).toEqual([]);
            expect(code, f).not.toMatch(/backdrop-filter/);
        }
    });

    it('REQ-06 · 그라데이션·반투명 섞기가 없다', () => {
        const all = [BOTH(), ...INLINE_JS.map(jsCode)].join('\n');
        expect(all.match(/gradient\(/g) ?? []).toEqual([]);
        expect(all.match(/color-mix\(/g) ?? []).toEqual([]);
    });

    it('REQ-07 · 모서리는 0·2px·999px·50%뿐', () => {
        const OK = new Set(['0', '2px', '999px', '50%', 'inherit', 'var(--radius)', 'var(--radius-sm)']);
        const radii = values(BOTH(), 'border-radius');
        expect(radii.length, 'border-radius 선언을 거의 못 읽었다').toBeGreaterThanOrEqual(150);
        expect(radii.filter((v) => v.split(/\s+/).some((c) => !OK.has(c)))).toEqual([]);

        const root = rulesP(APP()).filter((r) => r.selector === ':root').map((r) => r.body).join(';');
        expect(decl(root, '--radius')).toBe('2px');
        expect(decl(root, '--radius-sm')).toBe('2px');
        // 특이도 함정 — (0,2,0)이라 `.dash-btn-*`(0,1,0)의 999를 이긴다. 2px로 두면 책방 팔로우만 사각형이 된다.
        expect(merged(APP(), '.shop-actions .dash-btn-fill').get('border-radius')).toBe('999px');
        expect(merged(APP(), '.shop-actions .dash-btn-outline').get('border-radius')).toBe('999px');
        for (const s of ['.book-status-badge', '.book-sheet-badge', '.shop-badge']) {
            expect(merged(APP(), s).get('border-radius'), s).toBe('2px');
        }
    });

    it('REQ-08 · 버튼 2단 — 검정 알약 3규칙 · 외곽선 알약 · 위험 외곽선', () => {
        for (const s of ['.btn-primary', '.dash-btn-fill', '.auth-page .auth-btn-primary']) {
            const m = merged(APP(), s);
            expect(m.get('background') ?? m.get('background-color'), s).toBe('var(--accent-fill)');
            expect(m.get('color'), s).toBe('var(--on-accent)');
            expect(m.get('border-radius'), s).toBe('999px');
        }
        const outline: Array<[string, string]> = [
            [APP(), 'button'], [APP(), '.btn'], [APP(), '.settings-page .set-btn-save'],
            [APP(), '.book-sheet-search-btn'], [APP(), '.book-sheet-add-btn'],
            [LAND(), '.lp-nav-cta'], [LAND(), '.lp-bottom-cta-btn'],
        ];
        for (const [css, s] of outline) {
            const m = merged(css, s);
            expect(m.get('border'), s).toBe('1px solid var(--text)');
            expect(m.get('border-radius'), s).toBe('999px');
            expect(m.get('background') ?? m.get('background-color'), s).toBe('transparent');
        }
        const danger = merged(APP(), '.btn-danger');
        expect(danger.get('border')).toBe('1px solid var(--danger)');
        expect(danger.get('color')).toBe('var(--danger)');
        expect(danger.get('background') ?? danger.get('background-color')).toBe('transparent');
    });

    it('REQ-08 · 잉크 채움은 허용목록뿐이다', () => {
        // 음성 대조 — 목록 밖 셀렉터가 강조 토큰으로 칠하면 잡힌다.
        expect(inkFillLeaks(rulesP('.x { background: var(--accent); }'))).toEqual(['.x']);
        const rs = ALL_RULES();
        // 양성 대조 — 허용목록 셀렉터가 전부 실재한다(목록이 낡으면 이 가드가 무엇을 봐주는지 모른다).
        const present = new Set(rs.map((r) => r.selector));
        expect([...INK_FILL_ALLOWED].filter((s) => !present.has(s))).toEqual([]);
        expect(inkFillLeaks(rs)).toEqual([]);
    });

    it('REQ-09 · 입력칸은 흐린 글자색 1px · 자리글자는 흐린 글자 그대로', () => {
        const borders = inputBorderColors(rulesP(APP()));
        expect(borders.length, '입력칸 테두리 선언을 거의 못 읽었다').toBeGreaterThanOrEqual(12);
        expect(borders.filter(([, c]) => !INPUT_BORDER_OK.has(c))).toEqual([]);
        // 자리글자 — 흐린 글자에 불투명도를 또 걸면 종이 위 3.32로 AA 미달(시안 사양서 §14 ⓓ). `opacity: 1`은 허용 —
        // 파이어폭스 UA가 ::placeholder를 .54로 흐리므로 그걸 되돌리는 선언이다.
        for (const s of ['.study-day ::placeholder', '.study-editor-placeholder']) {
            const m = merged(APP(), s);
            expect(m.get('color'), s).toBe('var(--muted)');
            expect(m.get('opacity') ?? '1', s).toBe('1');
        }
    });

    it('REQ-10 · 카드·패널엔 면 테두리·그림자가 없다', () => {
        expect(cardFrameLeaks(rulesP('.x-card { border: 1px solid var(--border); }'))).toEqual(['.x-card']);
        expect(cardFrameLeaks(rulesP('.x-card h2 { border-bottom: 1px solid var(--text); }'))).toEqual([]);
        const rs = rulesP(APP());
        expect(rs.filter((r) => isCardTarget(r.selector)).length, '카드 규칙을 거의 못 읽었다').toBeGreaterThanOrEqual(10);
        expect(cardFrameLeaks(rs)).toEqual([]);
        // 섹션 머리 — 명조 + 잉크 1px 선(면 대신 덩어리를 가르는 것).
        const h2 = merged(APP(), '.card h2');
        expect(h2.get('font-family')).toBe('var(--font-serif)');
        expect(h2.get('border-bottom')).toBe('1px solid var(--text)');
    });

    it('REQ-10 · 떠 있는 것은 종이 + 잉크 1px', () => {
        for (const s of FLOATING) {
            const m = merged(APP(), s);
            expect(m.get('border'), s).toBe('1px solid var(--text)');
            expect(m.get('background') ?? m.get('background-color'), s).toBe('var(--card-bg)');
            expect(m.get('box-shadow') ?? 'none', s).toBe('none');
        }
        // 고른 칸 밑줄이 999px 둥근 끝 밖으로 나가지 않게 그릇이 자른다(시안 사양서 §8.6 R2 개정).
        expect(merged(APP(), '.dash-mode-toggle').get('overflow')).toBe('hidden');
    });

    it('REQ-15 · 직접 채운 잔디 칸은 가는 테', () => {
        const m = merged(APP(), '.grass-cell.manual');
        expect(m.has('box-shadow')).toBe(false);
        expect(m.get('outline')).toBe('1px solid var(--neutral-2)');
    });

    it('REQ-19 · 제휴 고지가 종이 위 4.5 이상(불투명도 0 · 흐린 글자)', () => {
        const m = merged(APP(), '.affiliate-note');
        expect(m.get('color')).toBe('var(--muted)');
        expect(m.has('opacity')).toBe(false);
    });

    it('REQ-20 · PWA 머리 색이 종이와 같고 설치본 캐시를 갈아끼운다', () => {
        const head = readFileSync(join(ROOT_DIR, 'src', 'main', 'resources', 'templates', 'fragments', 'pwa-head.html'), 'utf8');
        const manifest = JSON.parse(readFileSync(join(STATIC_DIR, 'manifest.json'), 'utf8'));
        const bg = decl(rulesP(APP()).filter((r) => r.selector === ':root').map((r) => r.body).join(';'), '--bg');
        expect(/name="theme-color" content="([^"]+)"/.exec(head)?.[1]).toBe(bg);
        expect(manifest.theme_color).toBe(bg);
        expect(manifest.background_color).toBe(bg);
        expect(/const\s+CACHE\s*=\s*'([^']+)'/.exec(readFileSync(join(STATIC_DIR, 'sw.js'), 'utf8'))?.[1]).toBe('shell-v9');
    });
});
