<script setup lang="ts">
import { ref, watch } from 'vue'
import { useReadingTimer } from './useReadingTimer'
import { fmtMSS } from './timerProgress'

const props = defineProps<{
    /** 오늘 공부한 초(완료 세션 합) — 측정 중 몫은 elapsed로 얹는다(독서와 같은 분업). */
    todaySeconds: number
    hasActiveSession: boolean
    activeStartedAt: string | null
    starting?: boolean
    stopping?: boolean
}>()

const emit = defineEmits<{ start: []; stop: [] }>()

// props를 ref로 래핑해 composable에 전달(TimerCard와 같은 3줄). 공부엔 부채가 없어 base는 0 —
// remainingNow는 안 쓰고 elapsed(벽시계 경과)만 쓴다.
const active = ref(props.hasActiveSession)
const startedAtIso = ref<string | null>(props.activeStartedAt)
watch(() => props.hasActiveSession, v => active.value = v)
watch(() => props.activeStartedAt, v => startedAtIso.value = v)

const { elapsed } = useReadingTimer(ref(0), active, startedAtIso)
</script>

<template>
    <section class="dash-card dash-timer-hero is-study">
        <div class="dash-timer-left">
            <div class="dash-timer-head">
                <span class="dash-pill">오늘 공부한 시간</span>
                <slot name="mode" />
            </div>
            <div class="dash-timer-num">{{ fmtMSS(todaySeconds + elapsed) }}</div>
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
