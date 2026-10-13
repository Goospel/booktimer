/*
 * 스토어 스크린샷 재촬영기 — 목 모드 앱을 열어 세로 8장 + 가로 배너 1장을 규격대로 찍는다.
 *
 * 실행:
 *   npm --prefix miniapp run dev:mock      # 먼저 목 서버를 띄운다
 *   node miniapp/screenshots/shoot.mjs     # 이 폴더의 png들을 덮어쓴다
 *
 * ⚠️ 포트의 단일 출처는 체크인된 `miniapp/vite.config.ts`의 `server.port`(5300)다 — 아래 기본값은 그
 * 사본이니 한쪽만 고치지 않는다. `.claude/launch.json`은 gitignore 대상이라 **없는 워크트리도 있다**
 * (있으면 같은 값이어야 한다). 다른 포트로 띄웠으면 `MINIAPP_MOCK_URL`로 넘긴다.
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
const URL_APP = process.env.MINIAPP_MOCK_URL ?? 'http://localhost:5300'

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

/**
 * 글꼴이 실렸다는 신호 — 에디토리얼 설계 §5-2. 앱은 글꼴 CSS를 **load 뒤에** 붙이므로(`fonts.ts`) 그냥 찍으면
 * 대체 글꼴로 찍힐 수 있다. 명조 500 face 하나가 `loaded`가 될 때까지 기다리고, 15초를 넘으면 **죽는다** —
 * 대체 글꼴 컷을 조용히 내지 않는다.
 *
 * ⚠️ `document.fonts.check()`는 쓰지 않는다 — 링크가 없어도·없는 패밀리 이름이어도 참이다(스파이크 실측).
 * ⚠️ 이 PC엔 Noto가 시스템에 깔려 있어 링크가 빠져도 그림은 Noto로 나온다 — 그림이 아니라 이 신호로만 판정한다.
 * 음성 대조: `--block-fonts`(구글 폰트 요청을 막는다)로 돌리면 여기서 타임아웃으로 죽어야 정상이다(U-15).
 */
async function waitFonts(p, label) {
    const t0 = Date.now()
    await p.waitForFunction(
        () => [...document.fonts].some(
            (f) => f.family.replace(/["']/g, '') === 'Noto Serif KR' && f.weight === '500' && f.status === 'loaded',
        ),
        null,
        { timeout: 15000 },
    )
    await p.evaluate(() => document.fonts.ready) // 신호 뒤 남은 조각까지
    console.log(`글꼴 신호 통과(${label}) — ${Date.now() - t0}ms`)
}

async function shot(name) {
    // 탭·버튼을 눌러 이동하므로 포커스 링이 그림에 남는다 — 촬영 직전마다 지운다.
    await page.evaluate(() => document.activeElement?.blur?.())
    await settle(400)
    // 탭을 옮기면 그 화면 글자가 새 unicode-range 조각을 부를 수 있다 — 진입 때의 waitFonts만으론 컷마다 보장이 안 된다.
    await page.evaluate(() => document.fonts.ready)
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
 * 합성물**이라 목 서버가 필요 없다. 문구만 손볼 때 세로 8장을 굳이 다시 찍으면, 목 픽스처의
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

if (process.argv.includes('--block-fonts')) await page.route(/fonts\.googleapis\.com/, (r) => r.abort())

await page.goto(URL_APP, { waitUntil: 'networkidle' })
await waitFonts(page, '독서 모드')
await settle(1200)

// 01 홈 — 인사말·아바타 헤더 + 아치(독서|공부 · 시계 · 게이지) + 오늘 표. 그 아래 고르기 선반은 탭바 뒤다.
// 최상단에서 찍는다: 주 CTA가 홈 하단 풀폭 버튼에서 **탭바 가운데 원**으로 옮겨가(항상 화면에 있다)
// 스크롤로 지켜 줄 것이 없어졌고, 대신 맨 위 헤더가 이 앱에서 내가 누구인지 말하는 자리가 되었다.
await page.evaluate(() => window.scrollTo(0, 0))
await shot('01-home')

// 02 홈 아래 — 「소식」·「여백」·「책 뉴스」 피드 박스.
// 끝까지 내려 찍지 않는다 — Soft 재테마로 박스가 커져 맨 아래에서 찍으면 **탭 줄이 반쯤 잘렸다**(2026-09-23
// 리뷰: 02와 그걸 얹은 10 가운데 폰). 03·06처럼 기준선을 둔다: 탭 줄 윗선을 화면 16px 아래에 세우고, 박스 바닥이
// 탭바 위에 있는지 확인한다(가리면 여기서 죽는다 — 조용히 잘린 그림을 내지 않는다).
await page.evaluate(() => {
    const label = (b) => (b.textContent ?? '').trim()
    const tabs = ['소식', '여백', '책 뉴스'].map((t) => [...document.querySelectorAll('button')].find((b) => label(b) === t))
    if (tabs.some((b) => !b)) throw new Error('피드 탭 줄을 못 찾았다') // 문구가 바뀌면 엉뚱한 그림 대신 여기서 죽는다
    const row = tabs[0].parentElement
    const box = row.closest('section')
    const nav = document.querySelector('nav[aria-label="메인 탭"]')
    if (!box || !nav) throw new Error('피드 박스 또는 탭바를 못 찾았다')
    window.scrollTo(0, 0)
    window.scrollTo(0, row.getBoundingClientRect().top - 16)
    if (row.getBoundingClientRect().top < 0) throw new Error('피드 탭 줄이 화면 위로 잘린다')
    if (box.getBoundingClientRect().bottom > nav.getBoundingClientRect().top) {
        throw new Error('피드 박스 바닥이 탭바에 가린다')
    }
})
await settle()
await shot('02-feed')

// 03 서재 — 「내 서재」 아치(판화) + 상태 선반 1·2단 + 고른 표지에 걸친 「여백에 글쓰기」 검정 알약.
// 에디토리얼 PR-3으로 상태 탭이 선반 세 단(+ 그 아래 펼친 책)이 됐다. 375×618 창엔 셋째 선반·펼친 책까지
// 안 든다(실측: 아치 24~186 · 선반 214~384 · 396~566 · 578~748 · 펼친 책 776~ · 탭바 540) — 이 컷이 파는
// 것은 「선반에 꽂힌 서재」와 걸친 알약이라 둘째 선반 표지 바닥까지를 담는다.
// 기준선: 아치 꼭대기를 화면 12px 아래에 세운다(0에서 찍으면 둘째 선반 바닥이 탭바 윗선에 걸린다 — 실측 544 vs 540).
// 그러고도 알약·둘째 선반 표지가 탭바에 가리면 죽는다(조용히 잘린 그림을 내지 않는다).
await tab('서재')
const libLayout = await page.evaluate(() => {
    const arch = document.querySelector('[data-arch]')
    const shelves = document.querySelectorAll('[data-shelf]')
    const nav = document.querySelector('nav[aria-label="메인 탭"]')
    const pill = [...document.querySelectorAll('button')].find((b) => (b.textContent ?? '').trim() === '여백에 글쓰기')
    if (!arch || shelves.length < 2 || !nav) throw new Error('서재 아치·선반·탭바 중 하나를 못 찾았다')
    if (!pill) throw new Error('걸친 「여백에 글쓰기」 알약이 없다 — 고른 책이 없다') // 엉뚱한 그림 대신 죽는다
    window.scrollTo(0, 0)
    window.scrollTo(0, arch.getBoundingClientRect().top - 12)
    const r = (el) => el.getBoundingClientRect()
    const covers = [...shelves[1].querySelectorAll('[data-shelf-item]')]
    const shelf2Bottom = Math.max(...covers.map((c) => r(c).bottom))
    if (r(arch).top < 0) throw new Error('아치 꼭대기가 화면 위로 잘린다')
    if (r(pill).bottom > r(nav).top - 16) throw new Error('「여백에 글쓰기」 알약이 탭바에 가린다')
    if (!covers.length || shelf2Bottom > r(nav).top - 4) throw new Error('둘째 선반 표지가 탭바에 가린다')
    return {
        scrollY: Math.round(window.scrollY), archTop: Math.round(r(arch).top), pillBottom: Math.round(r(pill).bottom),
        shelf2CoverBottom: Math.round(shelf2Bottom), navTop: Math.round(r(nav).top),
    }
})
console.log('03 구도', JSON.stringify(libLayout))
await settle()
await shot('03-library')

// 04 기록 — 잔디 + 펼친 하루(R2: 측정 한 건씩 「시각/길이 · 책 · [바꾸기]/[책 붙이기]」 + 「그날 목표」).
// 이 컷이 파는 것 = 잔디, 그리고 측정이 한 건씩 보이고 책 없는 측정에 [책 붙이기]로 나중에 책을 붙일 수 있다는 것.
// 둘 다 담는다(사용자 결정 2026-09-25 「잔디도 살리기」). 날을 차례로 펼쳐 **측정 줄 ≥ 2 · [책 붙이기] ≥ 1이면서
// 잔디 제목부터 그 날 바닥(「그날 목표」)까지가 탭바 16px 위에 드는 첫 날**을 고른다. 그런 날이 없으면 죽는다 —
// 잔디를 내주는 구도로 조용히 물러서지 않는다(목 픽스처가 오늘을 두 줄로 두는 이유다, dev-mock `readingSessionsAt`).
await tab('기록')
const days = page.locator('button[data-day-toggle]')
let picked = -1
for (let i = 0, n = await days.count(); i < n && picked < 0; i++) {
    await days.nth(i).click()
    await settle(250)
    const fits = await days.nth(i).evaluate((t) => {
        const box = t.parentElement
        const attach = [...box.querySelectorAll('button')].filter((b) => (b.textContent ?? '').trim() === '책 붙이기')
        if (box.querySelectorAll('[data-session-row]').length < 2 || attach.length < 1) return false
        const label = [...document.querySelectorAll('*')].find(
            (e) => e.children.length === 0 && (e.textContent ?? '').trim() === '읽은 날짜',
        )
        const nav = document.querySelector('nav[aria-label="메인 탭"]')
        if (!label || !nav) throw new Error('잔디 제목·탭바 중 하나를 못 찾았다') // 문구가 바뀌면 엉뚱한 그림 대신 죽는다
        const span = box.getBoundingClientRect().bottom - (label.getBoundingClientRect().top - 12)
        return span <= nav.getBoundingClientRect().top - 16
    })
    if (fits) picked = i
    else { await days.nth(i).click(); await settle(250) } // 한 번에 하나만 펼쳐지지만, 닫고 넘어가야 다음 날 좌표가 안 흔들린다
}
if (picked < 0) throw new Error('잔디와 한 컷에 드는 「측정 여러 건 + [책 붙이기]」 날이 없다')
const layout = await days.nth(picked).evaluate((t) => {
    const label = [...document.querySelectorAll('*')].find(
        (e) => e.children.length === 0 && (e.textContent ?? '').trim() === '읽은 날짜',
    )
    const nav = document.querySelector('nav[aria-label="메인 탭"]')
    const box = t.parentElement
    window.scrollTo(0, label.getBoundingClientRect().top + window.scrollY - 12)
    const r = (el) => el.getBoundingClientRect()
    if (r(box).bottom > r(nav).top - 16) throw new Error('펼친 날이 탭바에 가린다') // 스크롤이 끝까지 못 가면 여기서 잡힌다
    return {
        date: t.innerText.split('\n').slice(0, 2).join(' '),
        rows: [...box.querySelectorAll('[data-session-row]')].map((x) => x.innerText.replace(/\n/g, ' | ')),
        grassTitleTop: Math.round(r(label).top), dayTop: Math.round(r(t).top),
        dayBottom: Math.round(r(box).bottom), navTop: Math.round(r(nav).top),
    }
})
console.log('04 구도', JSON.stringify(layout)) // README 실측 좌표의 출처
await settle()
await shot('04-history')

// 05 목표 — 시/분 휠 피커.
// ⚠️ 진입이 **한 번 눌러 들어가는 알약**으로 바뀌었다(#936). 전에는 홈의 대시 밑줄 「남은시간 ⓘ」
// 한 줄을 누른 뒤 「하루 목표 바꾸기」를 또 눌렀는데, 그 줄이 2열 스탯 행 + 「변경 ›」 알약으로
// 갈리면서 두 단계가 한 단계가 됐다(그리고 라벨도 「남은 시간」으로 띄어쓰기가 생겼다).
// 목표가 0이면 알약 문구가 「정하기 ›」라 둘 다 받는다 — 목 픽스처는 목표 30분이라 「변경 ›」이다.
// → 2026-09-24: 2열이 「남은 시간 타일 + 목표 캡션 줄」로 바뀌며 알약이 캡션 줄의 글자 「바꾸기 ›」가 됐다.
//   캡션 줄 표식(`data-goal-caption`)으로 잡고, 목표 0 경로(카드 안 「목표 정하기」)는 문구로 받는다.
await tab('홈')
await page.evaluate(() => window.scrollTo(0, 0))
await page.evaluate(() => {
    const b =
        document.querySelector('[data-goal-caption] button') ??
        [...document.querySelectorAll('button')].find((x) => /정하기/.test(x.textContent ?? ''))
    if (!b) throw new Error('목표 진입 알약을 못 찾았다') // 문구가 바뀌면 엉뚱한 그림 대신 여기서 죽는다
    b.click()
})
await settle(1200)
await shot('05-goal')

/*
 * ── 공부 모드 3컷 ─────────────────────────────────────────────────────────────
 *
 * 모드는 홈의 토글이 `localStorage`에 적고(`App.tsx` MODE_KEY), 앱은 그 값을 **마운트 1회**만 읽는다
 * (`useState(() => readMode())`). 그래서 토글을 클릭하는 대신 키를 심고 다시 연다 — 클릭 경로는
 * 홈 스크롤 위치·토글 좌표에 기대지만 키는 그런 게 없다. 다시 열지 않으면 심어도 안 먹는다.
 */
await page.addInitScript(() => localStorage.setItem('booktimer.timerMode', 'study'))
await page.goto(URL_APP, { waitUntil: 'networkidle' })
await waitFonts(page, '공부 모드')
await settle(1200)

// 06 공부 홈 — 공부 모드 화면(`body.study-mode`)의 「무엇을 공부할까요?」 파랑 체크 표지 선반 + 「회당 50분 · 바꾸기」.
// ⚠️ 맨 위에서 찍으면 그 손잡이가 탭바 뒤에 깔린다. 그래서 아치(토글·시계)를 통째로 화면 밖으로 넘겨 손잡이까지
// 담는다(아래 에디토리얼 주석) — 이 컷이 파는 것은 인사말·시계가 아니라 책별 회당 시간이다.
// 손잡이·아치를 못 찾으면 옛 그림 대신 여기서 죽는다(문구·표식이 바뀌었거나 픽스처 책에 회당 시간이 없다).
await page.evaluate(() => {
    const handle = [...document.querySelectorAll('span')].find((x) => /^회당 .+ · 바꾸기$/.test(x.textContent?.trim() ?? ''))
    if (!handle) throw new Error('회당 시간 손잡이를 못 찾았다')
    const card = document.querySelector('.lamp-page')
    if (!card) throw new Error('타이머 카드(.lamp-page)를 못 찾았다')
    const nav = document.querySelector('nav[aria-label="메인 탭"]')
    if (!nav) throw new Error('탭바를 못 찾았다')
    window.scrollTo(0, 0)
    // 카드를 위에 붙이되, 그러면 손잡이가 탭바에 깔릴 때는 **손잡이 쪽을 지킨다**(탭바 16px 위에 세운다).
    // Soft 재테마(2026-09-23)로 탭바가 64로 커지고 카드들이 부풀어 카드 기준만으론 손잡이가 탭바 뒤로
    // 들어갔다(실측 375×618: 카드 위 104 → 손잡이 아래 703, 탭바 542) — 둘 다 담을 수 없으면 이 컷의 주제가 이긴다.
    const cardTop = card.getBoundingClientRect().top
    const handleFit = handle.getBoundingClientRect().bottom - (nav.getBoundingClientRect().top - 16)
    // 에디토리얼(2026-10-13): 카드가 아치가 되며 시계가 커져(시계 196~284 · 손잡이 바닥 738 — 542px) 탭바 위
    // 524px에 둘을 함께 못 담는다. 손잡이 기준으로만 내리면 **시계 숫자가 화면 윗변에 반토막 난다**(실측 — 잘린
    // 글자는 심사용 그림이 아니다). 그래서 아치를 통째로 화면 밖으로 넘긴다: 아치 바닥 선 4px 아래가 화면 윗변.
    // 공부 모드는 파랑 체크·손잡이 테·탭바 원·「일정」 탭이 말한다.
    const arch = card.matches('[data-arch]') ? card : card.querySelector('[data-arch]')
    if (!arch) throw new Error('공부 홈 아치([data-arch])를 못 찾았다') // 대체값으로 물러서면 시계 반토막 컷이 조용히 돌아온다
    const archBottom = arch.getBoundingClientRect().bottom + 4
    window.scrollTo(0, Math.max(cardTop - 16, handleFit, archBottom))
    if (handle.getBoundingClientRect().bottom > nav.getBoundingClientRect().top - 16) {
        throw new Error('회당 시간 손잡이가 탭바에 가린다') // 조용히 가린 그림을 내지 않는다
    }
    if (arch.getBoundingClientRect().bottom > 0) throw new Error('아치(시계)가 화면 윗변에 잘린다')
    return { scrollY: Math.round(window.scrollY), handleBottom: Math.round(handle.getBoundingClientRect().bottom), navTop: Math.round(nav.getBoundingClientRect().top) }
}).then((v) => console.log('06 구도', JSON.stringify(v)))
await settle()
await shot('06-study-home')

// 07 공부 서재 — 「N독」 칩 + 「회독 +1」 채움 버튼.
// 기본 선택이 첫 책이라(`resolveSelected`) 「회독 +1」·「관리」 줄이 이미 서 있다 — 고를 것이 없다.
await tab('서재')
await shot('07-study-library')

// 08 공부 일정 — 월 달력(지킴 원 · 못지킴 테두리 · 측정 점).
// 탭 이름이 「책방」이 아니라 「일정」인 것 자체가 공부 모드 탭바(`STUDY_TABS`)의 증거다.
// ⚠️ 목 픽스처는 표식을 **최근 4일**에 놓는다 — 달 초에 찍으면 그것들이 지난달에 있어 이번 달 격자가
// 통째로 빈다(2026-09-01 실측: 9월이 백지였다). 빈 달력은 이 화면이 뭘 하는지 말하지 못하므로,
// 표식이 3개 미만이면 지난달로 한 칸 물러선다 — 셋은 원·테두리·점을 다 보일 최소치다(09-02 실측: 9월에
// 점 하나뿐이라 0개 기준으론 안 물러섰고 백지와 같은 그림이 됐다). 달 중순엔 이 분기가 안 타고 이번 달이 찍힌다.
await tab('일정')
const MARKS = '[data-cal-state="kept"],[data-cal-state="missed"],[data-cal-dot]'
if ((await page.locator(MARKS).count()) < 3) {
    await page.click('button[aria-label="지난달 보기"]')
    await settle()
}
await shot('08-study-calendar')

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
  @import url('https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@500&family=Noto+Sans+KR:wght@400;500&display=swap');
  * { margin: 0; box-sizing: border-box; }
  body {
    width: ${LANDSCAPE.w}px; height: ${LANDSCAPE.h}px; overflow: hidden;
    /*
     * ⚠️ 바탕색은 앱을 따라간다 — 배너는 앱 컷 3장을 얹은 합성물이라 여기만 옛 값이면 폰 안팎이
     * 다른 톤으로 갈린다. 에디토리얼(2026-10-13)의 종이 #F5F0E6 · 잉크 #1D1B18 · 본문 #4A453E
     * (global.css html:root와 같은 값 — 한쪽만 고치지 않는다).
     *
     * 서체도 앱을 따라간다 — 제목은 Noto Serif KR 500(앱의 화면 제목·값 축), 본문은 Noto Sans KR 400.
     * 앱이 싣는 face도 이 셋뿐이고 font-synthesis: none이라 굵기는 400·500만 쓴다(fonts.ts와 같은 URL).
     * (이 CSS는 JS 템플릿 문자열 안이다 — 주석에 백틱을 쓰면 문자열이 끊긴다.)
     */
    background: #F5F0E6; color: #1D1B18;
    font-family: 'Noto Sans KR', 'Malgun Gothic', sans-serif;
    font-synthesis: none;
    display: flex; align-items: center;
  }
  /*
   * 크기는 480px 칸(왼쪽 여백 80 포함) 안에서 줄이 되접히지 않는 값이다 — 되접힌 줄은 폰 그림 위로 넘어간다.
   * 아래 조판 뒤 줄 수를 재서 되접히면 죽는다.
   */
  .copy { flex: 0 0 auto; padding-left: 80px; width: 480px; }
  .copy h1 { font-family: 'Noto Serif KR', serif; font-size: 58px; font-weight: 500; line-height: 1.3; letter-spacing: -1px; }
  .copy p  { margin-top: 24px; font-size: 21px; font-weight: 400; line-height: 1.7; color: #4A453E; }
  .phones { position: relative; flex: 1; height: 100%; }
  /* 에디토리얼은 그림자·면이 없다(이펙트 0) — 폰 테두리도 앱의 탭바처럼 잉크 1px 선 하나로 선다. */
  .phone {
    position: absolute; width: 300px; border-radius: 24px; overflow: hidden;
    border: 1px solid #1D1B18; background: #F5F0E6;
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
if (process.argv.includes('--block-fonts')) await wide.route(/fonts\.googleapis\.com/, (r) => r.abort())
await wide.setContent(banner, { waitUntil: 'networkidle' })
await waitFonts(wide, '배너')
const wraps = await wide.evaluate(() =>
    [...document.querySelectorAll('.copy h1, .copy p')]
        .filter((e) => Math.round(e.getBoundingClientRect().height / parseFloat(getComputedStyle(e).lineHeight)) !== 2)
        .map((e) => e.textContent),
)
if (wraps.length) throw new Error(`배너 카피가 되접힌다: ${wraps.join(' / ')}`) // 두 줄 카피가 셋째 줄로 넘치면 폰 위로 번진다
await wide.screenshot({ path: `${OUT}/10-landscape.png` })
console.log(`10-landscape.png — ${LANDSCAPE.w}x${LANDSCAPE.h}`)

await browser.close()
