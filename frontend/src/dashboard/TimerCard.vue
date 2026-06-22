<script setup lang="ts">
import { ref, watch } from 'vue'
import type { BookOption } from './types'
import { useReadingTimer } from './useReadingTimer'
import BookPickForm from './BookPickForm.vue'

const props = defineProps<{
    remainingSeconds: number
    carriedDebtSeconds: number
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

// props를 ref로 래핑해 composable에 전달(props 변경 시 반응)
const baseRemaining = ref(props.remainingSeconds)
const baseFloor = ref(props.carriedDebtSeconds)
const active = ref(props.hasActiveSession)
const startedAtIso = ref<string | null>(props.activeStartedAt)

watch(() => props.remainingSeconds, v => baseRemaining.value = v)
watch(() => props.carriedDebtSeconds, v => baseFloor.value = v)
watch(() => props.hasActiveSession, v => active.value = v)
watch(() => props.activeStartedAt, v => startedAtIso.value = v)

const { elapsed, remainingNow, goalMet, fmt } = useReadingTimer(baseRemaining, baseFloor, active, startedAtIso)

function totalHM(s: number): string {
    return `${Math.floor(s / 3600)}시간 ${Math.floor((s % 3600) / 60)}분`
}
</script>

<template>
    <section class="card timer-card">
        <h2>오늘 남은 독서 시간</h2>
        <p class="timer-display" :class="{ met: goalMet }">{{ fmt(remainingNow) }}</p>

        <p v-if="active" class="timer-sub">
            이번 측정 경과 <span class="mono">{{ fmt(elapsed) }}</span>
        </p>
        <span v-if="goalMet" class="goal-badge">오늘 목표 달성! 🎉</span>

        <!-- 측정 중: 누적 + 종료 -->
        <div v-if="hasActiveSession" class="timer-controls">
            <p v-if="activeBookTitle" class="status-line muted">
                이 책 누적 독서 <strong>{{ totalHM(activeBookTotalSeconds) }}</strong>
            </p>
            <button type="button" class="btn-danger" @click="emit('stop')">측정 종료</button>
        </div>

        <!-- 대기 중: 책 선택 + 시작 -->
        <BookPickForm
            v-else
            :reading-books="readingBooks"
            :finished-books="finishedBooks"
            :recent-book-id="recentBookId"
            @start="(id) => emit('start', id)"
        />
    </section>
</template>
