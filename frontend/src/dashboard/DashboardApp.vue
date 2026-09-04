<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { DashboardResponse, TimerState, StopResponse, BookOption, StudyState } from './types'
import { IDLE_STUDY } from './types'
import { getCsrfToken } from '../shared/follow'
import type { TimerMode } from './timerMode'
import { shouldRefresh, readMode, writeMode, effectiveMode } from './timerMode'
import TimerCard from './TimerCard.vue'
import StudyTimerCard from './StudyTimerCard.vue'
import ModeToggle from './ModeToggle.vue'
import BookPickSheet from './BookPickSheet.vue'
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
const modeHint = ref<string | null>(null)
watch(measuring, m => { if (!m) modeHint.value = null })

// 책 고르기/태깅 통합 시트(발견 1, §6.5) — 'start'=측정 전 고르기, 'tag'=종료 후 태깅. 같은 시트를 모드로 겸한다.
const sheetMode = ref<'start' | 'tag' | null>(null)
const pendingSessionId = ref<number | null>(null)
const tagging = ref(false)

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
    study.value = d.study ?? IDLE_STUDY
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
// 1차엔 책 선택·종료 후 태깅이 없어 stop 응답의 untaggedSessionId는 무시한다(웹에 공부 서재가 없다).
async function handleStudyStart() {
    if (starting.value) return
    actionError.value = null
    starting.value = true
    try {
        const res = await fetch('/api/study/start', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: '{}',
        })
        if (res.status === 409) { await conflict('다른 곳에서 이미 측정 중이에요 — 화면을 최신으로 맞췄어요'); return }
        if (!res.ok) { actionError.value = '측정을 시작할 수 없습니다'; return }
        study.value = await res.json() as StudyState
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
        study.value = await res.json() as StudyState
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    } finally {
        stopping.value = false
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

        <!-- 토글은 두 카드 안에 각각 든다 — v-if 바깥으로 빼면 "페이지 모드"로 읽혀
             아래 잔디·서재(독서 그대로)와 거짓말이 된다(설계 §2.1-C 기각). -->
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
                <ModeToggle :mode="mode" :locked="measuring" :hint="modeHint" @change="setMode" @blocked="onModeBlocked" />
            </template>
        </TimerCard>

        <StudyTimerCard
            v-else
            :today-seconds="study.todaySeconds"
            :has-active-session="study.hasActiveSession"
            :active-started-at="study.activeStartedAt"
            :starting="starting"
            :stopping="stopping"
            @start="handleStudyStart"
            @stop="handleStudyStop"
        >
            <template #mode>
                <ModeToggle :mode="mode" :locked="measuring" :hint="modeHint" @change="setMode" @blocked="onModeBlocked" />
            </template>
        </StudyTimerCard>

        <ContributionGraph :graph="data.graph" />

        <!-- 옛 스토리 스트립 자리 — 여백은 책에 귀속되므로 진입은 내 책방(/u/{me})의 책 리스트 하나뿐이다.
             대시보드에 대체 진입을 새로 만들지 않는다(2026-08-16 재설계 §D5-1). -->

        <div class="dash-grid-2col">
            <QuickNav :login-id="data.loginId" />
            <GardenPanel :garden="data.garden" />
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
    </template>
</template>
