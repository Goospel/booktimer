// 알림 설정 위젯 — 전역 ESM (빌드 없음, static 직접 서빙)
//
// 역할: settings.html의 "알림" 섹션 토글 바인딩.
// 로드순서·iOS 게이트·권한 흐름은 실 브라우저로 검증(N-083/T-053).

// ──────────────────────────────────────────────────────────
// 순수 함수 (export — vitest 단위 테스트 대상)
// ──────────────────────────────────────────────────────────

/**
 * 푸시 지원 여부와 iOS 미설치 안내 필요 여부를 순수하게 결정한다.
 * @param {{ua:string, standalone:boolean, hasServiceWorker:boolean, hasPushManager:boolean}} opts
 * @returns {{supported:boolean, iosInstallHint:boolean}}
 */
export function detectPushSupport({ ua, standalone, hasServiceWorker, hasPushManager }) {
    const isIOS = /iPad|iPhone|iPod/.test(ua) && !/CriOS|FxiOS|OPiOS/.test(ua);
    if (isIOS && !standalone) {
        return { supported: false, iosInstallHint: true };
    }
    if (!hasServiceWorker || !hasPushManager) {
        return { supported: false, iosInstallHint: false };
    }
    return { supported: true, iosInstallHint: false };
}

/**
 * VAPID 공개키가 실제로 설정된 값인지 확인한다.
 * @param {string|null|undefined} key
 * @returns {boolean}
 */
export function vapidConfigured(key) {
    return !!key && key !== 'not-configured';
}

/**
 * URL-safe base64 문자열을 Uint8Array로 변환한다 (VAPID applicationServerKey용).
 * @param {string} base64String
 * @returns {Uint8Array}
 */
export function urlBase64ToUint8Array(base64String) {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = atob(base64);
    const arr = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; i++) arr[i] = rawData.charCodeAt(i);
    return arr;
}

// ──────────────────────────────────────────────────────────
// 내부 헬퍼 — DOM 메타 읽기
// ──────────────────────────────────────────────────────────

function getCsrfToken() {
    return document.querySelector('meta[name="_csrf"]')?.content ?? '';
}

function getVapidPublicKey() {
    return document.querySelector('meta[name="vapid-public-key"]')?.content ?? '';
}

// ──────────────────────────────────────────────────────────
// 브라우저 전용 초기화 (node/vitest 환경 제외)
// ──────────────────────────────────────────────────────────

if (typeof window !== 'undefined') {
    // type=module은 기본 defer라 DOMContentLoaded보다 먼저 실행되는 보장이 없음(N-083).
    // readyState 체크로 타이밍에 무관하게 초기화를 보장한다.
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', _initNotificationSettings);
    } else {
        _initNotificationSettings();
    }
}

async function _initNotificationSettings() {
    const container = document.getElementById('notification-settings-container');
    if (!container) return;

    const ua = navigator.userAgent;
    const standalone = navigator.standalone === true
        || window.matchMedia('(display-mode: standalone)').matches;

    const { supported, iosInstallHint } = detectPushSupport({
        ua,
        standalone,
        hasServiceWorker: 'serviceWorker' in navigator,
        hasPushManager: 'PushManager' in window,
    });

    const marketingInitial = container.dataset.marketingPushConsent === 'true';

    // iOS 미설치 안내
    const iosHint = document.getElementById('push-ios-install-hint');
    if (iosHint) iosHint.hidden = !iosInstallHint;

    // 푸시 미지원이면 푸시 관련 행 숨김
    const pushRows = container.querySelectorAll('[data-push-row]');
    pushRows.forEach(el => { el.hidden = !supported; });

    if (!supported) return;

    // VAPID 미설정이면 토글 비활성
    const key = getVapidPublicKey();
    if (!vapidConfigured(key)) {
        const btns = container.querySelectorAll('button.btn-push-toggle');
        btns.forEach(btn => { btn.disabled = true; btn.title = '푸시 알림이 설정되지 않았습니다.'; });
        return;
    }

    // 매일 독서 알림 (L3a) 초기 상태
    let reminderEnabled = false;
    try {
        const reg = await navigator.serviceWorker.ready;
        const sub = await reg.pushManager.getSubscription();
        reminderEnabled = sub !== null && Notification.permission === 'granted';
    } catch { /* 무시 */ }

    _updateReminderBtn(reminderEnabled);
    _updateMarketingBtn(marketingInitial);

    // 버튼 바인딩
    const reminderBtn = document.getElementById('push-reminder-toggle');
    if (reminderBtn) {
        reminderBtn.addEventListener('click', async () => {
            reminderEnabled = await _toggleReminder(reminderEnabled, key);
            _updateReminderBtn(reminderEnabled);
            // 리마인더가 꺼지면 마케팅 힌트 업데이트
            const mktEnabled = document.getElementById('push-marketing-toggle')
                ?.dataset.enabled === 'true';
            _updateMarketingBtn(mktEnabled);
        });
    }

    const marketingBtn = document.getElementById('push-marketing-toggle');
    if (marketingBtn) {
        let mktEnabled = marketingInitial;
        marketingBtn.addEventListener('click', async () => {
            mktEnabled = await _toggleMarketing(mktEnabled, reminderEnabled, key);
            _updateMarketingBtn(mktEnabled);
        });
    }
}

function _updateReminderBtn(enabled) {
    const btn = document.getElementById('push-reminder-toggle');
    if (!btn) return;
    btn.textContent = enabled ? '🔔 매일 독서 알림 끄기' : '🔕 매일 독서 알림 받기';
    btn.dataset.enabled = String(enabled);
}

function _updateMarketingBtn(enabled) {
    const btn = document.getElementById('push-marketing-toggle');
    if (!btn) return;
    btn.textContent = enabled ? '📣 복귀 알림 끄기' : '📭 복귀 알림 받기';
    btn.dataset.enabled = String(enabled);

    // 리마인더 꺼져 있으면 힌트 표시
    const reminderEnabled = document.getElementById('push-reminder-toggle')?.dataset.enabled === 'true';
    const hint = document.getElementById('push-marketing-hint');
    if (hint) hint.hidden = reminderEnabled;
}

async function _toggleReminder(currentEnabled, vapidKey) {
    const reg = await navigator.serviceWorker.ready;
    if (!currentEnabled) {
        const permission = await Notification.requestPermission();
        if (permission !== 'granted') return currentEnabled;
        try {
            const sub = await reg.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: urlBase64ToUint8Array(vapidKey),
            });
            const j = sub.toJSON();
            await fetch('/api/push/subscribe', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
                body: JSON.stringify({ endpoint: j.endpoint, p256dh: j.keys?.p256dh ?? '', auth: j.keys?.auth ?? '' }),
            });
            return true;
        } catch (e) {
            console.error('[Push] 구독 실패:', e);
            return currentEnabled;
        }
    } else {
        try {
            const sub = await reg.pushManager.getSubscription();
            if (sub) {
                await fetch('/api/push/unsubscribe', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
                    body: JSON.stringify({ endpoint: sub.endpoint }),
                });
                await sub.unsubscribe();
            }
            return false;
        } catch (e) {
            console.error('[Push] 구독 취소 실패:', e);
            return currentEnabled;
        }
    }
}

async function _toggleMarketing(currentEnabled, reminderEnabled, vapidKey) {
    if (!currentEnabled) {
        const permission = await Notification.requestPermission();
        if (permission !== 'granted') return currentEnabled;
        try {
            const reg = await navigator.serviceWorker.ready;
            let sub = await reg.pushManager.getSubscription();
            if (!sub) {
                sub = await reg.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: urlBase64ToUint8Array(vapidKey),
                });
                const j = sub.toJSON();
                await fetch('/api/push/subscribe', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
                    body: JSON.stringify({ endpoint: j.endpoint, p256dh: j.keys?.p256dh ?? '', auth: j.keys?.auth ?? '' }),
                });
            }
            await fetch('/api/push/marketing-consent', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
                body: JSON.stringify({ enabled: true }),
            });
            return true;
        } catch (e) {
            console.error('[Push] 복귀 알림 동의 실패:', e);
            return currentEnabled;
        }
    } else {
        try {
            await fetch('/api/push/marketing-consent', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
                body: JSON.stringify({ enabled: false }),
            });
            return false;
        } catch (e) {
            console.error('[Push] 복귀 알림 철회 실패:', e);
            return currentEnabled;
        }
    }
}
