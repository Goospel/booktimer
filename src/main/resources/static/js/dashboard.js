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
 */

/*
 * 목표 달성 알람 — 순수 전이 로직 (부수효과 없음, 테스트 가능).
 *
 * 알람은 "상태"가 아니라 "전이"에 건다 — goalMet이 미달→달성(rising edge)으로 넘어가는
 * 순간 1회만 onMet()을 부른다. 매 tick마다 goalMet이 참이라고 울리면 안 되기 때문이다.
 *   - prime(goalMet): 로드 시점 baseline 동기화. 이미 달성 상태로 로드되면 울리지 않게 한다.
 *   - update(goalMet): 매 tick 호출. 미달→달성에서만 발화하고, 미달로 내려가면 재무장한다.
 */
function createGoalAlarm(onMet) {
    let armed = false; // true = 이미 울렸음(미달로 떨어지기 전까지 재발화 금지)
    return {
        prime(goalMet) {
            armed = !!goalMet;
        },
        update(goalMet) {
            if (goalMet && !armed) {
                armed = true;
                onMet();
            } else if (!goalMet) {
                armed = false; // 다시 미달이면 재무장(다음 달성 때 또 울림)
            }
        }
    };
}

/*
 * 알람 발생 시 부수효과 — 점진적 향상(progressive enhancement):
 *   ① 소리(WebAudio 짧은 비프) + 화면 플래시 — 항상 동작(에셋·권한 불필요)
 *   ② OS 알림(Web Notifications) — 권한이 허용된 경우에만, 탭이 백그라운드여도 뜸
 * 권한 거부/구형 브라우저면 ②는 조용히 건너뛰고 ①로 자연 폴백한다.
 */
let _audioCtx = null;

function playGoalChime() {
    try {
        const Ctx = window.AudioContext || window.webkitAudioContext;
        if (!Ctx) return;
        if (!_audioCtx) _audioCtx = new Ctx();
        // 측정은 사용자가 「측정 시작」을 클릭해 들어오므로 보통 풀려 있지만, 안전히 resume.
        if (_audioCtx.state === 'suspended') _audioCtx.resume();

        // 두 음(높→더 높음) 짧게 — "딩-동" 느낌.
        const notes = [880, 1174.66];
        notes.forEach((freq, i) => {
            const osc = _audioCtx.createOscillator();
            const gain = _audioCtx.createGain();
            osc.type = 'sine';
            osc.frequency.value = freq;
            const start = _audioCtx.currentTime + i * 0.18;
            const end = start + 0.16;
            gain.gain.setValueAtTime(0.0001, start);
            gain.gain.exponentialRampToValueAtTime(0.25, start + 0.02);
            gain.gain.exponentialRampToValueAtTime(0.0001, end);
            osc.connect(gain);
            gain.connect(_audioCtx.destination);
            osc.start(start);
            osc.stop(end + 0.02);
        });
    } catch (e) {
        /* 소리는 부가 기능 — 실패해도 플래시/알림은 진행 */
    }
}

function showGoalNotification() {
    try {
        if (!('Notification' in window)) return;
        if (Notification.permission !== 'granted') return;
        new Notification('오늘 목표 달성! 🎉', {
            body: '오늘의 독서 목표를 채웠어요. 잘하고 있어요!',
            tag: 'booktimer-goal' // 같은 태그면 중복 알림 누적 방지
        });
    } catch (e) {
        /* 알림 실패는 무시 — 소리/플래시로 충분 */
    }
}

function readingTimer() {
    return {
        active: false,
        startedAtMs: null,
        baseRemaining: 0,  // 페이지 로드 시점 서버 잔여(초)
        elapsed: 0,        // 이번 측정 경과(초)
        flash: false,      // 목표 달성 순간 화면 플래시 토글(CSS .goal-flash)
        _interval: null,
        _alarm: null,
        _flashTimer: null,

        init() {
            const el = this.$el;
            this.active = el.dataset.active === 'true';
            this.baseRemaining = parseInt(el.dataset.remaining || '0', 10) || 0;

            // 알람: 발화 시 소리+플래시+(권한 있으면)OS 알림. baseline은 현재 goalMet에 맞춰
            // 두어, 이미 달성 상태로 로드돼도 울리지 않게 한다(전이만 잡는다).
            this._alarm = createGoalAlarm(() => this.fireGoalAlarm());
            this._alarm.prime(this.goalMet);

            const started = el.dataset.started;
            if (this.active && started && started !== 'null') {
                this.startedAtMs = Date.parse(started);
                // 측정 진입(사용자 제스처 직후)에 한 번 알림 권한을 요청해 둔다.
                this.requestNotificationPermission();
                this.tick();
                this._interval = setInterval(() => this.tick(), 1000);
            }
        },

        destroy() {
            if (this._interval) clearInterval(this._interval);
            if (this._flashTimer) clearTimeout(this._flashTimer);
        },

        tick() {
            this.elapsed = Math.max(0, Math.floor((Date.now() - this.startedAtMs) / 1000));
            // 잔여 갱신 직후 전이를 평가 — 미달→달성 순간에만 알람.
            if (this._alarm) this._alarm.update(this.goalMet);
        },

        requestNotificationPermission() {
            try {
                if (!('Notification' in window)) return;
                if (Notification.permission === 'default') Notification.requestPermission();
            } catch (e) {
                /* 권한 요청 실패해도 소리/플래시로 폴백 */
            }
        },

        fireGoalAlarm() {
            playGoalChime();
            showGoalNotification();
            // 카드 플래시: 잠깐 켰다가 끈다(반복 발화 시 타이머 갱신).
            this.flash = true;
            if (this._flashTimer) clearTimeout(this._flashTimer);
            this._flashTimer = setTimeout(() => { this.flash = false; }, 1500);
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

// Node 테스트에서 순수 로직(createGoalAlarm)만 require할 수 있게 노출. 브라우저는 무시.
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { createGoalAlarm };
}
