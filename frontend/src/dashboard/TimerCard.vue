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
    /** 오늘 읽은 초(완료 세션 합). 측정 중 몫은 elapsed로 얹는다 — 부채에서 역산하지 않는 이유는 computeProgress 참조. */
    todayReadSeconds: number
    carryover: boolean
    streak: number
    hasActiveSession: boolean
    activeStartedAt: string | null
    activeBookTitle: string | null
    activeBookTotalSeconds: number
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    wantToReadBooks: BookOption[]
    recentBookId: number | null
    starting?: boolean
    stopping?: boolean
}>()

const emit = defineEmits<{
    start: [bookId: number | null]
    stop: []
    openSheet: []
}>()

// props를 ref로 래핑해 composable에 전달(props 변경 시 반응 — N-082 보존)
const baseRemaining = ref(props.remainingSeconds)
const active = ref(props.hasActiveSession)
const startedAtIso = ref<string | null>(props.activeStartedAt)

watch(() => props.remainingSeconds, v => baseRemaining.value = v)
watch(() => props.hasActiveSession, v => active.value = v)
watch(() => props.activeStartedAt, v => startedAtIso.value = v)

const { elapsed, remainingNow } = useReadingTimer(baseRemaining, active, startedAtIso)

// 진행바·달성은 서버 스냅샷이 아니라 라이브 값에서 파생(계획 §3-B/D)
const progress = computed(() =>
    computeProgress(
        remainingNow.value,
        props.carriedDebtSeconds,
        props.todayGoalSeconds,
        props.carryover,
        props.todayReadSeconds + elapsed.value
    )
)
const state = computed(() => panelState(props.hasActiveSession, progress.value.isAchieved))
// 히어로 큰 숫자 = 오늘 읽은 시간(카운트업) = 서버 완료 합 + 측정 중 경과. 같은 elapsed 틱이 동력이라
// 별도 tick이 없다(발견 3·4: 남은시간 카운트다운 → 성취 카운트업).
const todayReadDisplay = computed(() => fmtMSS(progress.value.todayRead))
const sessionDisplay = computed(() => fmtMSS(elapsed.value))
const goalText = computed(() => goalLabel(props.todayGoalSeconds))
// 진행바 메타 우측: 달성이면 "목표 달성 ✓", 아니면 "목표까지 M:SS"(남은 시간은 보조 정보로 강등).
const remainingLabel = computed(() =>
    progress.value.isAchieved ? '목표 달성' : `목표까지 ${fmtMSS(progress.value.remainingToGoal)}`
)
// 밀린 빚 안내 — 있을 때만. 7일 자동 소멸이 폐지됐으니(2026-08-14) "기다리면 사라진다"고 말할 수 없다.
// 대신 지우는 두 수단(더 읽기·광고)을 제시해 여전히 위협이 아니라 "할 수 있다"로 프레이밍한다.
const showForgive = computed(() => props.carriedDebtSeconds > 0)
const forgiveMinutes = computed(() => Math.max(1, Math.round(props.carriedDebtSeconds / 60)))

function totalHM(s: number): string {
    return `${Math.floor(s / 3600)}시간 ${Math.floor((s % 3600) / 60)}분`
}
</script>

<template>
    <section class="dash-card dash-timer-hero">
        <!-- 좌: 대형 숫자 + 진행바 -->
        <div class="dash-timer-left">
            <span class="dash-pill" :class="{ 'dash-pill-ok': progress.isAchieved }">
                {{ progress.isAchieved ? '🌿 오늘 목표 달성!' : '오늘 읽은 시간' }}
            </span>
            <div class="dash-timer-num" :class="{ 'dash-timer-num-ok': progress.isAchieved }">{{ todayReadDisplay }}</div>
            <div class="dash-progress-wrap">
                <div class="dash-progress-track">
                    <div class="dash-progress-fill" :class="{ 'dash-progress-fill-ok': progress.isAchieved }"
                         :style="{ width: progress.pctStr }"></div>
                </div>
                <div class="dash-progress-meta">
                    <span>오늘 목표 {{ goalText }}</span>
                    <span class="dash-progress-pct">{{ remainingLabel }}</span>
                </div>
            </div>
            <p v-if="showForgive" class="dash-forgive-note">
                밀린 {{ forgiveMinutes }}분은 목표보다 더 읽으면 줄어들어요 — 천천히 갚아도 괜찮아요.
            </p>
        </div>

        <!-- 우: 상태 3패널. <Transition mode="out-in">으로 패널 교체를 크로스페이드해
             측정 시작/종료 시 "툭" 끊기던 즉시 DOM 교체를 부드럽게 한다(key=state로 전환 트리거).
             reduced-motion이면 CSS에서 트랜지션 0초로 즉시 교체. -->
        <div class="dash-timer-right">
            <Transition name="panel-fade" mode="out-in">
                <!-- MEASURING (측정 중이면 달성해도 유지 — 종료 보존) -->
                <div v-if="state === 'measuring'" key="measuring" class="dash-state-panel">
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
                    <button type="button" class="dash-btn-outline" @click="emit('stop')" :disabled="stopping">
                        {{ stopping ? '종료하는 중…' : '측정 종료' }}
                    </button>
                </div>

                <!-- IDLE -->
                <div v-else-if="state === 'idle'" key="idle" class="dash-state-panel">
                    <BookPickForm
                        :reading-books="readingBooks"
                        :finished-books="finishedBooks"
                        :want-to-read-books="wantToReadBooks"
                        :recent-book-id="recentBookId"
                        :pending="starting"
                        @start="(id) => emit('start', id)"
                        @open-sheet="emit('openSheet')"
                    />
                </div>

                <!-- ACHIEVED (측정 안 하는데 오늘 달성) — 격려는 유지하되 측정 시작 폼도 함께 노출.
                     목표를 채워도 계속 시간을 측정할 수 있어야 한다(시작 버튼이 사라지면 안 됨). -->
                <div v-else key="achieved" class="dash-state-panel dash-state-achieved">
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
                        :want-to-read-books="wantToReadBooks"
                        :recent-book-id="recentBookId"
                        :pending="starting"
                        @start="(id) => emit('start', id)"
                        @open-sheet="emit('openSheet')"
                    />
                </div>
            </Transition>
        </div>
    </section>
</template>
