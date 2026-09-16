import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * 필기 편집기의 <b>제목은 본문과 눈으로 구별돼야 한다</b>.
 *
 * <p>2026-09-16까지 편집기 제목은 `h1 19px / h2 16px`이었고 본문도 16px이었다 — 즉 <b>H2는 본문과
 * 글자 크기가 완전히 같았고</b> H1도 3px(1.19배)밖에 크지 않았다. 게다가 본문 서체 고운돋움은 400
 * 단일 face라 제목의 굵게가 <b>합성 볼드</b>였다. 크기·굵기 두 축이 다 흐려서, 필기를 훑어볼 때
 * 제목이 제목으로 잡히지 않았다(사용자 지적).
 *
 * <p>그래서 세 축을 갈라 둔다 — 크기(본문 대비 배수) · 위 여백 · 서체(고운바탕은 실제 700 face가
 * 로드돼 있어 합성 볼드를 피한다). 이 테스트가 단독으로 잡는 실패는 <b>그 세 축 중 하나가 본문과
 * 같은 값으로 되돌아가는 것</b>이다 — 되돌리면 화면을 열지 않아도 여기서 먼저 운다.
 *
 * <p>⚠️ 값 검사이므로 <b>음성 판정 전용</b>이다. 「대비가 충분하다」는 미감은 이 테스트가 증명하지
 * 못한다(그건 화면에서 본다). 증명하는 것은 「본문과 같아지지는 않았다」까지다.
 */

const HERE = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const APP_CSS = join(HERE, '..', '..', 'src', 'main', 'resources', 'static', 'css', 'app.css');
const css = readFileSync(APP_CSS, 'utf8');

/** 셀렉터가 여는 선언 블록 본문. 줄 시작에 앵커를 두는 것이 일부러다 — 앵커가 없으면 더 긴 셀렉터
 *  (`.focus-stack.is-merged .study-editor .ProseMirror`)가 접미사로 걸려 엉뚱한 블록을 읽는다. */
function block(selector: string): string {
    const esc = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const m = css.match(new RegExp(`^${esc}\\s*\\{([^}]*)\\}`, 'm'));
    if (!m) throw new Error(`셀렉터를 못 찾음: ${selector}`);
    return m[1];
}

function px(selector: string, prop: string): number {
    const m = block(selector).match(new RegExp(`${prop}\\s*:\\s*([\\d.]+)px`));
    if (!m) throw new Error(`${selector} 에 ${prop} 선언이 없음`);
    return Number(m[1]);
}

const H1 = '.study-editor .ProseMirror h1';
const H2 = '.study-editor .ProseMirror h2';
/** 본문 크기의 단일 출처 — 합쳐진 카드(측정 중 화면)가 명시한 값을 그대로 읽는다. */
const BODY_PX = px('.focus-stack.is-merged .study-editor .ProseMirror', 'font-size');
/** 블록끼리의 기본 간격(`> *`의 아래 여백). 제목 위 여백은 이보다 넓어야 단락 구분으로 읽힌다.
 *  `margin: 0 0 8px` 단축의 <b>마지막</b> 값이라 `px()`(첫 값 전용)를 못 쓴다. */
const BLOCK_GAP = Number(
    block('.study-editor .ProseMirror > *').match(/margin\s*:[^;]*?([\d.]+)px\s*;/)?.[1]
        ?? (() => { throw new Error('블록 기본 간격을 못 읽음'); })(),
);

describe('필기 편집기 제목 — 본문과의 대비', () => {
    it('H1은 본문보다 1.5배 이상 크다', () => {
        expect(px(H1, 'font-size')).toBeGreaterThanOrEqual(BODY_PX * 1.5);
    });

    it('H2는 본문보다 1.15배 이상 크고, H1보다는 작다', () => {
        const h2 = px(H2, 'font-size');
        expect(h2).toBeGreaterThanOrEqual(BODY_PX * 1.15);
        expect(h2).toBeLessThan(px(H1, 'font-size'));
    });

    it('두 제목은 고운바탕 700 — 합성 볼드가 아닌 실제 굵기', () => {
        for (const sel of [H1, H2]) {
            expect(block(sel)).toMatch(/font-family\s*:\s*'Gowun Batang'/);
            expect(block(sel)).toMatch(/font-weight\s*:\s*700/);
        }
    });

    it('제목 위 여백이 블록 기본 간격보다 넓다', () => {
        for (const sel of [H1, H2]) {
            expect(px(sel, 'margin-top')).toBeGreaterThan(BLOCK_GAP);
        }
    });
});
