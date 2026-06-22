import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { useReadingTimer } from '../src/dashboard/useReadingTimer'

const FIXED_MS = new Date('2024-06-01T00:00:00.000Z').getTime()

describe('useReadingTimer', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(FIXED_MS)
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    // ── 1. floor 멈춤 ─────────────────────────────────────────────────
    it('remainingNow_capsAtFloor: elapsed > baseRemaining여도 floor 이하로 안 내려감', () => {
        // 200초 전에 시작 (elapsed = 200, baseRemaining = 100, floor = 50)
        const startedIso = new Date(FIXED_MS - 200_000).toISOString()
        const { remainingNow } = useReadingTimer(ref(100), ref(50), ref(true), ref(startedIso))

        // Math.max(50, 100-200) = Math.max(50, -100) = 50
        expect(remainingNow.value).toBe(50)
    })

    // ── 2. 비활성 = 잔여 그대로 ─────────────────────────────────────────
    it('remainingNow_inactive_equalsRemaining: active=false이면 elapsed 무시', () => {
        const { remainingNow } = useReadingTimer(ref(300), ref(50), ref(false), ref(null))
        expect(remainingNow.value).toBe(300)
    })

    // ── 3. wall-clock 자가보정 ───────────────────────────────────────────
    it('elapsed_wallClockBased_selfCorrects: 시간이 점프해도 누적 오차 없이 벽시계 값으로', () => {
        const startedIso = new Date(FIXED_MS).toISOString()
        const { elapsed } = useReadingTimer(ref(300), ref(0), ref(true), ref(startedIso))

        // 시작 직후 = 0
        expect(elapsed.value).toBe(0)

        // 10초 점프 (setInterval이 10번 아니라 1번 늦게 떴어도 elapsed=10이 보장됨)
        vi.advanceTimersByTime(10_000)
        expect(elapsed.value).toBe(10)
    })

    // ── 4. goalMet 전환 ──────────────────────────────────────────────────
    it('goalMet_transition: remainingNow <= floor이면 true', () => {
        // 80초 전 시작: elapsed=80, remainingNow=max(50,100-80)=50, goalMet=50<=50=true
        const startedIso = new Date(FIXED_MS - 80_000).toISOString()
        const { goalMet } = useReadingTimer(ref(100), ref(50), ref(true), ref(startedIso))
        expect(goalMet.value).toBe(true)
    })

    // ── 5. NaN fallback ──────────────────────────────────────────────────
    it('startedAt_unparseable_fallback: 파싱 불가 날짜 → elapsed=0, NaN cascade 차단', () => {
        const { elapsed } = useReadingTimer(ref(100), ref(0), ref(true), ref('garbage'))
        expect(elapsed.value).toBe(0)
    })
})
