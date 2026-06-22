import { ref, computed, watch, onUnmounted } from 'vue'
import type { Ref } from 'vue'

/**
 * 대시보드 타이머 composable — dashboard.js readingTimer() 1:1 포팅.
 *
 * elapsed는 벽시계(Date.now - startedAtMs)에서 매 틱마다 직접 산출한다 —
 * 모바일 백그라운드 throttle 시 여러 틱이 밀려도 포그라운드 복귀 시 올바른 값으로 자가보정.
 */
export function useReadingTimer(
    baseRemaining: Ref<number>,
    baseFloor: Ref<number>,
    active: Ref<boolean>,
    startedAtIso: Ref<string | null>
) {
    const elapsed = ref(0)
    let intervalId: ReturnType<typeof setInterval> | null = null

    function startedAtMs(): number {
        const iso = startedAtIso.value
        if (!iso || iso === 'null') return NaN
        return Date.parse(iso)
    }

    function tick() {
        const ms = startedAtMs()
        if (isNaN(ms)) {
            elapsed.value = 0
            return
        }
        elapsed.value = Math.max(0, Math.floor((Date.now() - ms) / 1000))
    }

    function stopTimer() {
        if (intervalId !== null) {
            clearInterval(intervalId)
            intervalId = null
        }
        elapsed.value = 0
    }

    function startTimer() {
        stopTimer()
        const ms = startedAtMs()
        if (isNaN(ms)) {
            console.warn('[useReadingTimer] startedAt 파싱 실패:', startedAtIso.value)
            return
        }
        tick()
        intervalId = setInterval(tick, 1000)
    }

    watch(active, (isActive) => {
        if (isActive) startTimer()
        else stopTimer()
    }, { immediate: true })

    onUnmounted(stopTimer)

    const remainingNow = computed(() => {
        const r = active.value ? baseRemaining.value - elapsed.value : baseRemaining.value
        return Math.max(baseFloor.value, r)
    })

    const goalMet = computed(() => remainingNow.value <= baseFloor.value)

    function fmt(totalSeconds: number): string {
        const s = Math.max(0, Math.floor(totalSeconds))
        const hh = String(Math.floor(s / 3600)).padStart(2, '0')
        const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
        const ss = String(s % 60).padStart(2, '0')
        return `${hh}:${mm}:${ss}`
    }

    return { elapsed, remainingNow, goalMet, fmt }
}
