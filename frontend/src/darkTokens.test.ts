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
const CSS_DIR = join(HERE, '..', '..', 'src', 'main', 'resources', 'static', 'css');
const APP_CSS = join(CSS_DIR, 'app.css');
const LANDING_CSS = join(CSS_DIR, 'landing.css');
/**
 * raw 색 래칫이 훑는 CSS — 토큰 짝 맞춤은 `:root`가 사는 `app.css`만이지만, 래칫은
 * <b>토큰화한 파일 전부</b>를 봐야 한다. `landing.css`는 이 PR이 같이 토큰화해 놓고도
 * 래칫 밖에 있었다(독립 리뷰가 `outline-color: #ABCDEF`로 실증 — 깨끗한데 지키는 게 없었다).
 */
const RATCHET_CSS = [APP_CSS, LANDING_CSS];

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
function rules(file = APP_CSS): Array<{ selector: string; body: string; at: number }> {
    const css = readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    const out: Array<{ selector: string; body: string; at: number }> = [];
    const re = /([^{}]+)\{([^{}]*)\}/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(css))) {
        const selector = m[1].replace(/^[\s\S]*;/, '').trim().replace(/\s+/g, ' ');
        out.push({ selector, body: m[2], at: m.index });
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

/**
 * 계측기 생존선 — <b>현재 실측 개수를 래칫으로 박는다</b>.
 *
 * <p>짝 맞춤은 양방향 부분집합이라 두 집합이 <b>같이</b> 쪼그라들면 통과한다(빈 집합끼리도 통과다).
 * 그래서 「0이 아니다」로는 부족하고 지금 세는 수를 바닥으로 깐다. 토큰을 정말로 줄일 일이 생기면
 * 이 숫자도 <b>같이 내린다</b> — 조용히 줄어드는 것만 막자는 것이지 못 줄이게 하려는 게 아니다.
 *
 * <p>단, <b>라이트 값 보존이 목적인 토큰은 줄이지 않는다</b>(사용자 결정 2026-09-10).
 * `--warn-bg-2/3/4`처럼 이름이 닮은 변종은 중복이 아니라 <b>`origin/main`의 라이트 픽셀을
 * 그대로 두려고</b> 갈라 둔 것이다 — 「닮았으니 합치자」로 이 숫자를 내리는 순간 라이트가
 * 조용히 다른 화면이 된다.
 */
const LIGHT_FLOOR = 119;
const STUDY_FLOOR = 15;

describe('다크 토큰 짝 맞춤', () => {
    const light = colorTokens(LIGHT_SELECTOR);
    const dark = colorTokens(DARK_SELECTOR);

    it('파서가 라이트 토큰을 실제로 잡는다 — 0개면 늘 통과하는 빈 가드다 (계측기 생존)', () => {
        expect(light.length).toBeGreaterThanOrEqual(LIGHT_FLOOR);
    });

    it('파서가 다크 토큰을 실제로 잡는다 (계측기 생존)', () => {
        expect(dark.length).toBeGreaterThanOrEqual(LIGHT_FLOOR);
    });

    it('다크가 안 덮은 라이트 색 토큰이 없다', () => {
        expect(light.filter((t) => !dark.includes(t))).toEqual([]);
    });

    it('라이트에 없는 토큰을 다크가 만들지 않는다 (오타·유령 토큰 차단)', () => {
        expect(dark.filter((t) => !light.includes(t))).toEqual([]);
    });
});

/**
 * 공부 모드(`.is-study`)도 같은 짝 맞춤이 필요하다.
 *
 * <p>`:root`에만 가드를 걸면 <b>정확히 같은 함정이 한 칸 옆에 남는다</b> — 라이트 `.is-study`에 파랑
 * 토큰이 하나 늘고 다크판을 잊으면, 공부 화면의 그 자리만 다크에서 세이지로 튄다(라이트에선 파랑인데
 * 다크에선 초록이 되는 셈). 독립 리뷰가 「`:root`에 건 것과 같은 함정」으로 짚어 준 자리다.
 */
describe('공부 모드 토큰 짝 맞춤 (.is-study)', () => {
    const light = colorTokens('.is-study');
    const dark = colorTokens(`${DARK_SELECTOR} .is-study`);

    it('파서가 공부 토큰을 실제로 잡는다 (계측기 생존)', () => {
        expect(light.length).toBeGreaterThanOrEqual(STUDY_FLOOR);
        expect(dark.length).toBeGreaterThanOrEqual(STUDY_FLOOR);
    });

    it('다크가 안 덮은 공부 색 토큰이 없다', () => {
        expect(light.filter((t) => !dark.includes(t))).toEqual([]);
    });

    it('라이트에 없는 공부 토큰을 다크가 만들지 않는다', () => {
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

/**
 * raw 색 <b>래칫</b> — 토큰을 안 거치는 색 리터럴이 다시 새어 들어오는 것을 막는다.
 *
 * <p>다크는 `:root` 토큰을 갈아끼워 동작한다. 그래서 규칙에 hex를 직접 적는 순간 그 자리만
 * 다크에서 <b>라이트 값 그대로</b> 남는다 — 크림색 카드 하나가 어두운 화면에 박히는 식이고,
 * 다크로 그 페이지를 열어봐야만 보인다. 짝 맞춤 가드는 「토큰끼리」만 보므로 이 새는 자리를
 * 못 잡는다. 여기서 <b>개수 자체</b>를 0으로 눌러 둔다.
 *
 * <p>허용목록은 「다크에서도 그대로여야 하는 색」만이다 — 개수가 아니라 <b>속성·셀렉터로</b>
 * 명시한다(숫자만 올리면 무엇이 늘었는지 알 수 없다).
 */
const ALLOWED = [
    // 그림자는 어느 테마에서도 검정·흑갈색이다. 다크 위의 검은 그림자는 무해하다.
    { why: 'box-shadow', match: (sel: string, prop: string) => prop === 'box-shadow' },
    // 공부 잉크(파랑)의 라이트 원본. 다크판은 `:root[data-theme="dark"] .is-study` 블록이
    // 따로 그리고, 그 짝은 위 「공부 모드 토큰 짝 맞춤」이 지킨다.
    { why: '.is-study 스코프', match: (sel: string) => /(^|[\s,])\.is-study\b/.test(sel) || /\.is-study(\.|\s|$)/.test(sel) },
    // 구글 브랜드 파랑 — 브랜드 자산이라 테마를 따르지 않는다(설계가 템플릿 인라인 로고를 뺀 것과 같은 이유).
    { why: '구글 브랜드', match: (sel: string) => sel.includes('.oauth-icon') },
    // 여백 글 배경 프리셋(종이·밤·숲…)은 사용자가 고르는 <b>콘텐츠 색</b>이다. 표지 팔레트를
    // 그대로 두는 것과 같은 범주 — 밤 프리셋이 다크에서 밝아지면 그건 다른 프리셋이 된다.
    { why: '.story-bg-* 콘텐츠 프리셋', match: (sel: string) => sel.includes('.story-bg-') },
];

/**
 * 다크 블록(`@media not print`)의 <b>[시작, 끝)</b> — 그 <b>안쪽만</b> 래칫에서 뺀다.
 *
 * <p>시작 위치만 알고 「그 뒤 전부」를 건너뛰면, 다크 블록이 <b>파일의 마지막</b>이라
 * 「CSS 끝에 규칙을 새로 붙인다」는 가장 자연스러운 동작이 곧바로 사각에 떨어진다 —
 * 독립 리뷰가 파일 끝 한 줄로 실증했다. 그래서 닫는 `}`를 세어 범위로 자른다.
 */
function darkSection(file = APP_CSS): { start: number; end: number } {
    const css = readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    const start = css.indexOf('@media not print');
    const open = start < 0 ? -1 : css.indexOf('{', start);
    if (open < 0) return { start: -1, end: -1 };
    let depth = 0;
    for (let i = open; i < css.length; i++) {
        if (css[i] === '{') depth++;
        else if (css[i] === '}' && --depth === 0) return { start, end: i };
    }
    return { start, end: css.length };
}

/**
 * CSS 이름 색 전부 — hex와 `rgb()`만 막으면 <b>`color: gray` 한 줄이 그대로 샌다</b>.
 * 독립 리뷰가 `gray`·`hsl()`을 심어 통과하는 것을 실증한 자리다.
 * `transparent`·`currentColor`는 테마를 안 타므로 뺀다.
 */
const NAMED_COLORS =
    'aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown ' +
    'burlywood cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan ' +
    'darkgoldenrod darkgray darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid ' +
    'darkred darksalmon darkseagreen darkslateblue darkslategray darkslategrey darkturquoise darkviolet ' +
    'deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ' +
    'ghostwhite gold goldenrod gray green greenyellow grey honeydew hotpink indianred indigo ivory khaki ' +
    'lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan lightgoldenrodyellow ' +
    'lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen lightskyblue lightslategray ' +
    'lightslategrey lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine ' +
    'mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen mediumturquoise ' +
    'mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive olivedrab ' +
    'orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru ' +
    'pink plum powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown ' +
    'seagreen seashell sienna silver skyblue slateblue slategray slategrey snow springgreen steelblue tan ' +
    'teal thistle tomato turquoise violet wheat white whitesmoke yellow yellowgreen';

/**
 * 색 리터럴 — hex · 색 함수 · 이름 색.
 *
 * <p>색 함수는 `rgb()` 하나가 아니다: `hsl()`·`hwb()`·`lab()`·`lch()`·`oklab()`·`oklch()`·`color()`가
 * 전부 토큰을 안 거친 리터럴이다. `color-mix(`는 <b>일부러 뺀다</b> — 안쪽이 `var(--…)`인 토큰 채움이고,
 * 리터럴을 품었다면 그 리터럴이 따로 잡힌다.
 */
const RAW_COLOR = new RegExp(
    '#[0-9a-fA-F]{3,8}\\b' +
        '|\\b(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\\(' +
        `|\\b(?:${NAMED_COLORS.replace(/ /g, '|')})\\b`,
);

function rawColorDeclarations(): Array<{ file: string; selector: string; decl: string; allowed: string | null }> {
    const out: Array<{ file: string; selector: string; decl: string; allowed: string | null }> = [];
    for (const file of RATCHET_CSS) {
        const name = file.split(/[\\/]/).pop()!;
        const { start, end } = darkSection(file);
        for (const r of rules(file)) {
            if (r.selector === LIGHT_SELECTOR || r.selector.startsWith(`${LIGHT_SELECTOR}[`)) continue;
            if (start >= 0 && r.at > start && r.at < end) continue;
            for (const d of r.body.split(';')) {
                const m = /^\s*([\w-]+)\s*:\s*(.+)$/s.exec(d);
                if (!m || !RAW_COLOR.test(m[2])) continue;
                const hit = ALLOWED.find((a) => a.match(r.selector, m[1]));
                out.push({
                    file: name,
                    selector: r.selector,
                    decl: `${m[1]}: ${m[2].trim()}`,
                    allowed: hit ? hit.why : null,
                });
            }
        }
    }
    return out;
}

describe('raw 색 래칫 — 토큰을 안 거친 색 리터럴', () => {
    const all = rawColorDeclarations();

    it('파서가 선언을 실제로 훑는다 — 0이면 늘 통과하는 빈 래칫이다 (계측기 생존)', () => {
        const cut = darkSection().start;   // 루프 밖에서 한 번 — 안에서 부르면 규칙마다 파일을 다시 읽는다
        const scanned = rules().filter((r) => r.at < cut).reduce((n, r) => n + r.body.split(';').length, 0);
        expect(scanned).toBeGreaterThanOrEqual(3000);
        // landing.css도 실제로 읽히는지 — 배열에 넣고 안 읽으면 조용히 0줄짜리 검사가 된다.
        expect(rules(LANDING_CSS).length).toBeGreaterThanOrEqual(50);
    });

    it('허용목록이 실제로 색을 잡고 있다 — 빈 허용목록이면 래칫이 무엇을 봐준 건지 알 수 없다 (양성 대조군)', () => {
        const byWhy = (why: string) => all.filter((d) => d.allowed === why).length;
        expect(byWhy('box-shadow')).toBeGreaterThanOrEqual(25);
        expect(byWhy('.is-study 스코프')).toBeGreaterThanOrEqual(10);
        expect(byWhy('.story-bg-* 콘텐츠 프리셋')).toBeGreaterThanOrEqual(10);
    });

    it('허용목록 밖에는 색 리터럴이 남아 있지 않다', () => {
        const leaked = all.filter((d) => !d.allowed).map((d) => `${d.file}: ${d.selector} { ${d.decl} }`);
        expect(leaked).toEqual([]);
    });
});

/**
 * 정적 JS가 <b>인라인 스타일로</b> 칠하는 색도 같은 래칫이 필요하다.
 *
 * <p>인라인 스타일은 CSS의 어떤 테마 규칙보다 세서 다크가 못 덮는다 — `app.css`만 보는 위 래칫의
 * 사각이다. 실제로 PWA 설치 칩이 라이트 세이지 채움 그대로 어두운 화면에 떠 있는 것을 브라우저
 * 실측에서 잡았다(코드만 읽어서는 안 보이던 자리다).
 */
describe('정적 JS 인라인 스타일의 raw 색', () => {
    const JS_DIR = join(HERE, '..', '..', 'src', 'main', 'resources', 'static');
    const FILES = ['pwa-install.js', join('js', 'theme.js'), join('js', 'confirm-submit.js')];

    for (const f of FILES) {
        it(`${f}가 색 리터럴로 스타일을 칠하지 않는다`, () => {
            const src = readFileSync(join(JS_DIR, f), 'utf8');
            // 주석은 걷는다 — 설명문의 hex(옛 값 기록 등)는 화면을 칠하지 않는다.
            const code = src.replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '');
            // box-shadow는 CSS 래칫과 같은 이유로 뺀다 — 그림자는 어느 테마에서도 검정이다.
            // 속성명은 `[-\w]*color`로 받는다. 옛 `(?<!-)color`는 `background-color:`를 통째로 놓쳤다 —
            // `background` 분기는 뒤의 `:` 대신 `-`를 만나 죽고 `color` 분기는 룩비하인드에 걸린다.
            // 이 래칫이 있는 이유가 정확히 인라인 채움 색인데 그 속성명이 사각이었다(독립 리뷰 실측).
            const hits = code.match(/(?:background|[-\w]*color|border)\s*:\s*[^'"]*(?:#[0-9a-fA-F]{3,8}\b|rgba?\()/g) ?? [];
            expect(hits).toEqual([]);
        });
    }
});

/**
 * accent 채움 위 글자색(`--on-accent`)은 <b>대비를 실제로 계산해</b> 못 박는다.
 *
 * <p>이 자리가 다크에서 유일하게 AA를 못 넘겼다: 다크 accent(#8FAE86)는 밝은 세이지라
 * 그 위의 흰 글자가 2.45다. 「토큰을 만들었다」로는 그 실수를 못 잡는다 — 값이 흰색이어도
 * 토큰은 있으니까. 그래서 존재가 아니라 <b>비율</b>을 잰다.
 */
function hexToRgb(hex: string): [number, number, number] {
    const h = hex.replace('#', '');
    const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
    return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16)) as [number, number, number];
}
function luminance(hex: string): number {
    const [r, g, b] = hexToRgb(hex).map((v) => {
        const s = v / 255;
        return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
function contrast(a: string, b: string): number {
    const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (hi + 0.05) / (lo + 0.05);
}
function tokenValue(selector: string, token: string): string {
    const m = new RegExp(`(^|;)\\s*${token}\\s*:\\s*([^;]+)`).exec(blockBody(selector));
    if (!m) throw new Error(`${selector}에 ${token}이 없다`);
    return m[2].trim();
}

describe('--on-accent 대비 (accent 채움 위 글자)', () => {
    it('다크 accent 채움 위 글자가 AA(4.5) 이상이다', () => {
        expect(contrast(tokenValue(DARK_SELECTOR, '--on-accent'), tokenValue(DARK_SELECTOR, '--accent')))
            .toBeGreaterThanOrEqual(4.5);
    });

    it('다크 danger 채움 위 글자도 AA 이상이다 — 삭제 버튼이 같은 토큰을 쓴다', () => {
        expect(contrast(tokenValue(DARK_SELECTOR, '--on-accent'), tokenValue(DARK_SELECTOR, '--danger-ink')))
            .toBeGreaterThanOrEqual(4.5);
    });

    it('accent 채움 규칙이 그 토큰을 실제로 쓴다 — 토큰만 만들고 안 쓰면 화면은 그대로다', () => {
        const users = ['.search-chip.active', '.hist-tab.active', '.settings-page .set-btn-save'];
        const map = new Map(rules().map((r) => [r.selector, r.body]));
        for (const sel of users) {
            expect(map.get(sel), `${sel} 규칙이 사라졌다 — 셀렉터가 바뀌면 이 가드는 조용히 죽는다`).toBeDefined();
            expect(map.get(sel)).toMatch(/color:\s*var\(--on-accent\)/);
        }
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
