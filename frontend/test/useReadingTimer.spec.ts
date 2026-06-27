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

    // ── 1. 부채 라이브 소진(핵심 회귀) ──────────────────────────────────
    // 오늘 목표를 다 채운 뒤 과거 빚을 갚는 동안에도 남은 시간은 0까지 매초 줄어야 한다.
    // 옛 동작은 floor(과거 빚)에서 멈춰 라이브 카운트다운이 죽었다(버그) — 서버 backward-only
    // 재분배(오늘 초과분이 과거 빚을 갚음)와 어긋났다. 이제 baseRemaining(=전체 빚)에서 0까지 소진한다.
    it('remainingNow_burnsDebtToZero: elapsed가 오늘 부채를 넘어도 전체 빚이 0까지 라이브 감소', () => {
        // baseRemaining=100(오늘 부채 50 + 과거 빚 50), 80초 경과 → max(0, 100-80)=20
        // (옛 floor=50 clamp였다면 50에서 멈췄을 값)
        const startedIso = new Date(FIXED_MS - 80_000).toISOString()
        const { remainingNow } = useReadingTimer(ref(100), ref(true), ref(startedIso))
        expect(remainingNow.value).toBe(20)
    })

    // ── 2. 0에서 멈춤(음수 방지) ─────────────────────────────────────────
    it('remainingNow_flooredAtZero: elapsed > baseRemaining이면 0으로 clamp', () => {
        // 200초 경과, baseRemaining=100 → max(0, 100-200)=0
        const startedIso = new Date(FIXED_MS - 200_000).toISOString()
        const { remainingNow } = useReadingTimer(ref(100), ref(true), ref(startedIso))
        expect(remainingNow.value).toBe(0)
    })

    // ── 3. 비활성 = 잔여 그대로 ─────────────────────────────────────────
    it('remainingNow_inactive_equalsRemaining: active=false이면 elapsed 무시', () => {
        const { remainingNow } = useReadingTimer(ref(300), ref(false), ref(null))
        expect(remainingNow.value).toBe(300)
    })

    // ── 4. wall-clock 자가보정 ───────────────────────────────────────────
    it('elapsed_wallClockBased_selfCorrects: 시간이 점프해도 누적 오차 없이 벽시계 값으로', () => {
        const startedIso = new Date(FIXED_MS).toISOString()
        const { elapsed } = useReadingTimer(ref(300), ref(true), ref(startedIso))

        // 시작 직후 = 0
        expect(elapsed.value).toBe(0)

        // 10초 점프 (setInterval이 10번 아니라 1번 늦게 떴어도 elapsed=10이 보장됨)
        vi.advanceTimersByTime(10_000)
        expect(elapsed.value).toBe(10)
    })

    // ── 5. NaN fallback ──────────────────────────────────────────────────
    it('startedAt_unparseable_fallback: 파싱 불가 날짜 → elapsed=0, NaN cascade 차단', () => {
        const { elapsed } = useReadingTimer(ref(100), ref(true), ref('garbage'))
        expect(elapsed.value).toBe(0)
    })
})
