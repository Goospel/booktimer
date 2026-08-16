/*
 * 스토어 스크린샷 재촬영기 — 목 모드 앱을 열어 세로 5장 + 가로 배너 1장을 규격대로 찍는다.
 *
 * 실행:
 *   npm --prefix miniapp run dev:mock      # 먼저 목 서버를 띄운다(포트 5174)
 *   node miniapp/screenshots/shoot.mjs     # 이 폴더의 png들을 덮어쓴다
 *
 * 규격·촬영조건의 근거는 옆의 README.md다. 특히 375×618@2로 찍어 636×1048로 축소하는 이유
 * (636 폭으로 직접 렌더하면 CSS 폭이 폰이 아니게 되어 실제 앱과 다른 레이아웃이 나온다)와
 * 가로 배너가 세로 컷 합성물인 이유가 거기 있다.
 *
 * ⚠️ 이 파일이 저장소에 있는 이유: 2026-08-14 촬영 때는 스크래치패드에 두고 세션과 함께 날려서,
 * 2026-08-16 재촬영 때 처음부터 다시 짰다. 화면이 바뀔 때마다 반복될 비용이라 여기 박는다.
 *
 * 의존성은 `frontend/node_modules`에서 빌려 쓴다(playwright·sharp가 거기 있다) — 스크린샷은
 * 번들과 무관한 콘솔 자산이라 미니앱에 촬영용 의존성을 새로 달지 않는다.
 */
const NM = new URL('../../frontend/node_modules/', import.meta.url)
const { chromium } = (await import(new URL('playwright/index.js', NM))).default
const sharp = (await import(new URL('sharp/dist/index.cjs', NM))).default
const { readFile } = await import('node:fs/promises')

/** 목 서버 주소 — vite는 `::1`에만 바인딩하므로 `127.0.0.1`이 아니라 `localhost`여야 한다. */
const URL_APP = process.env.MINIAPP_MOCK_URL ?? 'http://localhost:5174'

/** 콘솔 실측 규격(README) — 크기가 안 맞으면 업로드 자체가 거부된다. */
const PORTRAIT = { w: 636, h: 1048 }
const LANDSCAPE = { w: 1504, h: 741 }

const OUT = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')

const browser = await chromium.launch()
const page = await browser.newPage({
    viewport: { width: 375, height: 618 }, // ×2 = 750×1236, 비율이 규격(0.6069)과 사실상 같다
    deviceScaleFactor: 2,
    colorScheme: 'light',
})

const settle = (ms = 700) => page.waitForTimeout(ms)

async function shot(name) {
    // 탭·버튼을 눌러 이동하므로 포커스 링이 그림에 남는다 — 촬영 직전마다 지운다.
    await page.evaluate(() => document.activeElement?.blur?.())
    await settle(400)
    const buf = await page.screenshot()
    await sharp(buf).resize(PORTRAIT.w, PORTRAIT.h).png().toFile(`${OUT}/${name}.png`)
    console.log(`${name}.png — ${PORTRAIT.w}x${PORTRAIT.h}`)
}

/** 탭바는 `title` 속성에 라벨을 그대로 싣는다(App.tsx `TABS`). */
const tab = async (label) => { await page.click(`button[role="tab"][title="${label}"]`); await settle() }

/** 문구로 버튼 찾아 누르기 — TDS emotion 클래스라 잡을 손잡이가 문구뿐이다. */
async function clickText(text) {
    await page.evaluate((t) => {
        const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes(t))
        if (!b) throw new Error(`버튼 없음: ${t}`) // 문구가 바뀌면 조용히 엉뚱한 그림이 나오지 않게 여기서 죽는다
        b.click()
    }, text)
    await settle()
}

await page.goto(URL_APP, { waitUntil: 'networkidle' })
await settle(1200)

// 01 홈 — 타이머 카드 + 표지 캐러셀 + 주 버튼.
// ⚠️ 최상단에서 찍으면 「프로필·설정」 줄이 한 칸을 먹어 주 CTA(「측정 시작」)가 탭바 뒤로 거의 다 숨는다.
await page.evaluate(() => window.scrollTo(0, 80))
await shot('01-home')

// 02 홈 아래 — 「소식」·「책 뉴스」 피드 박스. 폴드 아래라 끝까지 내려 찍는다.
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
await settle()
await shot('02-feed')

// 03 서재 — 상태 탭 + 표지 캐러셀
await tab('서재')
await page.evaluate(() => window.scrollTo(0, 0))
await shot('03-library')

// 04 기록 — 통계 + 잔디 + 날짜별 기록
await tab('기록')
await page.evaluate(() => window.scrollTo(0, 0))
await shot('04-history')

// 05 목표 — 시/분 휠 피커. 진입은 홈 「남은시간 ⓘ」 상자 → 「하루 목표 바꾸기」(전면광고는 목이 즉시 resolve).
await tab('홈')
await page.evaluate(() => window.scrollTo(0, 0))
await clickText('남은시간')
await clickText('하루 목표 바꾸기')
await settle(1200)
await shot('05-goal')

// ── 가로 배너 — 앱에 가로 화면이 없어 세로 컷 3장을 얹은 합성물이다 ──────────────
//
// sharp+SVG가 아니라 브라우저로 조판한다: 한글을 확실히 태우려면 그게 유일하게 안전하다
// (librsvg 경로는 머신의 fontconfig에 기대고, 깨져도 조용히 네모로 나온다).
const uri = async (name) =>
    `data:image/png;base64,${(await readFile(`${OUT}/${name}.png`)).toString('base64')}`
const [home, feed, history] = await Promise.all(['01-home', '02-feed', '04-history'].map(uri))

const banner = `
<!doctype html><meta charset="utf-8">
<style>
  @import url('https://fonts.googleapis.com/css2?family=Gowun+Dodum&display=swap');
  * { margin: 0; box-sizing: border-box; }
  body {
    width: ${LANDSCAPE.w}px; height: ${LANDSCAPE.h}px; overflow: hidden;
    background: #F3EEE4; color: #2C2A24;
    font-family: 'Gowun Dodum', 'Malgun Gothic', sans-serif;
    display: flex; align-items: center;
  }
  .copy { flex: 0 0 auto; padding-left: 80px; width: 480px; }
  .copy h1 { font-size: 58px; font-weight: 700; line-height: 1.28; letter-spacing: -1px; }
  .copy p  { margin-top: 26px; font-size: 25px; line-height: 1.6; color: #6F6A5E; }
  .phones { position: relative; flex: 1; height: 100%; }
  .phone {
    position: absolute; width: 300px; border-radius: 24px; overflow: hidden;
    border: 1px solid #E4DDD0; background: #FCFAF5;
    box-shadow: 0 18px 44px rgba(44, 42, 36, 0.16);
  }
  .phone img { display: block; width: 100%; }
  /* 계단으로 어긋나게 + 캔버스 아래로 흘려 보낸다 — 잘린 변이 "아래에서 올라온다"로 읽힌다(README). */
  .p1 { left: 4px;   top: 296px; }
  .p2 { left: 318px; top: 340px; }
  .p3 { left: 632px; top: 314px; }
</style>
<div class="copy">
  <h1>읽은 시간이<br>쌓이는 재미</h1>
  <p>타이머로 독서를 기록하고,<br>잔디와 소식으로 이어 갑니다.</p>
</div>
<div class="phones">
  <div class="phone p1"><img src="${home}"></div>
  <div class="phone p2"><img src="${feed}"></div>
  <div class="phone p3"><img src="${history}"></div>
</div>
`

const wide = await browser.newPage({
    viewport: { width: LANDSCAPE.w, height: LANDSCAPE.h },
    deviceScaleFactor: 1,
})
await wide.setContent(banner, { waitUntil: 'networkidle' })
await wide.waitForTimeout(1200) // 웹폰트가 실제로 그려질 때까지
await wide.screenshot({ path: `${OUT}/10-landscape.png` })
console.log(`10-landscape.png — ${LANDSCAPE.w}x${LANDSCAPE.h}`)

await browser.close()
