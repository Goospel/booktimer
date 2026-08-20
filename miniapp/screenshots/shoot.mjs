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

/**
 * 탭바는 `title` 속성에 라벨을 그대로 싣는다(App.tsx `TABS`).
 *
 * ⚠️ `role="tab"`으로 잡지 않는다 — 탭바가 5칸이 되며 ARIA tabs 패턴을 버리고 `aria-current="page"`로
 * 갔다(그 짝인 `tabpanel`이 이 앱에 없어 종전 마크업이 규약 위반이었다). 옛 셀렉터는 여기서 죽는다.
 */
const tab = async (label) => { await page.click(`button[title="${label}"]`); await settle() }

/** 문구로 버튼 찾아 누르기 — TDS emotion 클래스라 잡을 손잡이가 문구뿐이다. */
async function clickText(text) {
    await page.evaluate((t) => {
        const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes(t))
        if (!b) throw new Error(`버튼 없음: ${t}`) // 문구가 바뀌면 조용히 엉뚱한 그림이 나오지 않게 여기서 죽는다
        b.click()
    }, text)
    await settle()
}

/**
 * `--banner`면 앱 컷은 건너뛰고 가로 배너만 다시 조판한다 — 배너는 **디스크의 세로 png를 얹은
 * 합성물**이라 목 서버가 필요 없다. 문구만 손볼 때 세로 5장을 굳이 다시 찍으면, 목 픽스처의
 * 상대 시간(「3시간 전」)이 흘러 **바뀐 게 없는 컷까지 diff가 뜬다**.
 */
const BANNER_ONLY = process.argv.includes('--banner')

if (!BANNER_ONLY) {

/*
 * ⚠️ 첫 사용 안내(코치마크)를 미리 껐다고 못 박는다 — 갓 띄운 브라우저엔 기기 기록이 없어 안내가
 * 곧바로 뜨고, 그 안내는 **스스로 탭을 옮기며** 다섯 걸음을 걷는다. 안 끄면 전 컷에 딤과 말풍선이
 * 깔리고 촬영 순서까지 어긋난다. 키를 심는 것이 곧 「이미 다 본 사용자」다(`coachmark.tsx`).
 */
await page.addInitScript(() => {
    for (const name of ['timer', 'library', 'add-book', 'bookshop', 'margin']) {
        localStorage.setItem(`booktimer.coachmark.${name}`, 'seen')
    }
})

await page.goto(URL_APP, { waitUntil: 'networkidle' })
await settle(1200)

// 01 홈 — 인사말·아바타 헤더 + 타이머 카드 + 표지 캐러셀.
// 최상단에서 찍는다: 주 CTA가 홈 하단 풀폭 버튼에서 **탭바 가운데 원**으로 옮겨가(항상 화면에 있다)
// 스크롤로 지켜 줄 것이 없어졌고, 대신 맨 위 헤더가 이 앱에서 내가 누구인지 말하는 자리가 되었다.
await page.evaluate(() => window.scrollTo(0, 0))
await shot('01-home')

// 02 홈 아래 — 「소식」·「책 뉴스」 피드 박스. 폴드 아래라 끝까지 내려 찍는다.
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
await settle()
await shot('02-feed')

// 03 서재 — 상태 탭 + 표지 캐러셀 + 인라인 여백 박스
// ⚠️ 최상단에서 찍지 않는다 — 여백 박스가 생기며 세로가 길어져, 0에서 찍으면 첫 글 카드가 문장
// 중간에 탭바로 잘린다(잘린 문장은 심사용 그림이 아니다).
// ⚠️ 눈으로 고른 매직넘버(옛 `60`)를 버리고 **상태 탭 줄을 기준으로 잡는다** — 그 위 「펼쳐보기」
// 알약의 위치가 바뀌자 60이 그 알약을 반토막 낸 채 화면 맨 위에 남겼다(실측). 기준선을 두면
// 위쪽 레이아웃이 또 바뀌어도 컷의 첫 줄은 늘 상태 탭이다.
await tab('서재')
await page.evaluate(() => {
    const label = [...document.querySelectorAll('*')].find(
        (e) => e.children.length === 0 && (e.textContent ?? '').trim().startsWith('읽는 중'),
    )
    if (!label) throw new Error('서재 상태 탭을 못 찾았다') // 문구가 바뀌면 엉뚱한 그림 대신 여기서 죽는다
    window.scrollTo(0, label.closest('div').getBoundingClientRect().top + window.scrollY - 12)
})
await settle()
await shot('03-library')

// 04 기록 — 잔디 + 날짜별 기록(하루에 읽은 책 더미)
// ⚠️ 최상단에서 찍지 않는다 — 0에서 찍으면 연속·통계 카드가 위쪽을 다 먹고 이 화면의 새 얼굴인
// **표지 더미**가 탭바 아래로 밀려 한 줄도 안 보인다(실측). 잔디 제목을 기준선으로 잡으면 잔디와
// 날짜 줄이 한 컷에 같이 들어온다 — 위쪽 카드가 또 바뀌어도 컷의 첫 줄은 늘 잔디다.
await tab('기록')
await page.evaluate(() => {
    const label = [...document.querySelectorAll('*')].find(
        (e) => e.children.length === 0 && (e.textContent ?? '').trim() === '읽은 날짜',
    )
    if (!label) throw new Error('기록 잔디 제목을 못 찾았다') // 문구가 바뀌면 엉뚱한 그림 대신 여기서 죽는다
    window.scrollTo(0, label.getBoundingClientRect().top + window.scrollY - 12)
})
// 여러 권 읽은 첫 날을 펼쳐 둔다 — 더미만 접힌 채로는 「표지가 겹쳐 있다」까지만 보이고 이 기능의
// 요점인 **책별로 얼마나 읽었나**가 그림에 없다. 펼침이 위쪽 레이아웃은 안 건드리므로 기준선은
// 그대로 유효하다(아래 줄만 밀린다).
await page.evaluate(() => {
    const first = document.querySelector('button[data-day-toggle]')
    if (!first) throw new Error('펼칠 수 있는 날이 없다') // 목 픽스처가 한 권짜리만 남으면 여기서 죽는다
    first.click()
})
await settle()
await shot('04-history')

// 05 목표 — 시/분 휠 피커. 진입은 홈 「남은시간 ⓘ」 상자 → 「하루 목표 바꾸기」(전면광고는 목이 즉시 resolve).
await tab('홈')
await page.evaluate(() => window.scrollTo(0, 0))
await clickText('남은시간')
await clickText('하루 목표 바꾸기')
await settle(1200)
await shot('05-goal')

} // if (!BANNER_ONLY)

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
  @import url('https://fonts.googleapis.com/css2?family=Gaegu:wght@400;700&display=swap');
  * { margin: 0; box-sizing: border-box; }
  body {
    width: ${LANDSCAPE.w}px; height: ${LANDSCAPE.h}px; overflow: hidden;
    /*
     * ⚠️ 종이색·서체는 앱을 따라간다 — 배너는 앱 컷 3장을 얹은 합성물이라 여기만 옛 값이면
     * 폰 안팎이 다른 톤·다른 서체로 갈린다. 색은 「거친 종이」 테마의 최종 종이색(#F7F2E8,
     * T-193에서 #F3EEE4를 밝힌 값)이고, 서체는 앱 본문과 같은 Gaegu다.
     */
    background: #F7F2E8; color: #2C2A24;
    font-family: 'Gaegu', 'Malgun Gothic', sans-serif;
    display: flex; align-items: center;
  }
  /*
   * Gaegu는 x-height가 작아 같은 px에서 눈에 띄게 작고 가늘다(앱 global.css의 112.5%·700 보정과
   * 같은 이유) — 제목은 58 → 66으로 올리고 본문은 굵기만 700으로 세운다. ⚠️ 본문을 25px 위로
   * 올리면 자폭이 넓어 480px 안에서 되접히고, 되접힌 줄이 폰 그림 위로 넘어간다(실측).
   */
  .copy { flex: 0 0 auto; padding-left: 80px; width: 480px; }
  .copy h1 { font-size: 66px; font-weight: 700; line-height: 1.26; letter-spacing: -1px; }
  .copy p  { margin-top: 24px; font-size: 25px; font-weight: 700; line-height: 1.6; color: #57534A; }
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
<!--
  ⚠️ 큰 문구(습관)는 그대로 두고 **서브에서 잔디만 소셜로 바꿨다**(사용자 결정 2026-08-17).
  이 앱이 파는 것은 여전히 「꾸준히 읽는 습관」이고, 잔디는 그 습관을 혼자 확인하는 장치라
  두 번째 줄에서 자리를 아꼈다 — 그 자리를 친구의 책방·소식이 받는다.
-->
<div class="copy">
  <h1>읽은 시간이<br>쌓이는 재미</h1>
  <p>타이머로 독서를 기록하고,<br>친구의 책방과 소식으로 이어 갑니다.</p>
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
