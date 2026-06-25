<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { BookOption } from './types'
import { useReadingTimer } from './useReadingTimer'
import { computeProgress, panelState, goalLabel, fmtMSS } from './timerProgress'
import BookPickForm from './BookPickForm.vue'

const props = defineProps<{
    remainingSeconds: number
    carriedDebtSeconds: number
    todayGoalSeconds: number
    carryover: boolean
    streak: number
    hasActiveSession: boolean
    activeStartedAt: string | null
    activeBookTitle: string | null
    activeBookTotalSeconds: number
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    recentBookId: number | null
}>()

const emit = defineEmits<{
    start: [bookId: number]
    stop: []
}>()

// props를 ref로 래핑해 composable에 전달(props 변경 시 반응 — N-082 보존)
const baseRemaining = ref(props.remainingSeconds)
const baseFloor = ref(props.carriedDebtSeconds)
const active = ref(props.hasActiveSession)
const startedAtIso = ref<string | null>(props.activeStartedAt)

watch(() => props.remainingSeconds, v => baseRemaining.value = v)
watch(() => props.carriedDebtSeconds, v => baseFloor.value = v)
watch(() => props.hasActiveSession, v => active.value = v)
watch(() => props.activeStartedAt, v => startedAtIso.value = v)

const { elapsed, remainingNow } = useReadingTimer(baseRemaining, baseFloor, active, startedAtIso)

// 진행바·달성은 서버 스냅샷이 아니라 라이브 remainingNow에서 파생(계획 §3-B/D)
const progress = computed(() =>
    computeProgress(remainingNow.value, props.carriedDebtSeconds, props.todayGoalSeconds, props.carryover)
)
const state = computed(() => panelState(props.hasActiveSession, progress.value.isAchieved))
const remainingDisplay = computed(() => fmtMSS(remainingNow.value))
const sessionDisplay = computed(() => fmtMSS(elapsed.value))
const goalText = computed(() => goalLabel(props.todayGoalSeconds))

function totalHM(s: number): string {
    return `${Math.floor(s / 3600)}시간 ${Math.floor((s % 3600) / 60)}분`
}
</script>

<template>
    <section class="dash-card dash-timer-hero">
        <!-- 좌: 대형 숫자 + 진행바 -->
        <div class="dash-timer-left">
            <span class="dash-pill" :class="{ 'dash-pill-ok': progress.isAchieved }">
                {{ progress.isAchieved ? '🌿 오늘 목표 달성!' : '오늘 남은 독서 시간' }}
            </span>
            <div class="dash-timer-num">{{ remainingDisplay }}</div>
            <div class="dash-progress-wrap">
                <div class="dash-progress-track">
                    <div class="dash-progress-fill" :style="{ width: progress.pctStr }"></div>
                </div>
                <div class="dash-progress-meta">
                    <span>오늘 목표 {{ goalText }}</span>
                    <span class="dash-progress-pct">{{ progress.pctStr }} 달성</span>
                </div>
            </div>
        </div>

        <!-- 우: 상태 3패널 -->
        <div class="dash-timer-right">
            <!-- MEASURING (측정 중이면 달성해도 유지 — 종료 보존) -->
            <div v-if="state === 'measuring'" class="dash-state-panel">
                <div class="dash-state-row">
                    <span class="dash-pill dash-pill-pulse"><span class="dash-pulse-dot"></span>측정 중</span>
                    <span class="dash-session-time">{{ sessionDisplay }}</span>
                </div>
                <div class="dash-divider"></div>
                <div class="dash-kv">
                    <span class="dash-kv-k">지금 읽는 책</span>
                    <span class="dash-kv-v">{{ activeBookTitle }}</span>
                </div>
                <div class="dash-kv">
                    <span class="dash-kv-k">이 책 누적 독서</span>
                    <span class="dash-kv-v-num">{{ totalHM(activeBookTotalSeconds) }}</span>
                </div>
                <button type="button" class="dash-btn-outline" @click="emit('stop')">측정 종료</button>
            </div>

            <!-- IDLE -->
            <div v-else-if="state === 'idle'" class="dash-state-panel">
                <BookPickForm
                    :reading-books="readingBooks"
                    :finished-books="finishedBooks"
                    :recent-book-id="recentBookId"
                    @start="(id) => emit('start', id)"
                />
            </div>

            <!-- ACHIEVED (측정 안 하는데 오늘 달성) — 격려는 유지하되 측정 시작 폼도 함께 노출.
                 목표를 채워도 계속 시간을 측정할 수 있어야 한다(시작 버튼이 사라지면 안 됨). -->
            <div v-else class="dash-state-panel dash-state-achieved">
                <span class="dash-achieved-emoji">🌱</span>
                <p class="dash-achieved-title">오늘도 약속을 지켰어요</p>
                <p class="dash-achieved-sub">
                    <template v-if="streak > 0">연속 {{ streak }}일째 — 천천히, 꾸준히</template>
                    <template v-else>오늘 목표를 채웠어요</template>
                </p>
                <div class="dash-divider"></div>
                <BookPickForm
                    :reading-books="readingBooks"
                    :finished-books="finishedBooks"
                    :recent-book-id="recentBookId"
                    @start="(id) => emit('start', id)"
                />
            </div>
        </div>
    </section>
</template>
