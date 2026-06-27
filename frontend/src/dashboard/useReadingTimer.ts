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

    // 남은 시간은 0까지 라이브로 줄어든다. baseRemaining(서버 remainingSeconds)은 전체 빚
    // (오늘 부채 + 과거 빚 합)이고, 서버는 오늘 목표 초과분으로 과거 빚을 갚는다(WeeklyDebtCalculator
    // backward-only 재분배). 따라서 elapsed가 오늘 부채분을 넘으면 baseRemaining - elapsed가
    // 그대로 과거 빚을 깎는 값이 되어 0까지 매초 감소한다 — 과거 빚 구간에서 멈추던 옛 floor clamp는
    // 도메인 모델과 어긋난 버그였다(측정 종료 시에만 갱신되어 통일성이 깨졌다).
    const remainingNow = computed(() => {
        const r = active.value ? baseRemaining.value - elapsed.value : baseRemaining.value
        return Math.max(0, r)
    })

    function fmt(totalSeconds: number): string {
        const s = Math.max(0, Math.floor(totalSeconds))
        const hh = String(Math.floor(s / 3600)).padStart(2, '0')
        const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
        const ss = String(s % 60).padStart(2, '0')
        return `${hh}:${mm}:${ss}`
    }

    return { elapsed, remainingNow, fmt }
}
