/*
 * BookTimer 대시보드 — 실시간 째깍 타이머 (Alpine.js 컴포넌트).
 *
 * 백엔드는 페이지 로드 시점의 값만 내려준다(누적 잔여 remaining, 진행 중 세션 시작시각).
 * 이 컴포넌트는 그 값을 받아 1초마다 클라이언트에서 경과/잔여를 갱신한다 —
 * 서버 왕복 없이 "초가 흐르는" 인터랙션을 만든다(N-017의 SSR 다리).
 *
 * 서버 값은 카드 엘리먼트의 data-* 속성으로 주입된다(Thymeleaf th:attr):
 *   data-active   : 진행 중 세션 여부 ("true"/"false")
 *   data-started  : 세션 시작 시각 (ISO-8601, 예 2026-06-01T12:00:00Z)
 *   data-remaining: 페이지 로드 시점의 누적 잔여 초
 *
 * goalMet(잔여 0)은 화면 배지("오늘 목표 달성! 🎉")와 타이머 색(.met)을 위한 상태값.
 * 달성 "순간"을 소리/OS알림으로 알리는 인앱 알람은 제거됨 — 탭이 살아있어야만 동작하고
 * 모바일에서 화면이 꺼지면 백그라운드 throttle로 전이 감지조차 안 돼 실효가 없었다.
 * 달성 순간 알림은 서버발(이메일 재참여 넛지) 인프라가 붙을 때 다시 다룬다(plan.md §전략 레버 ①).
 */
function readingTimer() {
    return {
        active: false,
        startedAtMs: null,
        baseRemaining: 0,  // 페이지 로드 시점 서버 잔여(초)
        elapsed: 0,        // 이번 측정 경과(초)
        _interval: null,

        init() {
            const el = this.$el;
            this.active = el.dataset.active === 'true';
            this.baseRemaining = parseInt(el.dataset.remaining || '0', 10) || 0;

            const started = el.dataset.started;
            if (this.active && started && started !== 'null') {
                this.startedAtMs = Date.parse(started);
                this.tick();
                this._interval = setInterval(() => this.tick(), 1000);
            }
        },

        destroy() {
            if (this._interval) clearInterval(this._interval);
        },

        tick() {
            this.elapsed = Math.max(0, Math.floor((Date.now() - this.startedAtMs) / 1000));
        },

        // 진행 중이면 (로드시 잔여 - 경과)를 0에서 바닥 처리, 아니면 로드시 잔여 그대로.
        get remainingNow() {
            const r = this.active ? this.baseRemaining - this.elapsed : this.baseRemaining;
            return Math.max(0, r);
        },

        get goalMet() {
            return this.remainingNow === 0;
        },

        // 초 -> "HH:MM:SS"
        fmt(totalSeconds) {
            const s = Math.max(0, Math.floor(totalSeconds));
            const hh = String(Math.floor(s / 3600)).padStart(2, '0');
            const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
            const ss = String(s % 60).padStart(2, '0');
            return hh + ':' + mm + ':' + ss;
        }
    };
}
