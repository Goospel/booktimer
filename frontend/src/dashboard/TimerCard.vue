<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { BookOption } from './types'
import { useReadingTimer } from './useReadingTimer'
import { computeProgress, panelState, goalLabel, fmtMSS } from './timerProgress'
import BookPickForm from './BookPickForm.vue'
import ClockDigits from '../shared/ClockDigits.vue'

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
    /** 시트에서 방금 고른 책 — 칩이 이 값을 우선한다(BookPickForm으로 통과). */
    pickedBook?: BookOption | null
    starting?: boolean
    stopping?: boolean
    /** 측정 중 책 교체 왕복 중 — 「책 바꾸기」를 잠근다(공부 StudyTimerCard와 같은 이름). */
    changing?: boolean
}>()

const emit = defineEmits<{
    start: [bookId: number | null]
    stop: []
    openSheet: []
    /** 측정 중 [책 바꾸기] — 시트를 여는 건 부모다(R2 P4). */
    changeBook: []
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

function totalHM(s: number): string {
    return `${Math.floor(s / 3600)}시간 ${Math.floor((s % 3600) / 60)}분`
}

// 목표 달성 표식 — 미니앱 ui.tsx와 같은 새싹 세 획(editorialTheme.test.ts가 두 파일의 문자열을 대조한다).
// 눈썹엔 선 새싹, 달성 칸엔 잉크 도장(원 두 겹 + 새싹). 기본 이모지 대신이고, 튀는 움직임은 두지 않는다(새 애니메이션 0).
const SPROUT_PATHS = ['M12 20v-6', 'M12 14c0-3.5-2.5-6-6-6 0 3.5 2.5 6 6 6z', 'M12 14c0-3.5 2.5-6 6-6 0 3.5-2.5 6-6 6z'] as const
</script>

<template>
    <section class="dash-card dash-timer-hero">
        <!-- 좌: 대형 숫자 + 진행바 -->
        <div class="dash-timer-left">
            <span class="dash-pill" :class="{ 'dash-pill-ok': progress.isAchieved }">
                <svg v-if="progress.isAchieved" class="dash-sprout" data-sprout viewBox="0 0 24 24" width="15" height="15" fill="none"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path v-for="p in SPROUT_PATHS" :key="p" :d="p" /></svg>
                {{ progress.isAchieved ? '오늘 목표 달성' : '오늘 읽은 시간' }}
            </span>
            <div class="dash-timer-num" :class="{ 'dash-timer-num-ok': progress.isAchieved, 'is-long': todayReadDisplay.length > 5 }"><ClockDigits :text="todayReadDisplay" /></div>
            <!-- 목표 0(목표 없음)이면 진행바 · 메타 줄을 통째로 안 그린다 — 채울 분모가 없다(미니앱 게이지와 같은 규칙). -->
            <div v-if="todayGoalSeconds > 0" class="dash-progress-wrap">
                <div class="dash-progress-track">
                    <div class="dash-progress-fill" :class="{ 'dash-progress-fill-ok': progress.isAchieved }"
                         :style="{ width: progress.pctStr }"></div>
                </div>
                <div class="dash-progress-meta">
                    <span>오늘 목표 {{ goalText }}</span>
                    <span class="dash-progress-pct"><ClockDigits :text="remainingLabel" /></span>
                </div>
            </div>
        </div>

        <!-- 우: 상태 3패널(오늘 표). <Transition mode="out-in">으로 패널 교체를 크로스페이드해
             측정 시작/종료 시 "툭" 끊기던 즉시 DOM 교체를 부드럽게 한다(key=state로 전환 트리거).
             reduced-motion이면 CSS에서 트랜지션 0초로 즉시 교체. -->
        <div class="dash-timer-right">
            <Transition name="panel-fade" mode="out-in">
                <!-- MEASURING (측정 중이면 달성해도 유지 — 종료 보존) -->
                <div v-if="state === 'measuring'" key="measuring" class="dash-state-panel">
                    <div class="dash-state-row">
                        <span class="dash-pill dash-pill-pulse"><span class="dash-pulse-dot"></span>측정 중</span>
                        <span class="dash-session-time"><ClockDigits :text="sessionDisplay" /></span>
                    </div>
                    <div class="dash-divider"></div>
                    <!-- 책 없이 재는 중엔 빈칸이 아니라 「책 없이」가 상태다(R2 P11). 누적 줄은 책이 있을 때만 —
                         책이 없는데 「이 책 누적 0시간 0분」은 거짓이다. -->
                    <div class="dash-kv">
                        <span class="dash-kv-k">지금 읽는 책</span>
                        <span class="dash-kv-v">{{ activeBookTitle ?? '책 없이' }}</span>
                    </div>
                    <div v-if="activeBookTitle !== null" class="dash-kv">
                        <span class="dash-kv-k">이 책 누적 독서</span>
                        <span class="dash-kv-v-num">{{ totalHM(activeBookTotalSeconds) }}</span>
                    </div>
                    <!-- 잰 시간은 통째로 새 책에 옮겨간다(서버 계약) — 측정을 끊지 않고 바꾼다. 공부 카드와 같은 자리·같은 모양. -->
                    <button type="button" class="dash-btn-link dash-bookless" :disabled="changing" @click="emit('changeBook')">
                        책 바꾸기
                    </button>
                    <!-- 측정 중엔 「측정 시작」이 없어 이 버튼이 화면의 검정 알약 하나다(미니앱 정지 = 잉크 채움). -->
                    <button type="button" class="dash-btn-fill" @click="emit('stop')" :disabled="stopping">
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
                        :picked-book="pickedBook"
                        :pending="starting"
                        @start="(id) => emit('start', id)"
                        @open-sheet="emit('openSheet')"
                    />
                </div>

                <!-- ACHIEVED (측정 안 하는데 오늘 달성) — 격려는 유지하되 측정 시작 폼도 함께 노출.
                     목표를 채워도 계속 시간을 측정할 수 있어야 한다(시작 버튼이 사라지면 안 됨). -->
                <div v-else key="achieved" class="dash-state-panel dash-state-achieved">
                    <svg class="dash-goal-stamp" data-goal-stamp viewBox="0 0 120 120" width="76" height="76" fill="none" aria-hidden="true">
                        <circle cx="60" cy="60" r="56" stroke-width="1.5" />
                        <circle cx="60" cy="60" r="48" stroke-width="1" />
                        <g transform="translate(24 18) scale(3)" stroke-width="0.9" stroke-linecap="round" stroke-linejoin="round">
                            <path v-for="p in SPROUT_PATHS" :key="p" :d="p" />
                        </g>
                    </svg>
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
                        :picked-book="pickedBook"
                        :pending="starting"
                        @start="(id) => emit('start', id)"
                        @open-sheet="emit('openSheet')"
                    />
                </div>
            </Transition>
            <!-- 「밀린 N분」 안내는 걷었다(책생글 PR-R · REQ-120) — 웹은 밀린 시간을 보이지 않는다. 미니앱은 그대로. -->
        </div>
    </section>
</template>
