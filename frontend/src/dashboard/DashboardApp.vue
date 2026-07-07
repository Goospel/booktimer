<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { DashboardResponse, TimerState, StopResponse, BookOption } from './types'
import { getCsrfToken } from '../shared/follow'
import TimerCard from './TimerCard.vue'
import BookPickSheet from './BookPickSheet.vue'
import ContributionGraph from './ContributionGraph.vue'
import GardenPanel from './GardenPanel.vue'
import BrandQuote from './BrandQuote.vue'
import EmailVerifyBanner from './EmailVerifyBanner.vue'
import QuickNav from './QuickNav.vue'
import DashHeader from './DashHeader.vue'
import StoryStrip from '../shared/story/StoryStrip.vue'

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
const carryover = ref(true)
const hasActiveSession = ref(false)
const activeStartedAt = ref<string | null>(null)
const activeBookTitle = ref<string | null>(null)
const activeBookTotalSeconds = ref(0)
const readingBooks = ref<BookOption[]>([])
const finishedBooks = ref<BookOption[]>([])
const wantToReadBooks = ref<BookOption[]>([])
const recentBookId = ref<number | null>(null)

// 책 고르기/태깅 통합 시트(발견 1, §6.5) — 'start'=측정 전 고르기, 'tag'=종료 후 태깅. 같은 시트를 모드로 겸한다.
const sheetMode = ref<'start' | 'tag' | null>(null)
const pendingSessionId = ref<number | null>(null)
const tagging = ref(false)

function applyTimerState(s: TimerState) {
    remainingSeconds.value = s.remainingSeconds
    carriedDebtSeconds.value = s.carriedDebtSeconds
    todayGoalSeconds.value = s.todayGoalSeconds
    carryover.value = s.carryover
    hasActiveSession.value = s.hasActiveSession
    activeStartedAt.value = s.activeStartedAt
    activeBookTitle.value = s.activeBookTitle
    activeBookTotalSeconds.value = s.activeBookTotalSeconds
    readingBooks.value = s.readingBooks
    finishedBooks.value = s.finishedBooks
    recentBookId.value = s.recentBookId
}

onMounted(async () => {
    try {
        const res = await fetch('/api/dashboard', { credentials: 'same-origin' })
        if (!res.ok) throw new Error(res.statusText)
        data.value = await res.json() as DashboardResponse
        applyTimerState(data.value)
        wantToReadBooks.value = data.value.wantToReadBooks ?? []
    } catch {
        fetchError.value = true
    } finally {
        loading.value = false
    }
})

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
        if (res.status === 409) { actionError.value = '이미 진행 중인 측정이 있습니다'; return }
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
        if (res.status === 409) { actionError.value = '진행 중인 측정이 없습니다'; return }
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
        <!-- 발견 2(상단 정리): 헤더 → 타이머 → 잔디 → 스토리 → 바로가기 → 격언(발밑).
             격언(BrandQuote)은 Teleport로 #brand-quote-slot(대시보드 발밑)에 렌더되므로 여기 순서상 위치는 무관. -->
        <DashHeader :login-id="data.loginId" :profile-character-code="data.profileCharacterCode" />

        <EmailVerifyBanner v-if="!data.emailVerified" />

        <div v-if="actionError" class="alert alert-error">{{ actionError }}</div>

        <TimerCard
            :remaining-seconds="remainingSeconds"
            :carried-debt-seconds="carriedDebtSeconds"
            :today-goal-seconds="todayGoalSeconds"
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
        />

        <ContributionGraph :graph="data.graph" />

        <!-- 독서 스토리 스트립 (sns-design §13.7) — 타이머 아래로 강등(발견 2). 로드 실패·스토리 0이어도 홈을 막지 않는다 -->
        <StoryStrip :my-login-id="data.loginId" :my-nickname="data.nickname"
                    :my-profile-character-code="data.profileCharacterCode" />

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
