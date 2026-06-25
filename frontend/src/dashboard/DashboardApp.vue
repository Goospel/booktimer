<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { DashboardResponse, TimerState } from './types'
import { getCsrfToken } from '../shared/follow'
import TimerCard from './TimerCard.vue'
import ContributionGraph from './ContributionGraph.vue'
import GardenPanel from './GardenPanel.vue'
import QuoteCard from './QuoteCard.vue'
import EmailVerifyBanner from './EmailVerifyBanner.vue'
import QuickNav from './QuickNav.vue'
import DashHeader from './DashHeader.vue'

const data = ref<DashboardResponse | null>(null)
const loading = ref(true)
const fetchError = ref(false)
const actionError = ref<string | null>(null)

// 타이머 상태 — start/stop 응답으로 부분 갱신
const remainingSeconds = ref(0)
const carriedDebtSeconds = ref(0)
const todayGoalSeconds = ref(0)
const carryover = ref(true)
const hasActiveSession = ref(false)
const activeStartedAt = ref<string | null>(null)
const activeBookTitle = ref<string | null>(null)
const activeBookTotalSeconds = ref(0)
const readingBooks = ref<{ id: number; title: string }[]>([])
const finishedBooks = ref<{ id: number; title: string }[]>([])
const recentBookId = ref<number | null>(null)

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
    } catch {
        fetchError.value = true
    } finally {
        loading.value = false
    }
})

async function handleStart(bookId: number) {
    actionError.value = null
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
    }
}

async function handleStop() {
    actionError.value = null
    try {
        const res = await fetch('/api/sessions/stop', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() },
        })
        if (res.status === 409) { actionError.value = '진행 중인 측정이 없습니다'; return }
        if (!res.ok) { actionError.value = '측정을 종료할 수 없습니다'; return }
        applyTimerState(await res.json() as TimerState)
    } catch {
        actionError.value = '네트워크 오류가 발생했습니다'
    }
}
</script>

<template>
    <div v-if="loading" class="status-line muted">불러오는 중…</div>

    <div v-else-if="fetchError" class="alert alert-error">
        페이지를 불러오지 못했어요. 잠시 후 새로고침 해주세요.
    </div>

    <template v-else-if="data">
        <DashHeader :login-id="data.loginId" />

        <QuoteCard :quotes="data.quotes" />

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
            :recent-book-id="recentBookId"
            @start="handleStart"
            @stop="handleStop"
        />

        <ContributionGraph :graph="data.graph" />

        <div class="dash-grid-2col">
            <QuickNav :login-id="data.loginId" />
            <GardenPanel :garden="data.garden" />
        </div>

        <form class="dash-logout-form" action="/logout" method="post">
            <input type="hidden" name="_csrf" :value="getCsrfToken()">
            <button type="submit" class="dash-logout-btn">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M15 17l5-5-5-5" /><path d="M20 12H9" /><path d="M9 4H5v16h4" />
                </svg>
                로그아웃
            </button>
        </form>
    </template>
</template>
