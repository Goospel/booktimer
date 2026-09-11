import { test, expect, type Page } from '@playwright/test';

// 연필 프레임 가림 프로브 — border-image가 패딩 박스 **안쪽**으로 그리는 7px 띠를
// 불투명한 자식(배경 있는 요소)이나 <img> 자기 콘텐츠가 덮어 선이 얇아지는 결함을 렌더 트리에서 잡는다.
//
// 왜 정적 테스트(vitest)가 아닌가: 이 결함은 「선언이 있느냐」가 아니라 「누가 누구 위에 그려지느냐」다.
// app.css를 문자열로 파싱하면 `margin: 0 4px`가 있는지밖에 못 보고 그건 구현을 베낀 테스트다.
// 그래서 실제 페이지에서 elementsFromPoint로 잰다.
//
// ⚠️ 이 스위트는 양성·음성 대조군을 내장한다 — 일부러 가리는 자식을 주입했을 때 프로브가
// 그걸 못 잡으면(= 통과만 하는 계측기) 스위트 자체가 실패한다. 통과는 증거가 아니다.

type Finding = { selector: string; edge: string; x: number; y: number; by: string };

/**
 * 페이지 컨텍스트에서 돈다(Playwright가 소스를 직렬화하므로 **자기완결**이어야 한다 — 외부 참조 금지).
 *
 * 판정 규칙:
 *  1. 대상 = computed borderImageSource !== 'none'인 모든 요소.
 *     단 ::after/::before가 프레임을 들고 있으면 제외 — 그건 프레임이 자식 **위에** 얹힌 형태고
 *     elementsFromPoint는 pseudo를 못 보므로 이 예외가 없으면 오버레이 처방이 영원히 오탐된다.
 *  2. 표본점 = border-box 각 변 2.5px 안쪽(스트로크 한가운데), 변마다 15%/50%/85% → 12점.
 *  3. 결함 = 그 점의 스택에서 el보다 앞(=위)에 있는 el의 자손 중 배경 알파 > .5, opacity > .5인 것.
 *  4. 대체 요소(<img>)는 자손이 아니라 자기 콘텐츠가 덮으므로 border+padding < 4px인 변을 결함으로 친다.
 */
function pencilProbe(): Finding[] {
    const out: Finding[] = [];
    const desc = (e: Element) =>
        e.tagName + (e.classList.length ? '.' + Array.from(e.classList).join('.') : '');
    const alphaOf = (color: string) => {
        const n = color.match(/[\d.]+/g);
        if (!n) return 0;
        return n.length >= 4 ? parseFloat(n[3]) : 1;
    };

    for (const el of Array.from(document.querySelectorAll('*'))) {
        const cs = getComputedStyle(el);
        if (!cs.borderImageSource || cs.borderImageSource === 'none') continue;
        if (getComputedStyle(el, '::after').borderImageSource !== 'none') continue;
        if (getComputedStyle(el, '::before').borderImageSource !== 'none') continue;

        let r = el.getBoundingClientRect();
        if (r.width < 16 || r.height < 16) continue;

        if (el.tagName === 'IMG') {
            const edges: Array<[string, string, string]> = [
                ['top', cs.borderTopWidth, cs.paddingTop],
                ['right', cs.borderRightWidth, cs.paddingRight],
                ['bottom', cs.borderBottomWidth, cs.paddingBottom],
                ['left', cs.borderLeftWidth, cs.paddingLeft],
            ];
            for (const [edge, bw, pd] of edges) {
                if (parseFloat(bw) + parseFloat(pd) < 4) {
                    out.push({ selector: desc(el), edge, x: -1, y: -1, by: 'own content' });
                }
            }
            continue;
        }

        el.scrollIntoView({ block: 'center', inline: 'center' });
        r = el.getBoundingClientRect();

        const points: Array<[string, number, number]> = [];
        for (const f of [0.15, 0.5, 0.85]) {
            points.push(['top', r.left + r.width * f, r.top + 2.5]);
            points.push(['bottom', r.left + r.width * f, r.bottom - 2.5]);
            points.push(['left', r.left + 2.5, r.top + r.height * f]);
            points.push(['right', r.right - 2.5, r.top + r.height * f]);
        }

        for (const [edge, x, y] of points) {
            if (x < 0 || y < 0 || x > window.innerWidth || y > window.innerHeight) continue;
            const stack = document.elementsFromPoint(x, y);
            const idx = stack.indexOf(el);
            const above = idx === -1 ? stack : stack.slice(0, idx);
            for (const c of above) {
                if (c === el || !el.contains(c)) continue;
                const ccs = getComputedStyle(c);
                if (alphaOf(ccs.backgroundColor) > 0.5 && parseFloat(ccs.opacity) > 0.5) {
                    out.push({
                        selector: desc(el),
                        edge,
                        x: Math.round(x),
                        y: Math.round(y),
                        by: desc(c),
                    });
                    break;
                }
            }
        }
    }
    return out;
}

async function probe(page: Page): Promise<Finding[]> {
    return await page.evaluate(pencilProbe);
}

/** 한 선택자에 걸린 결함만 — 전수 프로브라 다른 자리의 결함이 섞여 들어오는 것을 막는다. */
const on = (found: Finding[], needle: string) => found.filter((f) => f.selector.includes(needle));

async function setTheme(page: Page, theme: 'light' | 'dark') {
    await page.evaluate((t) => {
        document.documentElement.dataset.theme = t;
    }, theme);
}

test.describe('연필 프레임 안쪽 띠가 가려지지 않는다', () => {
    // ── 대조군: 프로브가 실제로 판별력이 있는지부터 못 박는다 ───────────────────────
    test('양성 대조군 — 일부러 가리는 자식을 주입하면 프로브가 그 요소를 잡는다', async ({ page }) => {
        await page.goto('/books');
        await page.evaluate(() => {
            const box = document.createElement('div');
            box.className = 'card';
            box.id = 'probe-positive';
            box.setAttribute('style', 'padding:0;width:200px;height:60px');
            const child = document.createElement('div');
            child.setAttribute('style', 'background:#f00;height:100%');
            box.appendChild(child);
            document.body.appendChild(box);
        });

        const found = await probe(page);
        const hits = found.filter((f) => f.by === 'DIV');
        expect(hits.length, `프로브가 주입한 가림을 못 봤다 — 계측기가 죽었다. found=${JSON.stringify(found)}`)
            .toBeGreaterThan(0);
        expect(hits.every((f) => f.selector === 'DIV.card')).toBe(true);
    });

    test('음성 대조군 — 자식이 투명하면 0건', async ({ page }) => {
        await page.goto('/books');
        await page.evaluate(() => {
            const box = document.createElement('div');
            box.className = 'card';
            box.id = 'probe-negative';
            box.setAttribute('style', 'padding:0;width:200px;height:60px');
            const child = document.createElement('div');
            child.setAttribute('style', 'background:transparent;height:100%');
            box.appendChild(child);
            document.body.appendChild(box);
        });

        const found = await probe(page);
        expect(found.filter((f) => f.by === 'DIV')).toEqual([]);
    });

    // ── 실제 세 자리 ──────────────────────────────────────────────────────────────
    for (const theme of ['light', 'dark'] as const) {
        test(`① 편집기 툴바가 프레임을 덮지 않는다 (${theme})`, async ({ page }) => {
            await page.goto('/study');
            await page.waitForSelector('[data-testid="recall-body"]', { state: 'visible' });
            await setTheme(page, theme);

            const found = await probe(page);
            console.log(`[전수 ①/${theme}]`, JSON.stringify(found));
            expect(on(found, 'study-editor')).toEqual([]);
        });

        test(`② 세그먼트 칩이 프레임을 덮지 않는다 (${theme})`, async ({ page }) => {
            await page.goto('/books');
            await page.waitForSelector('.shelf-filter-chips', { state: 'visible' });
            await setTheme(page, theme);

            const found = await probe(page);
            console.log(`[전수 ②/${theme}]`, JSON.stringify(found));
            expect(on(found, 'shelf-filter-chips')).toEqual([]);
        });

        test(`③ 책 상세 표지 <img>가 자기 프레임을 덮지 않는다 (${theme})`, async ({ page }) => {
            // 로컬 시더는 계정만 심고 책은 안 심어서 /books/{id}를 만들 수 없다 → CSS 규칙 대 DOM 모양을
            // 합성으로 검사한다(규칙 자체의 회귀는 잡힌다). 실제 페이지 확인은 수동 U-3 몫.
            await page.goto('/books');
            await page.waitForSelector('.shelf-filter-chips', { state: 'visible' });
            await setTheme(page, theme);
            await page.evaluate(() => {
                const sec = document.createElement('section');
                sec.className = 'card book-detail-head';
                const img = document.createElement('img');
                img.className = 'book-cover';
                // 유효한 1×1 GIF — 깨진 이미지 아이콘이 콘텐츠 자리를 대신하지 않게.
                img.src =
                    'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
                sec.appendChild(img);
                document.body.appendChild(sec);
            });
            await page.waitForFunction(
                () => (document.querySelector('.book-detail-head .book-cover') as HTMLImageElement)?.complete,
            );

            const found = await probe(page);
            console.log(`[전수 ③/${theme}]`, JSON.stringify(found));
            expect(on(found, 'IMG.book-cover')).toEqual([]);
        });
    }
});
