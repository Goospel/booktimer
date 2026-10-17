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
interface Rule { selector: string; body: string; at: number; group: string[] }

/**
 * <p>셀렉터 <b>목록</b>(`a, b { }`)은 항목마다 한 규칙으로 낸다 — `group`에 목록 전체를 싣는다.
 * 독서등(2026-09-15)이 토큰 블록을 복제하지 않고 `:root, body.study-lamp .lamp-page {…}`처럼 목록을
 * 늘려 얻기 때문이다: 목록을 통째로 한 셀렉터로 보면 `:root` 정확 일치가 깨져 라이트 토큰이 0개가 된다.
 * 괄호 안 콤마(`:is(a, b)`)는 쪼개지 않는다. (Java `SideRailsTemplateTest.declarationsOf`와 같은 꼴.)
 */
function rules(file = APP_CSS): Rule[] {
    return parse(readFileSync(file, 'utf8'));
}

/** `rules()`의 몸통 — 파일 대신 문자열을 받는다(음성 대조에 일부러 틀린 CSS를 넣으려고). */
function parse(text: string): Rule[] {
    const css = text.replace(/\/\*[\s\S]*?\*\//g, '');
    const out: Rule[] = [];
    const re = /([^{}]+)\{([^{}]*)\}/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(css))) {
        const group = splitSelectors(m[1].replace(/^[\s\S]*;/, ''));
        for (const selector of group) out.push({ selector, body: m[2], at: m.index, group });
    }
    return out;
}

function splitSelectors(list: string): string[] {
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
// 에디토리얼(2026-10 PR-2): 119 → 114 — 질감·그림자 토큰 5개(`--shadow`·`--pencil-frame`·`--pencil-frame-soft`·`--grain`·`--fiber`)를
// 지웠다. 공부 블록 15 → 10 — 면·선 5개(`--sage-soft` 등)는 공부에서도 낮 값을 상속하고 강조 6 + 잔디 4만 갈아 끼운다(설계 §5-4).
const LIGHT_FLOOR = 114;
const STUDY_FLOOR = 10;

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

    // 세로 바 공부 모드 셀렉터는 허용목록에 정확 일치로 들어가 있다 — 공유 블록과 **별개로** 라이트 전용 규칙을
    // 새로 쓰면 목록 소속 단언만으론 안 잡힌다(리뷰 사소-3). 토큰 집합이 .is-study와 같은지 직접 잰다.
    it('세로 바 공부 모드(#side-rails)의 토큰 집합이 .is-study와 같다 — 라이트·다크·독서등', () => {
        expect(colorTokens('#side-rails[data-mode="study"]')).toEqual(light);
        expect(colorTokens(`${DARK_SELECTOR} #side-rails[data-mode="study"]`)).toEqual(dark);
        expect(colorTokens('body.study-lamp #side-rails[data-mode="study"]')).toEqual(dark);
    });
});

/**
 * 독서등(공부 측정 중 홈) — 토큰을 <b>복제하지 않고</b> 기존 네 블록의 셀렉터 목록에 범위를 덧붙여 얻는다
 * (설계 2026-09-15-study-focus-lamp §2-2 B). 밤 = 다크 목록에 `body.study-lamp`, 낮(합쳐진 카드) = 라이트
 * 목록에 `body.study-lamp .lamp-page`.
 *
 * <p>한쪽만 늘리면 밤에 크림색·낮에 차콜이 남는데, 그건 측정을 시작해 봐야만 보인다. 그래서 목록 소속을 잰다.
 */
describe('독서등 — 토큰 복제 0', () => {
    const groupOf = (selector: string) => rules().find((r) => r.selector === selector)?.group ?? [];

    it('밤·낮 범위가 네 토큰 블록의 목록에 들어 있다', () => {
        expect(groupOf(LIGHT_SELECTOR)).toContain('body.study-lamp .lamp-page');
        expect(groupOf(DARK_SELECTOR)).toContain('body.study-lamp');
        expect(groupOf('.is-study')).toContain('body.study-lamp .lamp-page .is-study');
        expect(groupOf(`${DARK_SELECTOR} .is-study`)).toContain('body.study-lamp .is-study');
        // 세로 바 공부 모드 — 라이트·다크·독서등 세 블록 모두 .is-study와 같은 목록에 있어야 짝 맞춤을 물려받는다
        expect(groupOf('.is-study')).toContain('#side-rails[data-mode="study"]');
        expect(groupOf(`${DARK_SELECTOR} .is-study`)).toContain(`${DARK_SELECTOR} #side-rails[data-mode="study"]`);
        expect(groupOf(`${DARK_SELECTOR} .is-study`)).toContain('body.study-lamp #side-rails[data-mode="study"]');
    });

    it('덧씌움 블록이 새 토큰 이름을 만들지 않는다 — 밤은 다크의, 낮은 라이트의 부분집합', () => {
        const light = colorTokens(LIGHT_SELECTOR);
        const dark = colorTokens(DARK_SELECTOR);
        expect(colorTokens('body.study-lamp').filter((t) => !dark.includes(t))).toEqual([]);
        expect(colorTokens('body.study-lamp .lamp-page').filter((t) => !light.includes(t))).toEqual([]);
    });

    // 커스텀 속성은 선언된 요소에서 계산된 값이 상속된다. 다크 테마는 같은 html에서 다시 깔아 별칭이 따라오지만,
    // 독서등은 body에서 깔아서 html에서 라이트로 계산된 별칭(`--nav-hover-bg: var(--sage-soft)` = #E7EEE2)이
    // 그대로 내려온다 — 밤 바 hover가 밝은 연녹 위 밝은 글자(대비 1.09)가 됐다(리뷰 Important-1).
    it('var() 별칭 토큰도 다크 목록에 다시 선언된다 (body 범위에선 별칭이 자동으로 안 따라온다)', () => {
        const names = (sel: string, onlyAlias: boolean) => blockBody(sel).split(';')
            .map((d) => /^\s*(--[\w-]+)\s*:\s*(.+)$/s.exec(d))
            .filter((m): m is RegExpExecArray => !!m && (!onlyAlias || m[2].includes('var(')))
            .map((m) => m[1]);
        const light = names(LIGHT_SELECTOR, true);
        expect(light.length).toBeGreaterThan(0);   // 양성 대조 — 별칭을 실제로 잡는다
        expect(light.filter((t) => !names(DARK_SELECTOR, false).includes(t))).toEqual([]);
    });

    // 합쳐진 카드 투명화는 다크 `:root[data-theme="dark"] .dash-timer-hero.is-study`(0,4,0, 파일 끝)를 이겨야 한다 —
    // `body`가 빠지면 특이도가 같아 파일 순서로 다크가 이겨 다크 테마 막대만 #222A30으로 남는다(실 브라우저 실측).
    it('합쳐진 카드 투명화 규칙은 body로 시작한다(특이도 0,4,1)', () => {
        const hit = rules().filter((r) => r.selector.endsWith('.focus-stack.is-merged > .dash-card.is-study'));
        expect(hit.map((r) => r.selector)).toEqual(['body .focus-stack.is-merged > .dash-card.is-study']);
    });

    // 상속 함정 — 토큰만 되돌리면 글자색은 body에서 계산이 끝난 밤 값(#E8E2D6)이 그대로 내려온다(시안 실측).
    it('합쳐진 카드가 글자색을 낮 토큰으로 다시 계산시킨다 (다크 섹션 안)', () => {
        const { start, end } = darkSection();
        const decl = rules()
            .filter((r) => r.selector === 'body.study-lamp .lamp-page' && r.at > start && r.at < end)
            .some((r) => /(^|;)\s*color\s*:\s*var\(--text\)/.test(r.body));
        expect(decl).toBe(true);
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
    // (옛 `box-shadow` 허용은 에디토리얼 PR-2에서 걷었다 — 그림자는 흐림 0 링만 남고 링은 토큰만 쓴다.
    //  그림자에 색 리터럴이 다시 들어오면 이제 새는 것으로 잡힌다.)
    // 공부 잉크(파랑)의 라이트 원본. 다크판은 `:root[data-theme="dark"] .is-study` 블록이
    // 따로 그리고, 그 짝은 위 「공부 모드 토큰 짝 맞춤」이 지킨다.
    // 세로 바의 공부 모드(#side-rails[data-mode="study"])는 `.is-study`와 같은 토큰 블록의 셀렉터 목록에 끼어 있다
    // (설계 2026-09-17-single-rail-mode-switch) — 다크 짝은 아래 「밤·낮 범위」 케이스가 같은 블록에 있음을 지킨다.
    { why: '.is-study 스코프', match: (sel: string) => /(^|[\s,])\.is-study\b/.test(sel) || /\.is-study(\.|\s|$)/.test(sel) || sel === '#side-rails[data-mode="study"]' },
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
            // 토큰 블록(`:root, body.study-lamp .lamp-page {…}`처럼 목록에 `:root`가 든 규칙)에선 **커스텀 속성 선언만** 건너뛴다.
            // 목록째 건너뛰면 `.leak-probe, :root { color: #123456 }` 한 줄이 통째로 사각에 떨어진다(리뷰 Minor-1 실측).
            const tokenGroup = r.group.some((s) => s === LIGHT_SELECTOR || s.startsWith(`${LIGHT_SELECTOR}[`));
            if (start >= 0 && r.at > start && r.at < end) continue;
            for (const d of r.body.split(';')) {
                const m = /^\s*([\w-]+)\s*:\s*(.+)$/s.exec(d);
                if (!m || !RAW_COLOR.test(m[2])) continue;
                if (tokenGroup && m[1].startsWith('--')) continue;
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
        // 에디토리얼(PR-2): 고른 칩·탭은 채우지 않고 설정 저장은 외곽선이 됐다 — 채움 위 글자를 쓰는 건 검정 알약 3규칙이다.
        const users = ['.btn-primary', '.dash-btn-fill', '.auth-page .auth-btn-primary'];
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

/**
 * 에디토리얼 팔레트(2026-10 PR-2) — 토큰 <b>이름은 그대로, 값만</b> 역할 군으로 모은다(설계 §5-2 낮 · §5-3 밤 · §5-4 공부).
 *
 * <p>군 표를 테스트 상수로 박는다. 단언이 둘이다 — ① 군 안 <b>모든 이름</b>이 그 군의 값이다(변종 하나만 옛 값으로
 * 남는 것을 잡는다) ② 군 표의 이름 합집합이 블록의 색 토큰 전체와 <b>정확히 같다</b>(어느 군에도 안 든 토큰이 새로 생기거나
 * 표에서 빠지면 운다). 값은 대소문자·공백만 정규화해 비교한다.
 */
const norm = (v: string) => v.replace(/\s+/g, '').toUpperCase();
type Groups = Record<string, [string, string[]]>;
const range = (base: string, from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => `${base}-${from + i}`);

const SHARED_NAMES = {
    S: ['--bg', '--card-bg', '--surface', '--paper-1', '--paper-1-2', '--paper-1-3', '--paper-4', '--paper-4-2'],
    F: ['--sage-soft', '--sage-soft-2', '--paper-2', '--paper-3', '--paper-2-2', '--paper-2-3', '--info-soft', ...range('--info-soft', 2, 4),
        '--ok-soft', ...range('--ok-soft', 2, 5), '--warn-bg', ...range('--warn-bg', 2, 4)],
    H: ['--sage-hover', '--sage-hover-2'],
    L: ['--border', ...range('--border', 2, 4), '--line-1', ...range('--line-1', 2, 7), '--line-2', '--line-2-2', '--sage-line', '--sage-line-2',
        '--ok-line', '--ok-line-2', '--ok-line-3', '--warn-border', ...range('--warn-border', 2, 4), '--nav-hover-border', '--nav-hover-border-2'],
    I: ['--text', '--accent', '--accent-hover', '--accent-fill', '--accent-hover-2', '--ok', '--ok-fill', '--ok-ink', '--ok-ink-2', '--info-ink',
        '--warn-fg', ...range('--warn-fg', 2, 5)],
    T: ['--ink-2'],
    M: ['--muted', '--muted-2', '--muted-3', '--neutral-3-2', '--neutral-3-3'],
    N1: ['--neutral-1', '--sage-1', '--sage-2', '--sage-3', '--sage-4'],
    N2: ['--neutral-2', '--sage-5', '--sage-6'],
    N3: ['--neutral-3', '--sage-7', '--sage-8', '--graphite'],
    O: ['--on-accent'],
    R1: ['--danger', '--danger-ink', '--danger-ink-2'],
    R2: ['--danger-hover', '--danger-ink-deep', ...range('--danger-ink-deep', 2, 4)],
    R3: ['--danger-soft', ...range('--danger-soft', 2, 5)],
    R4: ['--danger-line', ...range('--danger-line', 2, 4)],
    G0: ['--grass-0'], G1: ['--grass-1'], G2: ['--grass-2'], G3: ['--grass-3'], G4: ['--grass-4'],
    XF: ['--focus-ring'],
};
const DAY: Groups = {
    S: ['#F5F0E6', SHARED_NAMES.S], F: ['#ECE5D8', SHARED_NAMES.F], H: ['rgba(29, 27, 24, .06)', SHARED_NAMES.H],
    L: ['#D9D1C3', SHARED_NAMES.L], I: ['#1D1B18', SHARED_NAMES.I], T: ['#4A453E', SHARED_NAMES.T], M: ['#645E54', SHARED_NAMES.M],
    N1: ['#B9AF9F', SHARED_NAMES.N1], N2: ['#9C9182', SHARED_NAMES.N2], N3: ['#6F675B', SHARED_NAMES.N3], O: ['#F5F0E6', SHARED_NAMES.O],
    R1: ['#A8322A', SHARED_NAMES.R1], R2: ['#8C2A23', SHARED_NAMES.R2], R3: ['#F6E7E4', SHARED_NAMES.R3], R4: ['#E4C6C2', SHARED_NAMES.R4],
    G0: ['#E6DFD2', SHARED_NAMES.G0], G1: ['#CDC3B2', SHARED_NAMES.G1], G2: ['#A29785', SHARED_NAMES.G2], G3: ['#625A4F', SHARED_NAMES.G3], G4: ['#1D1B18', SHARED_NAMES.G4],
    XF: ['rgba(29, 27, 24, .5)', SHARED_NAMES.XF],
    XS: ['rgba(29, 27, 24, .45)', ['--scrim', '--scrim-soft', '--scrim-2', '--scrim-3']],
};
const NIGHT: Groups = {
    S: ['#1C1A17', SHARED_NAMES.S], F: ['#2C2925', SHARED_NAMES.F], H: ['rgba(242, 237, 227, .08)', SHARED_NAMES.H],
    L: ['#3A3630', SHARED_NAMES.L], I: ['#F2EDE3', SHARED_NAMES.I], T: ['#D6CFC2', SHARED_NAMES.T], M: ['#A39B8E', SHARED_NAMES.M],
    N1: ['#4F4A42', SHARED_NAMES.N1], N2: ['#7A7367', SHARED_NAMES.N2], N3: ['#A39B8E', SHARED_NAMES.N3], O: ['#1C1A17', SHARED_NAMES.O],
    R1: ['#E5766B', SHARED_NAMES.R1], R2: ['#F0918A', SHARED_NAMES.R2], R3: ['#33201D', SHARED_NAMES.R3], R4: ['#5A3A34', SHARED_NAMES.R4],
    G0: ['#2C2925', SHARED_NAMES.G0], G1: ['#4F4A42', SHARED_NAMES.G1], G2: ['#7A7367', SHARED_NAMES.G2], G3: ['#A39B8E', SHARED_NAMES.G3], G4: ['#F2EDE3', SHARED_NAMES.G4],
    XF: ['rgba(242, 237, 227, .5)', SHARED_NAMES.XF],
    XS: ['rgba(0, 0, 0, .62)', ['--scrim', '--scrim-2', '--scrim-3']],
    XS2: ['rgba(0, 0, 0, .5)', ['--scrim-soft']],
};
const STUDY_ACCENT = ['--accent', '--accent-hover', '--ok', '--accent-fill', '--accent-hover-2', '--ok-fill'];
const STUDY_DAY: Groups = {
    A: ['#2B4A6F', STUDY_ACCENT],
    G1: ['#B8C3D1', ['--grass-1']], G2: ['#8598B0', ['--grass-2']], G3: ['#4F6A8B', ['--grass-3']], G4: ['#2B4A6F', ['--grass-4']],
};
const STUDY_NIGHT: Groups = {
    A: ['#8FA9BE', STUDY_ACCENT],
    G1: ['#34495A', ['--grass-1']], G2: ['#4A6A80', ['--grass-2']], G3: ['#6F92AA', ['--grass-3']], G4: ['#A0BED0', ['--grass-4']],
};

function groupMismatches(selector: string, groups: Groups): string[] {
    const out: string[] = [];
    for (const [g, [value, names]] of Object.entries(groups)) {
        for (const n of names) {
            let v: string;
            try { v = tokenValue(selector, n); } catch { out.push(`${g} ${n}: 없음`); continue; }
            if (norm(v) !== norm(value)) out.push(`${g} ${n}: ${v} ≠ ${value}`);
        }
    }
    return out;
}
const groupNames = (groups: Groups) => Object.values(groups).flatMap(([, names]) => names).sort();

describe('에디토리얼 팔레트 — 이름 유지 · 값만 (PR-2)', () => {
    it('REQ-01 · 낮 토큰이 에디토리얼 값이다(군 안 모든 이름 · 군 밖 토큰 0)', () => {
        expect(groupMismatches(LIGHT_SELECTOR, DAY)).toEqual([]);
        expect(colorTokens(LIGHT_SELECTOR)).toEqual(groupNames(DAY));
        // 별칭 호버 면 — 낮·밤 둘 다 같은 이름을 가리킨다(독서등은 body에서 다시 깔아야 따라온다 — 위 별칭 단언).
        expect(tokenValue(LIGHT_SELECTOR, '--nav-hover-bg')).toBe('var(--sage-hover)');
        expect(tokenValue(LIGHT_SELECTOR, '--font-serif')).toMatch(/^'Noto Serif KR'/);
    });

    it('REQ-11 · 다크는 밤 책상 값이다', () => {
        expect(groupMismatches(DARK_SELECTOR, NIGHT)).toEqual([]);
        expect(colorTokens(DARK_SELECTOR)).toEqual(groupNames(NIGHT));
        expect(tokenValue(DARK_SELECTOR, '--nav-hover-bg')).toBe('var(--sage-hover)');
    });
});

/** 합성색(알파 칠을 바탕 위에 얹은 결과) — 포커스 링·호버 면처럼 반투명 토큰의 실제 대비를 재려고. */
function over(rgba: string, bgHex: string): string {
    const m = /rgba\(\s*(\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\s*\)/.exec(rgba);
    if (!m) throw new Error(`rgba 아님: ${rgba}`);
    const a = Number(m[4]);
    const bg = hexToRgb(bgHex);
    const mix = [1, 2, 3].map((i, k) => Math.round(Number(m[i]) * a + bg[k] * (1 - a)));
    return '#' + mix.map((v) => v.toString(16).padStart(2, '0')).join('');
}

describe('에디토리얼 대비 — 토큰 값으로 계산 (PR-2)', () => {
    it('계측기 자기검증 — 검정/흰색 = 21.00', () => {
        expect(contrast('#000000', '#FFFFFF')).toBeCloseTo(21, 2);
    });

    it('REQ-02 · 글자·바탕 쌍이 4.5 이상(낮·밤·공부·위험)', () => {
        const pairs: Array<[string, string, string]> = [];
        for (const sel of [LIGHT_SELECTOR, DARK_SELECTOR]) {
            const t = (n: string) => tokenValue(sel, n);
            pairs.push(
                [`${sel} text/bg`, t('--text'), t('--bg')], [`${sel} ink-2/bg`, t('--ink-2'), t('--bg')],
                [`${sel} muted/bg`, t('--muted'), t('--bg')], [`${sel} muted/sage-soft`, t('--muted'), t('--sage-soft')],
                [`${sel} on-accent/accent-fill`, t('--on-accent'), t('--accent-fill')],
                [`${sel} danger/bg`, t('--danger'), t('--bg')], [`${sel} danger-ink-deep/bg`, t('--danger-ink-deep'), t('--bg')],
                [`${sel} danger/danger-soft`, t('--danger'), t('--danger-soft')],
            );
        }
        pairs.push(
            ['공부 낮 accent/bg', tokenValue('.is-study', '--accent'), tokenValue(LIGHT_SELECTOR, '--bg')],
            ['공부 낮 on-accent/accent-fill', tokenValue(LIGHT_SELECTOR, '--on-accent'), tokenValue('.is-study', '--accent-fill')],
            ['공부 밤 accent/bg', tokenValue(`${DARK_SELECTOR} .is-study`, '--accent'), tokenValue(DARK_SELECTOR, '--bg')],
            ['공부 밤 on-accent/accent-fill', tokenValue(DARK_SELECTOR, '--on-accent'), tokenValue(`${DARK_SELECTOR} .is-study`, '--accent-fill')],
        );
        expect(pairs.length).toBe(20);
        expect(pairs.filter(([, a, b]) => contrast(a, b) < 4.5).map(([n, a, b]) => `${n} ${contrast(a, b).toFixed(2)}`)).toEqual([]);
    });

    it('REQ-02 · 비글자 경계(입력칸·고른 칩)·포커스 링이 3:1 이상', () => {
        const resolve = (v: string, sel: string) => {
            const m = /^var\((--[\w-]+)\)$/.exec(v.trim());
            return m ? tokenValue(sel, m[1]) : v.trim();
        };
        // 입력칸 규칙에서 실제 테 색을 뽑는다 — 누가 가는 선(--border)으로 「정리」하면 여기서 운다.
        const INPUTS = ['.auth-page .auth-input', '.settings-page .set-input', '.book-search-form input[type=text]', '.field input', '.study-editor'];
        const map = new Map(rules().map((r) => [r.selector, r.body]));
        const borders = INPUTS.map((s) => {
            const body = map.get(s);
            if (!body) throw new Error(`${s} 규칙이 사라졌다`);
            const m = /(^|;)\s*border\s*:\s*1px\s+solid\s+([^;]+)/.exec(body);
            if (!m) throw new Error(`${s}에 1px solid 테가 없다`);
            return [s, m[2].trim()] as const;
        });
        const low: string[] = [];
        for (const sel of [LIGHT_SELECTOR, DARK_SELECTOR]) {
            const bg = tokenValue(sel, '--bg');
            for (const [s, c] of borders) {
                const r = contrast(resolve(c, sel), bg);
                if (r < 3) low.push(`${sel} ${s} ${r.toFixed(2)}`);
            }
            const chip = contrast(tokenValue(sel, '--text'), bg);
            if (chip < 3) low.push(`${sel} 고른 칩 ${chip.toFixed(2)}`);
            const ring = contrast(over(tokenValue(sel, '--focus-ring'), bg), bg);
            if (ring < 3) low.push(`${sel} 포커스 링 ${ring.toFixed(2)}`);
        }
        expect(low).toEqual([]);
        // 음성 대조 — 장식 선은 경계로 못 쓴다(이 값이 3을 넘으면 계산기가 고장이다).
        expect(contrast(tokenValue(LIGHT_SELECTOR, '--border'), tokenValue(LIGHT_SELECTOR, '--bg'))).toBeLessThan(3);
    });
});

/**
 * 공부 범위 — 토큰 블록 밖에서 `.is-study`·`[data-mode="study"]`를 셀렉터에 품은 규칙은 <b>정확 일치 허용목록 셋</b>뿐이다.
 *
 * <p>옛 공부 전용 값 규칙(푸른 히어로 면 · 칩 면 · 숫자 · `.is-study .btn-primary { background-color: var(--sage-soft) }`)은
 * 토큰이 파란 잉크를 주게 되면서 걷었다. 특히 마지막 것은 <b>토큰을 쓰는 규칙</b>이라 리터럴 래칫에 안 걸리는데, 남기면
 * 공부 주 버튼이 옅은 면 위 미색 글자(대비 ≈ 1)로 사라진다 — 그래서 값이 리터럴이든 토큰이든 목록 밖이면 운다.
 */
const STUDY_SCOPED_ALLOWED = [
    'body .focus-stack.is-merged > .dash-card.is-study',
    '#side-rails[data-mode="study"] .dash-mode-toggle [data-pick="study"]',
    '#side-rails[data-mode="study"] .rail-nav-reading',
];
function studyScopedLeaks(rs: Rule[]): string[] {
    const tokenOnly = (body: string) => body.split(';').map((d) => d.trim()).filter(Boolean).every((d) => d.startsWith('--'));
    return rs
        .filter((r) => /\.is-study\b|\[data-mode="study"\]/.test(r.selector.replace(/:not\([^)]*\)/g, '')))
        .filter((r) => !tokenOnly(r.body) && !STUDY_SCOPED_ALLOWED.includes(r.selector))
        .map((r) => r.selector);
}

describe('공부 — 강조 자리만 파란 잉크 (PR-2)', () => {
    it('REQ-12 · 공부 토큰은 강조 6 + 잔디 4뿐이고 값이 파란 잉크다(낮·밤)', () => {
        expect(groupMismatches('.is-study', STUDY_DAY)).toEqual([]);
        expect(colorTokens('.is-study')).toEqual(groupNames(STUDY_DAY));
        expect(groupMismatches(`${DARK_SELECTOR} .is-study`, STUDY_NIGHT)).toEqual([]);
        expect(colorTokens(`${DARK_SELECTOR} .is-study`)).toEqual(groupNames(STUDY_NIGHT));
    });

    it('REQ-12 · 공부 범위 규칙은 허용목록 셋뿐이다(토큰 규칙도 목록 밖이면 운다)', () => {
        // 음성 대조 — 3067 꼴(토큰으로 칠하는 공부 규칙)을 넣으면 잡힌다.
        expect(studyScopedLeaks(parse('.is-study .btn-primary { background-color: var(--sage-soft) }'))).toEqual(['.is-study .btn-primary']);
        const rs = rules();
        // 양성 대조 — 허용목록 셋이 실재한다.
        const present = new Set(rs.map((r) => r.selector));
        expect(STUDY_SCOPED_ALLOWED.filter((s) => !present.has(s))).toEqual([]);
        expect(studyScopedLeaks(rs)).toEqual([]);
    });
});

describe('독서등 — 밤 책상 + 종이 한 장, 불빛 0 (PR-2)', () => {
    it('REQ-13 · 불빛·글로우·밤 덮기가 없고 합쳐진 카드는 종이로 칠한다', () => {
        const rs = rules();
        expect(rs.filter((r) => r.selector === 'body.study-lamp::after').map((r) => r.selector)).toEqual([]);
        // 독서등 밤 = 다크 팔레트 그대로 — body.study-lamp가 따로 --bg·--card-bg를 덮지 않는다(토큰 블록 목록 소속만).
        const lampOwn = rs.filter((r) => r.selector === 'body.study-lamp' && r.group.length === 1);
        expect(lampOwn.filter((r) => /--(bg|card-bg)\s*:/.test(r.body)).map((r) => r.body.trim())).toEqual([]);
        expect(rs.filter((r) => r.selector.includes('.lamp-page') && /(^|;)\s*box-shadow\s*:/.test(r.body)).map((r) => r.selector)).toEqual([]);
        // 함정 — 합쳐진 카드를 투명으로 두면 밤 위에 잉크 글자가 떠 안 읽힌다. 낮 토큰 목록이 .lamp-page에 종이를 다시 깐다.
        const merged = rs.filter((r) => r.selector === '.focus-stack.is-merged').map((r) => r.body).join(';');
        expect(merged).toMatch(/(^|;)\s*background\s*:\s*var\(--card-bg\)/);
    });
});
