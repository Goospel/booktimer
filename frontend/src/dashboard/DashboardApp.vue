<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { DashboardResponse, TimerState, StopResponse, BookOption, StudyState, GraphDto } from './types'
import { IDLE_STUDY, studyStateOf } from './types'
import { getCsrfToken } from '../shared/follow'
import type { TimerMode } from './timerMode'
import { shouldRefresh, readMode, writeMode, effectiveMode } from './timerMode'
import TimerCard from './TimerCard.vue'
import StudyTimerCard from './StudyTimerCard.vue'
import ModeToggle from './ModeToggle.vue'
import BookPickSheet from './BookPickSheet.vue'
import StudyBookSheet from './StudyBookSheet.vue'
import ContributionGraph from './ContributionGraph.vue'
import GardenPanel from './GardenPanel.vue'
import BrandQuote from './BrandQuote.vue'
import EmailVerifyBanner from './EmailVerifyBanner.vue'
import WelcomeBanner from './WelcomeBanner.vue'
import QuickNav from './QuickNav.vue'
import DashHeader from './DashHeader.vue'

// justOnboarded: 온보딩 직후 셸 data 속성 → main.ts가 읽어 주입. 1회 환영 배너 트리거(§6.4).
const props = defineProps<{ justOnboarded?: boolean }>()
const showWelcome = ref(props.justOnboarded === true)

const data = ref<DashboardResponse | null>(null)
const loading = ref(true)
const fetchError = ref(false)
const actionError = ref<string | null>(null)
// 서버 왕복 동안 버튼에 "진행 중"을 표시해 멈칫을 의도된 피드백으로 보이게 + 중복 클릭(409) 방지
const starting = ref(false)
const stopping = ref(false)
// 공부 목표 저장 왕복 — 히어로 편집 폼의 저장 버튼만 잠근다(측정 시작/종료와 무관한 별도 문).
// 왕복이 끝날 때까지 폼이 열려 있어야 이 잠금이 실제로 보인다 — 닫기는 성공 분기에서 카드에 알린다.
const savingGoal = ref(false)
const studyCard = ref<{ closeEdit: () => void } | null>(null)

// 타이머 상태 — start/stop 응답으로 부분 갱신
const remainingSeconds = ref(0)
const carriedDebtSeconds = ref(0)
const todayGoalSeconds = ref(0)
const todayReadSeconds = ref(0)
const carryover = ref(true)
const hasActiveSession = ref(false)
const activeStartedAt = ref<string | null>(null)
const activeBookTitle = ref<string | null>(null)
const activeBookTotalSeconds = ref(0)
const readingBooks = ref<BookOption[]>([])
const finishedBooks = ref<BookOption[]>([])
const wantToReadBooks = ref<BookOption[]>([])
const recentBookId = ref<number | null>(null)

// 공부 원장 — /api/dashboard의 study 블록. 없으면(옛 서버) IDLE_STUDY로 떨어져 독서 모드가 된다.
const study = ref<StudyState>(IDLE_STUDY)
const storedMode = ref<TimerMode>(readMode())
// 서버 진실이 저장값을 이긴다 — 진행 중 원장의 모드가 화면 모드다(미니앱 effectiveMode 1:1).
const mode = computed(() => effectiveMode(hasActiveSession.value, study.value.hasActiveSession, storedMode.value))
const measuring = computed(() => hasActiveSession.value || study.value.hasActiveSession)
// 왕복 중(starting/stopping)에도 잠근다 — 응답 대기 중에 모드를 바꾸면 반대 카드가 요청도 없이
// 남의 "시작하는 중…" 비활성 버튼을 뒤집어쓰고, 응답이 오면 도로 튄다.
const toggleLocked = computed(() => measuring.value || starting.value || stopping.value)
const modeHint = ref<string | null>(null)
watch(toggleLocked, l => { if (!l) modeHint.value = null })

// 공부 잔디 — /api/dashboard의 study 블록엔 graph가 없어 따로 받는다(페이지 수명 동안 캐시, 공부 stop 뒤 재조회).
const studyGraph = ref<GraphDto | null>(null)
const studyGraphError = ref(false)
async function loadStudyGraph() {
    studyGraphError.value = false
    try {
        const res = await fetch('/api/study/history', { credentials: 'same-origin' })
        if (!res.ok) throw new Error(res.statusText)
        studyGraph.value = (await res.json() as { graph: GraphDto }).graph
    } catch {
        studyGraphError.value = true
    }
}
// immediate: 저장 모드가 study면 마운트 즉시 /api/dashboard와 병렬로 나가 재로드 대기가 0이다.
watch(mode, m => { if (m === 'study' && !studyGraph.value) loadStudyGraph() }, { immediate: true })

// 책 고르기/태깅 통합 시트(발견 1, §6.5) — 'start'=측정 전 고르기, 'tag'=종료 후 태깅. 같은 시트를 모드로 겸한다.
const sheetMode = ref<'start' | 'tag' | null>(null)
const pendingSessionId = ref<number | null>(null)
const tagging = ref(false)

// 공부 책 시트 — 독서 시트와 원장이 갈린다(각자 자기 stop 응답에서만 열려 겹치지 않는다).
// 'start'=시작 전 고르기, 'tag'=종료 후 태깅, 'change'=측정 중 교체.
const studySheet = ref<'start' | 'tag' | 'change' | null>(null)
const studyPendingSessionId = ref<number | null>(null)

function applyTimerState(s: TimerState) {
    remainingSeconds.value = s.remainingSeconds
    carriedDebtSeconds.value = s.carriedDebtSeconds
    todayGoalSeconds.value = s.todayGoalSeconds
    todayReadSeconds.value = s.todayReadSeconds
    carryover.value = s.carryover
    hasActiveSession.value = s.hasActiveSession
    activeStartedAt.value = s.activeStartedAt
    activeBookTitle.value = s.activeBookTitle
    activeBookTotalSeconds.value = s.activeBookTotalSeconds
    readingBooks.value = s.readingBooks
    finishedBooks.value = s.finishedBooks
    recentBookId.value = s.recentBookId
}

/** /api/dashboard 응답 전체를 화면 상태에 얹는다(최초 로드·복귀 재조회 공용). graph·garden·quotes는 제외. */
function applyDashboard(d: DashboardResponse) {
    applyTimerState(d)
    wantToReadBooks.value = d.wantToReadBooks ?? []
    study.value = studyStateOf(d.study)
}

// 마지막 /api/dashboard 조회 시각 — 복귀 재조회 스로틀의 기준(요청 "전"에 찍는다).
let lastFetchedAt = 0

onMounted(async () => {
    try {
        lastFetchedAt = Date.now()
        const res = await fetch('/api/dashboard', { credentials: 'same-origin' })
        if (!res.ok) throw new Error(res.statusText)
        data.value = await res.json() as DashboardResponse
        applyDashboard(data.value)
    } catch {
        fetchError.value = true
    } finally {
        loading.value = false
    }
})

/**
 * 탭·창 복귀 시 조용한 재조회 — 다른 기기에서 시작·정지하면 이 화면이 낡기 때문(미니앱 silentRefresh와 같은 규칙).
 * 성공했을 때만 덮고 실패는 무시한다(화면 유지). graph·garden·quotes는 안 덮는다 —
 * 명언이 복귀마다 섞이면 어지럽고, 잔디는 stop 응답이 이미 갱신한다.
 */
async function refresh(force = false) {
    if (!force) {
        if (document.visibilityState !== 'visible') return
        // 내 왕복 응답을 낡은 스냅샷이 덮지 않게. force는 방금 실패한 내 왕복이 부른 것이라 덮을 게 없다.
        if (starting.value || stopping.value || tagging.value) return
    }
    if (!shouldRefresh(lastFetchedAt, Date.now(), force)) return
    lastFetchedAt = Date.now()
    try {
        const res = await fetch('/api/dashboard', { credentials: 'same-origin' })
        if (!res.ok) return
        applyDashboard(await res.json() as DashboardResponse)
    } catch {
        /* 조용히 — 다음 복귀·클릭에서 다시 시도한다 */
    }
}

// focus도 듣는 이유: 데스크톱은 창을 갈아타도 visibilityState가 'visible'로 남는 경우가 많다.
// 같은 스로틀을 타므로 둘 다 발화해도 요청은 1회.
const onReturn = () => { refresh() }
onMounted(() => {
    document.addEventListener('visibilitychange', onReturn)
    window.addEventListener('focus', onReturn)
})
onUnmounted(() => {
    document.removeEventListener('visibilitychange', onReturn)
    window.removeEventListener('focus', onReturn)
})

/** 409 = "내 화면이 낡았다"는 신호 — 문구를 띄우고 즉시 최신 상태를 받아 다음에 할 수 있는 일을 화면에 세운다. */
async function conflict(msg: string) {
    actionError.value = msg
    await refresh(true)
}

async function handleStart(bookId: number | null) {
    if (starting.value) return
    actionError.value = null
    starting.value = true
    try {
        const res = await fetch('/api/sessions/start', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        if (res.status === 409) { await conflict('다른 곳에서 이미 측정 중이에요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 시작할 수 없습니다'; return }
        applyTimerState(await res.json() as TimerState)
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        starting.value = false
    }
}

async function handleStop() {
    if (stopping.value) return
    actionError.value = null
    stopping.value = true
    try {
        const res = await fetch('/api/sessions/stop', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() },
        })
        if (res.status === 409) { await conflict('진행 중인 측정이 없어요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 종료할 수 없습니다'; return }
        // stop 응답은 타이머 + 잔디(graph) 동봉 — 측정 종료가 잔디가 변하는 순간이라
        // data.graph를 갈아끼워 새로고침 없이 잔디·연속일을 즉시 갱신한다(Vue deep ref가 재렌더 트리거).
        const r = await res.json() as StopResponse
        applyTimerState(r.timer)
        if (data.value) data.value.graph = r.graph
        // 책 없이 시작한 세션이면 "무슨 책?" 태깅 시트를 띄운다(발견 1). 책 골라 시작했으면 안 뜬다.
        if (r.untagged) {
            pendingSessionId.value = r.sessionId
            sheetMode.value = 'tag'
        }
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        stopping.value = false
    }
}

// 공부 원장 — 독서 핸들러와 같은 골격(starting/stopping 재사용). 응답은 StudyState 그대로다.
async function handleStudyStart(bookId: number | null) {
    if (starting.value) return
    actionError.value = null
    starting.value = true
    try {
        const res = await fetch('/api/study/start', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        if (res.status === 409) { await conflict('다른 곳에서 이미 측정 중이에요 — 화면을 최신으로 맞췄어요'); return }
        // 404 = 다른 탭에서 지운 책을 고른 것. 재조회가 새 books를 실어 와 화면이 스스로 맞는다.
        if (res.status === 404) { await conflict('그 책이 공부 서재에 없어요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 시작할 수 없습니다'; return }
        study.value = studyStateOf(await res.json())
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        starting.value = false
    }
}

async function handleStudyStop() {
    if (stopping.value) return
    actionError.value = null
    stopping.value = true
    try {
        const res = await fetch('/api/study/stop', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() },
        })
        if (res.status === 409) { await conflict('진행 중인 측정이 없어요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 종료할 수 없습니다'; return }
        const s = studyStateOf(await res.json())
        study.value = s
        // 책 없이 끝낸 측정이면 "무슨 책?" 태깅 시트. 서재가 비었으면 띄우지 않는다 —
        // 고를 게 없는데 매번 「담으러 가기」를 들이미는 건 잔소리다(E10).
        if (s.untaggedSessionId !== null && s.books.length > 0) {
            studyPendingSessionId.value = s.untaggedSessionId
            studySheet.value = 'tag'
        }
        // 측정 종료가 잔디가 변하는 순간 — 독서 stop의 data.graph 갈아끼우기와 같은 자리다.
        // await 하지 않는다: 히어로는 먼저 idle로 돌아간다.
        loadStudyGraph()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        stopping.value = false
    }
}

/**
 * 공부 하루 목표 — 히어로에서 바로 고친다(설계 §2.3-ⓑ). 독서는 /settings SSR 폼이지만 공부엔
 * 서버 폼이 없고, 설정 페이지의 「빠뜨린 날은 나중에 채워」 힌트가 공부엔 거짓이라 여기서 받는다.
 * seconds는 카드가 minutesToGoalSeconds로 이미 0 이상 정수 분에서 환산한 값이다(서버 400 방지).
 * 응답은 StudyState 그대로라 통째로 얹는다.
 */
async function handleStudyGoal(seconds: number) {
    if (savingGoal.value) return
    actionError.value = null
    savingGoal.value = true
    try {
        const res = await fetch('/api/study/goal', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ dailyGoalSeconds: seconds }),
        })
        // 실패면 폼을 열어 둔 채 둔다 — 사용자가 친 값이 살아 있어야 다시 누를 수 있다.
        if (!res.ok) { actionError.value = '목표를 저장하지 못했어요'; return }
        study.value = studyStateOf(await res.json())
        studyCard.value?.closeEdit()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        savingGoal.value = false
    }
}

function setMode(next: TimerMode) {
    writeMode(next)
    storedMode.value = next
    modeHint.value = null
}
// 측정 중 토글은 진짜 disabled가 아니다 — 클릭을 받아 왜 못 바꾸는지 말한다.
function onModeBlocked() { modeHint.value = '측정을 끝내면 바꿀 수 있어요' }

async function tagBook(bookId: number) {
    if (tagging.value || pendingSessionId.value === null) return
    tagging.value = true
    try {
        const res = await fetch(`/api/sessions/${pendingSessionId.value}/tag-book`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        if (!res.ok) { actionError.value = '책을 연결하지 못했어요'; return }
        closeSheet()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        tagging.value = false
    }
}

function openStartSheet() { sheetMode.value = 'start' }
function closeSheet() {
    sheetMode.value = null
    pendingSessionId.value = null
}

// 시트에서 책을 고르면 — start 모드면 그 책으로 측정 시작, tag 모드면 방금 세션에 태깅.
function onSheetPick(bookId: number) {
    if (sheetMode.value === 'tag') { tagBook(bookId); return }
    sheetMode.value = null
    handleStart(bookId)
}
// start 모드 하단 CTA — 책 없이 바로 시작.
function onSheetBookless() {
    sheetMode.value = null
    handleStart(null)
}
// ── 공부 책 시트 핸들러 ────────────────────────────────────────────────────────
// 태깅·교체는 tagging 플래그를 공유한다(둘 다 「측정 원장에 책을 붙이는」 왕복이고 동시에 열리지 않는다).
// 응답이 StudyState 통째라 recentBookId·books.totalSeconds까지 한 번에 최신이 된다.

/** 종료된 세션에 책을 붙인다 — 세션 id는 stop 응답이 준 것만 쓴다(지어내지 않는다). */
async function studyTagBook(bookId: number) {
    if (tagging.value || studyPendingSessionId.value === null) return
    tagging.value = true
    try {
        const res = await fetch(`/api/study/sessions/${studyPendingSessionId.value}/tag-book`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        // 여기엔 404 자동 복구(시트 닫기 + 재조회)를 두지 않는다 — start·change와 갈리는 자리다.
        // 이 시트가 그 세션을 태깅할 **유일한 진입점**이라(세션 id는 stop 응답에만 실린다) 닫으면
        // 미태깅으로 굳는다. 열어 둬야 사용자가 다른 책을 골라 성공할 수 있다.
        if (!res.ok) { actionError.value = '책을 연결하지 못했어요'; return }
        study.value = studyStateOf(await res.json())
        closeStudySheet()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        tagging.value = false
    }
}

/** 측정 중 교체 — 지금까지 잰 시간이 통째로 새 책으로 옮겨간다(서버 계약). null = 책 없이. */
async function studyChangeBook(bookId: number | null) {
    if (tagging.value) return
    tagging.value = true
    try {
        const res = await fetch('/api/study/active/book', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }),
        })
        if (res.status === 409) { closeStudySheet(); await conflict('진행 중인 측정이 없어요 — 화면을 최신으로 맞췄어요'); return }
        // 404 = 다른 곳에서 지운 책을 고른 것(start와 같은 규칙, E8). 시트를 닫지 않으면 지워진 그 행이
        // 목록에 남아 눌러도 계속 실패한다 — 재조회가 새 books를 실어 와 화면이 스스로 낫는다.
        if (res.status === 404) { closeStudySheet(); await conflict('그 책이 공부 서재에 없어요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '책을 바꾸지 못했어요'; return }
        study.value = studyStateOf(await res.json())
        closeStudySheet()
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        tagging.value = false
    }
}

function openStudySheet(m: 'start' | 'change') { studySheet.value = m }
function closeStudySheet() {
    studySheet.value = null
    studyPendingSessionId.value = null
}
// 시트에서 책을 고르면 — 모드마다 가는 문이 다르다.
function onStudySheetPick(bookId: number) {
    if (studySheet.value === 'tag') { studyTagBook(bookId); return }
    if (studySheet.value === 'change') { studyChangeBook(bookId); return }
    studySheet.value = null
    handleStudyStart(bookId)
}
// 하단 CTA — start=책 없이 시작 / tag=건너뛰기(닫기만) / change=책 없이 공부하기.
function onStudySheetNone() {
    if (studySheet.value === 'tag') { closeStudySheet(); return }
    if (studySheet.value === 'change') { studyChangeBook(null); return }
    studySheet.value = null
    handleStudyStart(null)
}

// 담기 성공(시트 안 검색담기) — 담은 책을 대시보드 목록에도 낙관적 반영(칩·시트 최신화).
function onSheetAdded(book: { id: number; title: string; status: string }) {
    const opt: BookOption = { id: book.id, title: book.title }
    const list = book.status === 'READING' ? readingBooks
        : book.status === 'FINISHED' ? finishedBooks : wantToReadBooks
    if (!list.value.find(b => b.id === book.id)) list.value = [opt, ...list.value]
}
</script>

<template>
    <div v-if="loading" class="status-line muted">불러오는 중…</div>

    <div v-else-if="fetchError" class="alert alert-error">
        페이지를 불러오지 못했어요. 잠시 후 새로고침 해주세요.
    </div>

    <template v-else-if="data">
        <!-- 발견 2(상단 정리): 헤더 → 타이머 → 잔디 → 바로가기 → 격언(발밑).
             격언(BrandQuote)은 Teleport로 #brand-quote-slot(대시보드 발밑)에 렌더되므로 여기 순서상 위치는 무관. -->
        <DashHeader :login-id="data.loginId" :profile-character-code="data.profileCharacterCode" />

        <WelcomeBanner v-if="showWelcome" :nickname="data.nickname" @close="showWelcome = false" />

        <EmailVerifyBanner v-if="!data.emailVerified" />

        <div v-if="actionError" class="alert alert-error">{{ actionError }}</div>

        <!-- 토글은 두 카드 안에 각각 든다. 옛 근거("카드 밖이면 아래 잔디·서재와 거짓말이 된다")는
             잔디·타일·정원이 mode를 같이 타면서 사라졌지만, 옮길 이유도 없어 자리는 그대로 둔다. -->
        <TimerCard
            v-if="mode === 'reading'"
            :remaining-seconds="remainingSeconds"
            :carried-debt-seconds="carriedDebtSeconds"
            :today-goal-seconds="todayGoalSeconds"
            :today-read-seconds="todayReadSeconds"
            :carryover="carryover"
            :streak="data.graph.currentStreak"
            :has-active-session="hasActiveSession"
            :active-started-at="activeStartedAt"
            :active-book-title="activeBookTitle"
            :active-book-total-seconds="activeBookTotalSeconds"
            :reading-books="readingBooks"
            :finished-books="finishedBooks"
            :want-to-read-books="wantToReadBooks"
            :recent-book-id="recentBookId"
            :starting="starting"
            :stopping="stopping"
            @start="handleStart"
            @stop="handleStop"
            @open-sheet="openStartSheet"
        >
            <template #mode>
                <ModeToggle :mode="mode" :locked="toggleLocked" :hint="modeHint" @change="setMode" @blocked="onModeBlocked" />
            </template>
        </TimerCard>

        <StudyTimerCard
            v-else
            ref="studyCard"
            :today-seconds="study.todaySeconds"
            :has-active-session="study.hasActiveSession"
            :active-started-at="study.activeStartedAt"
            :goal-seconds="study.goalSeconds"
            :books="study.books"
            :recent-book-id="study.recentBookId"
            :active-book="study.activeBook"
            :starting="starting"
            :stopping="stopping"
            :saving-goal="savingGoal"
            :changing="tagging"
            @start="handleStudyStart"
            @stop="handleStudyStop"
            @set-goal="handleStudyGoal"
            @open-sheet="openStudySheet('start')"
            @change-book="openStudySheet('change')"
        >
            <template #mode>
                <ModeToggle :mode="mode" :locked="toggleLocked" :hint="modeHint" @change="setMode" @blocked="onModeBlocked" />
            </template>
        </StudyTimerCard>

        <ContributionGraph v-if="mode === 'reading'" :graph="data.graph" />
        <ContributionGraph v-else-if="studyGraph" :graph="studyGraph" mode="study" />
        <section v-else class="dash-card dash-grass-card is-study">
            <span class="dash-pill">공부 기록</span>
            <span class="status-line muted">{{ studyGraphError ? '공부 기록을 불러오지 못했어요' : '불러오는 중…' }}</span>
        </section>

        <!-- 옛 스토리 스트립 자리 — 여백은 책에 귀속되므로 진입은 내 책방(/u/{me})의 책 리스트 하나뿐이다.
             대시보드에 대체 진입을 새로 만들지 않는다(2026-08-16 재설계 §D5-1). -->

        <div class="dash-grid-2col">
            <QuickNav :login-id="data.loginId" :mode="mode" />
            <!-- 정원은 독서 전용 세계관(작가는 독서 시간으로 입주). .dash-grid-2col이 auto-fit이라
                 공부 모드에선 QuickNav가 자연히 전체폭으로 선다 — 2단계 「공부 서재」가 이 자리에 온다. -->
            <GardenPanel v-if="mode === 'reading'" :garden="data.garden" />
        </div>

        <BrandQuote :quotes="data.quotes" />

        <!-- 통합 책 시트(발견 1, §6.5) — 'start'=측정 전 고르기, 'tag'=종료 후 태깅. 같은 시트를 모드로 겸한다. -->
        <BookPickSheet
            v-if="sheetMode"
            :mode="sheetMode"
            :reading-books="readingBooks"
            :finished-books="finishedBooks"
            :want-to-read-books="wantToReadBooks"
            :pending="sheetMode === 'tag' ? tagging : starting"
            @pick="onSheetPick"
            @bookless="onSheetBookless"
            @skip="closeSheet"
            @close="closeSheet"
            @added="onSheetAdded"
        />

        <!-- 공부 책 시트 — 목록만(검색·fetch 0). 데이터는 study.books가 이미 들고 있다. -->
        <StudyBookSheet
            v-if="studySheet"
            :mode="studySheet"
            :books="study.books"
            :current-book-id="study.activeBook?.id ?? null"
            :pending="studySheet === 'start' ? starting : tagging"
            @pick="onStudySheetPick"
            @none="onStudySheetNone"
            @close="closeStudySheet"
        />
    </template>
</template>
