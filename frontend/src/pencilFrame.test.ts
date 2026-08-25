import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * 카드와 폼 컨트롤은 <b>연필 테두리(`--pencil-frame`)를 반드시 받는다</b>.
 *
 * <p>종이·연필 테마는 표준 테두리 64곳에 일괄 적용됐지만, 그 일괄이 닿는 기준은
 * 「`.card`이거나 `border: 1px solid var(--border)`인 것」이었다. 그래서 <b>자체 클래스로 테두리를
 * 따로 선언한 자리는 통째로 빠진 채</b> 남았다 — `.dash-card`가 그랬고, 그건 홈의 카드 4개(측정 히어로·
 * 잔디·바로가기·정원)와 책방의 카드 전부다. <b>로그인 뒤 가장 많이 보는 두 화면</b>이 매끈한 선으로 남아
 * 있었는데, 정작 그 카드 <b>안</b>의 `.dash-state-panel`·`.dash-nav-tile`엔 연필선이 있어 한 화면에서
 * 두 양식이 갈렸다(2026-08-25 실측 발견 — plan.md 「종이·연필 테마 — 남은 검증 2건」이 예고한 자리다).
 *
 * <p>두 번째 뿌리는 <b>`border` 단축 선언이 `border-image`를 initial로 리셋</b>한다는 것이다.
 * `.set-card`엔 프레임이 있는데 `.set-card-danger`가 `border: 1.5px solid …`로 덮어 그 카드 하나만
 * 매끈했고, 피드백 폼 입력도 같은 이유로 다른 화면의 입력과 갈렸다. 즉 <b>상속으로 받은 프레임은
 * 나중 선언 하나에 조용히 사라진다</b> — 눈으로 훑어서는 못 찾고, 화면을 열어 재야 보인다.
 *
 * <p>그래서 자리마다 고치는 대신 여기서 <b>소스를 전수 스캔</b>한다. 다음에 카드를 새로 만드는 사람이
 * 프레임을 빠뜨리면 이 테스트가 먼저 운다(noEmoji.test.ts와 같은 사고방식).
 */

const HERE = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const APP_CSS = join(HERE, '..', '..', 'src', 'main', 'resources', 'static', 'css', 'app.css');

/**
 * 폼 컨트롤은 <b>태그 셀렉터로만</b> 잡는다.
 *
 * <p>`\b(input|select|textarea)\b`로 두면 `.pbti-select-rep` 같은 <b>클래스 이름 속 단어</b>가 걸린다
 * (하이픈이 단어 경계라서다). 그 오탐을 실제로 한 번 냈고, 예외 목록으로 덮으면 규칙이 아니라
 * 예외 목록이 커진다 — 경계를 셀렉터 문법으로 좁히는 쪽이 맞다.
 */
const FORM_TAG = /(^|[\s,>+~(])(input|select|textarea)(?=[\s,:.[)]|$)/i;

/** 카드 역할 — 클래스 이름에 `card`가 든 것(`.dash-card`·`.set-card-danger`·`.shop-header-card` …). */
const CARD_NAME = /\bcard\b/i;

/** 테두리를 실제로 그리는 `border` 단축 선언(색·굵기 무관, `none`·`0`은 제외). */
const DRAWS_BORDER = /(^|;|\s)border\s*:\s*[^;]*\b(solid|dashed|dotted)\b/;

/**
 * 프레임을 <b>일부러</b> 안 받는 자리.
 *
 * <p>`.margin-card-btn`은 높이 20px 남짓한 pill 버튼이다(`padding: 3px 10px`·`border-radius: 999px`).
 * 연필 프레임은 300×300 SVG를 8px 슬라이스로 늘여 쓰므로 <b>그만한 높이에서는 결이 뭉개진다</b> —
 * 미니앱이 20px 배지에서 같은 이유로 포기한 자리와 같다. 게다가 이 버튼의 `currentColor` 테두리는
 * 여백 배경 6종 어디서나 읽히게 하려는 설계 의도라, 프레임으로 덮으면 그 의도가 사라진다.
 */
const ALLOWED = new Set(['.margin-card-btn']);

/** `app.css`를 규칙 블록으로 쪼갠다 — `@media` 안쪽 블록은 안쪽이 잡히므로 그대로 대상이 된다. */
function rules(): Array<{ selector: string; body: string }> {
    const css = readFileSync(APP_CSS, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    const out: Array<{ selector: string; body: string }> = [];
    const re = /([^{}]+)\{([^{}]*)\}/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(css))) {
        const selector = m[1].trim().replace(/\s+/g, ' ');
        if (selector.startsWith('@') || selector.includes(':root')) continue;
        out.push({ selector, body: m[2] });
    }
    return out;
}

describe('연필 테두리 전수 가드', () => {
    it('카드·폼 컨트롤이 테두리를 그리면 연필 프레임도 함께 준다', () => {
        const violations = rules()
            .filter(({ selector }) => CARD_NAME.test(selector) || FORM_TAG.test(selector))
            .filter(({ selector }) => !ALLOWED.has(selector))
            .filter(({ body }) => DRAWS_BORDER.test(body))
            .filter(({ body }) => !/border-image\s*:/.test(body))
            .map(({ selector, body }) => `${selector} {${(body.match(/border\s*:[^;]*/) ?? [''])[0].trim()}}`);

        expect(violations).toEqual([]);
    });

    /**
     * 가드가 <b>「항상 통과」 쪽으로 고장나지 않았음</b>을 증명한다.
     *
     * <p>위 단언은 위반이 0이면 초록인데, 셀렉터 대상이 하나도 안 잡혀도 똑같이 초록이다.
     * 그래서 「프레임을 제대로 받은 카드가 실제로 여럿 있다」를 따로 센다 — 이게 없으면
     * 정규식을 잘못 고쳐 대상이 0이 된 순간에도 전 스위트가 조용히 통과한다.
     */
    it('프레임을 받은 카드가 실제로 잡힌다 (계측기 생존 확인)', () => {
        const framed = rules().filter(
            ({ selector, body }) => CARD_NAME.test(selector) && /border-image\s*:/.test(body),
        );

        expect(framed.length).toBeGreaterThanOrEqual(3);
    });
});
