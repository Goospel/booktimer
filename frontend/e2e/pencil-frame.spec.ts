import { test, expect, type Page } from '@playwright/test';
import sharp from 'sharp';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// bootRun이 서빙하는 실체는 `src/main/resources`가 아니라 **`build/resources/main` 복사본**이다(T-187).
// 그래서 CSS를 고치고 `processResources`를 안 돌리면 이 스위트는 옛 CSS를 재고, 증상이 「고친 게
// 안 먹었다」로 읽혀 **가짜 RED**가 된다(2026-09-11 #1098에서 구현·리뷰가 각 1회 걸렸다).
// 아래 첫 테스트가 그 상태를 이름 붙여 실패시킨다 — 조용히 틀리는 것만 막으면 된다.
// 전제(2026-09-12 실측): `processResources`는 정적 리소스를 **바이트 그대로** 복사하고 HTTP도
// 그 바이트를 그대로 준다(233081B, sha256 앞 16자 일치). 그래서 지문 대조가 성립한다.
const CSS_SOURCE = fileURLToPath(new URL('../../src/main/resources/static/css/app.css', import.meta.url));
const sha12 = (b: Buffer): string => createHash('sha256').update(b).digest('hex').slice(0, 12);

// 연필 프레임 가림 프로브 — border-image가 패딩 박스 **안쪽**으로 그리는 7px 띠를
// 불투명한 자식(배경 있는 요소)이나 <img> 자기 콘텐츠가 덮어 선이 얇아지는 결함을 렌더 트리에서 잡는다.
//
// 왜 정적 테스트(vitest)가 아닌가: 이 결함은 「선언이 있느냐」가 아니라 「누가 누구 위에 그려지느냐」다.
// app.css를 문자열로 파싱하면 `margin: 0 4px`가 있는지밖에 못 보고 그건 구현을 베낀 테스트다.
//
// ⚠️ 이 스위트가 **무엇을 배제하는가**(2026-09-11 독립 리뷰가 돌연변이로 잡은 공허함 2건 이후):
//  - 프로브 하나로는 부족하다. 프로브는 `borderImageSource !== 'none'`인 요소만 재므로,
//    **프레임 선언이 사라지면 검사 대상이 0개가 되어 조용히 초록**이다(M3: `::after` 규칙 삭제 → 초록).
//    그래서 자리마다 **「프레임이 실제로 존재한다」를 먼저 단언**한다.
//  - 프로브는 `elementsFromPoint`를 쓰는데 `pointer-events: none`인 오버레이는 그 목록에 안 나온다.
//    즉 ②처럼 프레임을 `::after`로 얹은 자리는 **프로브가 원리상 눈이 먼다**(M6: 자식에 z-index 1을
//    주면 오버레이 위로 올라가 선이 실제로 사라지는데 프로브는 초록). 그래서 ②는 ⑴ 오버레이 존재·형상
//    ⑵ 자식 스택 조건 ⑶ **화면 픽셀에서 선이 실제로 보이는지**를 따로 단언한다.
//  - 대조군: 빨간 배경 자식을 일부러 주입했을 때 프로브가 못 잡으면 스위트가 먼저 실패한다.
//  - 스킵은 말없이 넘어가지 않는다 — 16px 미만 요소와 뷰포트 밖 표본점은 세어서 돌려주고,
//    자리마다 「실제로 몇 점을 쟀는가」를 단언한다(「0건」과 「안 재봄」을 가른다).

type Finding = { selector: string; edge: string; x: number; y: number; by: string };
type Examined = { selector: string; kind: 'box' | 'img'; sampled: number; skippedOffscreen: number };
type ProbeResult = { findings: Finding[]; examined: Examined[]; skippedSmall: string[] };

/**
 * 페이지 컨텍스트에서 돈다(Playwright가 소스를 직렬화하므로 **자기완결**이어야 한다 — 외부 참조 금지).
 *
 * 판정 규칙:
 *  1. 대상 = computed borderImageSource !== 'none'인 모든 요소.
 *  2. 표본점 = border-box 각 변 2.5px 안쪽(스트로크 한가운데), 변마다 15%/50%/85% → 12점.
 *  3. 결함 = 그 점의 스택에서 el보다 앞(=위)에 있는 el의 자손 중 배경 알파 > .5, opacity > .5인 것.
 *  4. 대체 요소(<img>)는 자손이 아니라 자기 콘텐츠가 덮으므로 border+padding < 4px인 변을 결함으로 친다.
 */
function pencilProbe(): ProbeResult {
    const findings: Finding[] = [];
    const examined: Examined[] = [];
    const skippedSmall: string[] = [];
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

        let r = el.getBoundingClientRect();
        if (r.width < 16 || r.height < 16) {
            skippedSmall.push(desc(el));
            continue;
        }

        if (el.tagName === 'IMG') {
            const edges: Array<[string, string, string]> = [
                ['top', cs.borderTopWidth, cs.paddingTop],
                ['right', cs.borderRightWidth, cs.paddingRight],
                ['bottom', cs.borderBottomWidth, cs.paddingBottom],
                ['left', cs.borderLeftWidth, cs.paddingLeft],
            ];
            for (const [edge, bw, pd] of edges) {
                if (parseFloat(bw) + parseFloat(pd) < 4) {
                    findings.push({ selector: desc(el), edge, x: -1, y: -1, by: 'own content' });
                }
            }
            examined.push({ selector: desc(el), kind: 'img', sampled: 4, skippedOffscreen: 0 });
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

        let sampled = 0;
        let skippedOffscreen = 0;
        for (const [edge, x, y] of points) {
            if (x < 0 || y < 0 || x > window.innerWidth || y > window.innerHeight) {
                skippedOffscreen++;
                continue;
            }
            sampled++;
            const stack = document.elementsFromPoint(x, y);
            const idx = stack.indexOf(el);
            const above = idx === -1 ? stack : stack.slice(0, idx);
            for (const c of above) {
                if (c === el || !el.contains(c)) continue;
                const ccs = getComputedStyle(c);
                if (alphaOf(ccs.backgroundColor) > 0.5 && parseFloat(ccs.opacity) > 0.5) {
                    findings.push({
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
        examined.push({ selector: desc(el), kind: 'box', sampled, skippedOffscreen });
    }
    return { findings, examined, skippedSmall };
}

async function probe(page: Page): Promise<ProbeResult> {
    return await page.evaluate(pencilProbe);
}

/** 한 선택자에 걸린 결함만 — 전수 프로브라 다른 자리의 결함이 섞여 들어오는 것을 막는다. */
const on = (r: ProbeResult, needle: string) => r.findings.filter((f) => f.selector.includes(needle));
const examinedFor = (r: ProbeResult, needle: string) =>
    r.examined.filter((e) => e.selector.includes(needle));

async function setTheme(page: Page, theme: 'light' | 'dark') {
    await page.evaluate((t) => {
        document.documentElement.dataset.theme = t;
    }, theme);
}

/**
 * 왼쪽 변의 연필선이 **화면 픽셀에 실제로 있는지**. 스트로크는 바깥 가장자리 0.65~3.1px에 사니
 * border-box 안쪽 0~3px을 훑어, 그 픽셀이 **안쪽 내용(활성 칩)과도 바깥 종이와도 다른 색**인지 본다.
 * 두 기준이 다 필요하다 — 한쪽만 보면 못 가른다:
 *   · 프레임이 아예 없으면 그 자리는 「1px 투명 테두리 = 바깥 종이색」 아니면 「칩 배경색」뿐이다.
 *   · 칩이 오버레이 위로 쌓이면 띠 전체가 칩 배경색이 된다.
 * 둘 중 어느 고장이든 최소 한쪽 기준과 같아져 점수가 0으로 떨어진다.
 * ⚠️ 휘도차가 아니라 **RGB 거리**로 잰다 — 다크의 연필선(#B8B1A6)과 활성 칩(세이지)은 밝기가 비슷해
 * 휘도로는 정상인데도 12밖에 안 나온다(실측). 기준색은 종이 결 노이즈를 지우려고 5점 평균을 쓴다.
 */
async function leftEdgeStrokeScore(page: Page, selector: string): Promise<number> {
    const el = page.locator(selector).first();
    await el.scrollIntoViewIfNeeded();
    const box = (await el.boundingBox())!;
    const pad = 4;
    const h = Math.min(box.height, 60);
    const shot = await page.screenshot({
        clip: { x: box.x - pad, y: box.y - pad, width: pad + 16, height: h + pad * 2 },
        scale: 'css',
    });
    const { data, info } = await sharp(shot).raw().toBuffer({ resolveWithObject: true });
    const px = (x: number, y: number) => {
        const i = (y * info.width + x) * info.channels;
        return [data[i], data[i + 1], data[i + 2]];
    };
    const dist = (a: number[], b: number[]) =>
        Math.sqrt((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2);
    const ys = [0.2, 0.35, 0.5, 0.65, 0.8].map((f) => Math.round(pad + h * f));
    const mean = (x: number) => {
        const s = ys.map((y) => px(x, y));
        return [0, 1, 2].map((c) => s.reduce((t, p) => t + p[c], 0) / s.length);
    };
    const inside = mean(pad + 8);   // 활성 칩 배경
    const outside = mean(1);        // 카드(종이) 배경

    let worst = Infinity;
    for (const f of [0.25, 0.5, 0.75]) {
        const y = Math.round(pad + h * f);
        let best = 0;
        for (const dx of [0, 1, 2, 3]) {
            const p = px(pad + dx, y);
            best = Math.max(best, Math.min(dist(p, inside), dist(p, outside)));
        }
        worst = Math.min(worst, best);
    }
    return Math.round(worst);
}

test.describe('연필 프레임 안쪽 띠가 가려지지 않는다', () => {
    // ── 전제: 서버가 「지금 소스」를 서빙하는가 (T-187 가짜 RED 차단) ──────────────
    // 이 테스트가 먼저 실패하면 아래 결과는 전부 읽을 가치가 없다 — 옛 CSS를 잰 것이다.
    test('전제 — 서버가 서빙하는 app.css가 소스와 바이트 동일하다', async ({ request }) => {
        const src = readFileSync(CSS_SOURCE);
        const res = await request.get('/css/app.css');
        expect(res.status(), 'app.css를 못 받았다 — bootRun이 8080에 떠 있는가').toBe(200);
        const served = Buffer.from(await res.body());
        expect(
            sha12(served),
            '서버가 서빙하는 app.css가 소스와 다르다 — `build/resources/main` 복사본이 낡았다(T-187).\n'
            + `  소스 ${src.length}B ${sha12(src)} / 서빙 ${served.length}B ${sha12(served)}\n`
            + '  처방: ./gradlew processResources 를 돌린 뒤 이 스위트를 다시 실행한다.\n'
            + '  (해시 URL까지 갱신해야 하면 bootRun 재시작 — T-187 원인 ②)',
        ).toBe(sha12(src));
    });

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

        const r = await probe(page);
        const hits = r.findings.filter((f) => f.by === 'DIV');
        expect(hits.length, `프로브가 주입한 가림을 못 봤다 — 계측기가 죽었다. found=${JSON.stringify(r.findings)}`)
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

        const r = await probe(page);
        expect(r.findings.filter((f) => f.by === 'DIV')).toEqual([]);
    });

    // ── 실제 세 자리 ──────────────────────────────────────────────────────────────
    for (const theme of ['light', 'dark'] as const) {
        test(`① 편집기 툴바가 프레임을 덮지 않는다 (${theme})`, async ({ page }) => {
            await page.goto('/study');
            await page.waitForSelector('[data-testid="recall-body"]', { state: 'visible' });
            await setTheme(page, theme);

            // (1) 프레임이 존재한다 — 없으면 아래 단언은 검사 대상 0개짜리 공허한 초록이 된다.
            const src = await page.evaluate(
                () => getComputedStyle(document.querySelector('.study-editor')!).borderImageSource);
            expect(src).not.toBe('none');

            const r = await probe(page);
            // (2) 실제로 쟀다 — 「0건」과 「안 재봄」을 가른다.
            const ex = examinedFor(r, 'DIV.study-editor');
            expect(ex.length).toBe(1);
            expect(ex[0].sampled, `표본점을 거의 못 쟀다: ${JSON.stringify(ex[0])}`).toBeGreaterThanOrEqual(8);
            console.log(`[① ${theme}] examined=${JSON.stringify(ex)} skippedSmall=${r.skippedSmall.length} 전수=${JSON.stringify(r.findings)}`);

            // (3) 가려진 변이 없다.
            expect(on(r, 'study-editor')).toEqual([]);
        });

        test(`② 세그먼트 칩이 프레임을 덮지 않는다 (${theme})`, async ({ page }) => {
            await page.goto('/books');
            await page.waitForSelector('.shelf-filter-chips', { state: 'visible' });
            await setTheme(page, theme);

            // (1) 프레임이 존재한다 — 이 자리는 프레임이 ::after 오버레이 형태다.
            //     컨테이너 자신은 border-image를 갖지 않으므로 프로브의 검사 대상이 **아니다**(구조적 사각).
            const ov = await page.evaluate(() => {
                const el = document.querySelector('.shelf-filter-chips')!;
                const a = getComputedStyle(el, '::after');
                const own = getComputedStyle(el);
                return {
                    src: a.borderImageSource, pos: a.position, pe: a.pointerEvents,
                    inset: [a.top, a.right, a.bottom, a.left],
                    ownPos: own.position, ownSrc: own.borderImageSource,
                };
            });
            expect(ov.src, '오버레이가 프레임을 안 들고 있다 — 선 자체가 사라졌다').not.toBe('none');
            expect(ov.inset, '오버레이 박스가 컨테이너 border-box와 어긋나면 선 위치가 달라진다').toEqual(['-1px', '-1px', '-1px', '-1px']);
            expect(ov.pos).toBe('absolute');
            expect(ov.pe, 'pointer-events가 살아 있으면 칩 클릭을 삼킨다').toBe('none');
            expect(ov.ownPos, '컨테이너가 position:relative가 아니면 오버레이 기준이 어긋난다').toBe('relative');

            // (2) 자식이 오버레이 위로 쌓이지 않는다.
            //     오버레이는 z-index auto인 positioned 요소(페인트 8단계, DOM 순서상 마지막)라
            //     **양수 z-index를 가진 자식(9단계)만이** 그 위로 올라간다 — 그 순간 선이 덮인다.
            const chips = await page.evaluate(() =>
                Array.from(document.querySelectorAll('.shelf-filter-chips .filter-chip')).map((c) => {
                    const s = getComputedStyle(c);
                    return { cls: c.className, z: s.zIndex, pos: s.position };
                }));
            expect(chips.length).toBeGreaterThan(0);
            for (const c of chips) {
                expect(
                    c.z === 'auto' || Number(c.z) <= 0,
                    `칩이 양수 z-index로 오버레이 위에 올라간다: ${JSON.stringify(c)}`,
                ).toBe(true);
            }

            // (3) 선이 화면 픽셀에 실제로 있다 — (1)(2)가 규칙의 모양을 볼 때 이것은 결과를 본다.
            //     첫 셀이 활성인 기본 상태에서 왼쪽 세로선이 바로 원래 결함이 났던 자리다.
            const stroke = await leftEdgeStrokeScore(page, '.shelf-filter-chips');
            expect(stroke, `왼쪽 변에 연필선이 안 보인다(색거리 ${stroke})`).toBeGreaterThan(20);

            const r = await probe(page);
            console.log(`[② ${theme}] overlay=${JSON.stringify(ov.inset)} strokeScore=${stroke} chips=${chips.length} 전수=${JSON.stringify(r.findings)}`);
            expect(on(r, 'shelf-filter-chips')).toEqual([]);
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

            // (1) 프레임이 존재한다.
            const src = await page.evaluate(
                () => getComputedStyle(document.querySelector('.book-detail-head .book-cover')!).borderImageSource);
            expect(src).not.toBe('none');

            const r = await probe(page);
            // (2) 실제로 쟀다 — <img>는 4변을 기하로 본다.
            const ex = examinedFor(r, 'IMG.book-cover');
            expect(ex.length).toBe(1);
            expect(ex[0].kind).toBe('img');
            expect(ex[0].sampled).toBe(4);
            console.log(`[③ ${theme}] examined=${JSON.stringify(ex)} skippedSmall=${r.skippedSmall.length} 전수=${JSON.stringify(r.findings)}`);

            // (3) 4변 모두 border+padding ≥ 4px.
            expect(on(r, 'IMG.book-cover')).toEqual([]);
        });
    }
});
