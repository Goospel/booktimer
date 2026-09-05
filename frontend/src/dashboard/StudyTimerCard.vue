<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useReadingTimer } from './useReadingTimer'
import { fmtMSS, goalLabel } from './timerProgress'
import { studyProgress, minutesToGoalSeconds } from './studyProgress'

const props = withDefaults(defineProps<{
    /** 오늘 공부한 초(완료 세션 합) — 측정 중 몫은 elapsed로 얹는다(독서와 같은 분업). */
    todaySeconds: number
    hasActiveSession: boolean
    activeStartedAt: string | null
    /** 하루 목표 초. 0 = 「목표 없음」이라는 정당한 상태 — 게이지 대신 「하루 목표 정하기」를 띄운다. */
    goalSeconds?: number
    starting?: boolean
    stopping?: boolean
    savingGoal?: boolean
}>(), { goalSeconds: 0 })

const emit = defineEmits<{ start: []; stop: []; setGoal: [seconds: number] }>()

// props를 ref로 래핑해 composable에 전달(TimerCard와 같은 3줄). 공부엔 부채가 없어 base는 0 —
// remainingNow는 안 쓰고 elapsed(벽시계 경과)만 쓴다.
const active = ref(props.hasActiveSession)
const startedAtIso = ref<string | null>(props.activeStartedAt)
watch(() => props.hasActiveSession, v => active.value = v)
watch(() => props.activeStartedAt, v => startedAtIso.value = v)

const { elapsed } = useReadingTimer(ref(0), active, startedAtIso)

// 게이지는 히어로 숫자와 같은 분자를 본다 — 완료 합 + 측정 중 경과.
const progress = computed(() => studyProgress(props.goalSeconds, props.todaySeconds + elapsed.value))

// 목표 인라인 편집 — 설정 페이지로 보내지 않는다(서버 0줄, 미니앱과 같은 자리).
const editing = ref(false)
const goalMinutes = ref(0)
function openEdit() {
    goalMinutes.value = Math.round(props.goalSeconds / 60)
    editing.value = true
}
function submitGoal() {
    emit('setGoal', minutesToGoalSeconds(goalMinutes.value))
    editing.value = false
}
</script>

<template>
    <section class="dash-card dash-timer-hero is-study">
        <div class="dash-timer-left">
            <div class="dash-timer-head">
                <span class="dash-pill">오늘 공부한 시간</span>
                <slot name="mode" />
            </div>
            <div class="dash-timer-num">{{ fmtMSS(todaySeconds + elapsed) }}</div>

            <div v-if="goalSeconds > 0" class="dash-progress-wrap">
                <div class="dash-progress-track">
                    <div class="dash-progress-fill" :style="{ width: progress.pctStr }"></div>
                </div>
                <div class="dash-progress-meta">
                    <span>하루 목표 {{ goalLabel(goalSeconds) }}
                        <button type="button" class="dash-goal-change" @click="openEdit">변경</button></span>
                    <span class="dash-progress-pct">
                        {{ progress.achieved ? '목표 달성' : `목표까지 ${fmtMSS(progress.remaining)}` }}
                    </span>
                </div>
            </div>
            <button v-else type="button" class="dash-btn-link dash-bookless dash-goal-set" @click="openEdit">
                하루 목표 정하기
            </button>

            <form v-if="editing" class="dash-goal-edit" @submit.prevent="submitGoal">
                <label>하루 목표
                    <!-- step은 스피너 간격이 아니라 **유효성 제약**이다 — step="5"면 7·23분이 stepMismatch가
                         되어 네이티브 검증이 submit을 조용히 막는다(실브라우저 실측 2026-09-05). -->
                    <input type="number" min="0" step="1" v-model.number="goalMinutes" aria-label="하루 목표(분)"> 분
                </label>
                <button type="submit" class="dash-btn-fill" :disabled="savingGoal">
                    {{ savingGoal ? '저장하는 중…' : '저장' }}
                </button>
                <!-- 보조 둘은 「책 없이 시작」과 같은 조용한 링크 관용구(.dash-bookless) — 그냥 .dash-btn-link면
                     전역 button의 연필 테두리·18px를 그대로 받아 저장과 같은 무게로 선다(실브라우저 실측). -->
                <button type="button" class="dash-btn-link dash-bookless" @click="editing = false">취소</button>
                <!-- 미니앱 showClearGoal — 공부이고 지울 목표가 있을 때만. 독서엔 이 문이 없다(0이 원장을 깨는 값). -->
                <button v-if="goalSeconds > 0" type="button" class="dash-btn-link dash-bookless"
                        @click="emit('setGoal', 0); editing = false">목표 없이 지내기</button>
            </form>
        </div>

        <div class="dash-timer-right">
            <Transition name="panel-fade" mode="out-in">
                <div v-if="hasActiveSession" key="measuring" class="dash-state-panel">
                    <div class="dash-state-row">
                        <span class="dash-pill dash-pill-pulse"><span class="dash-pulse-dot"></span>측정 중</span>
                        <span class="dash-session-time">{{ fmtMSS(elapsed) }}</span>
                    </div>
                    <button type="button" class="dash-btn-outline" :disabled="stopping" @click="emit('stop')">
                        {{ stopping ? '종료하는 중…' : '측정 종료' }}
                    </button>
                </div>

                <div v-else key="idle" class="dash-state-panel">
                    <span class="dash-idle-label">지금 공부를 시작할까요?</span>
                    <button type="button" class="dash-btn-fill" :disabled="starting" @click="emit('start')">
                        {{ starting ? '시작하는 중…' : '공부 측정 시작' }}
                    </button>
                </div>
            </Transition>
        </div>
    </section>
</template>
