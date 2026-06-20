// PWA 설치 유도 칩 — 전역 ESM (빌드 없음, static 직접 서빙)
//
// 역할: 우하단 코너 알약 칩(+ × 닫기)을 document.body에 주입한다.
// 플랫폼 분기: 크로미움(beforeinstallprompt) / iOS Safari(수동 안내 오버레이) / 그 외(숨김)
// 7일 침묵: ×로 닫으면 localStorage에 타임스탬프 저장, 7일간 표시하지 않음.

// ──────────────────────────────────────────────────────────
// 순수 함수 (export — vitest 단위 테스트 대상)
// ──────────────────────────────────────────────────────────

/** localStorage 저장 키 */
export const DISMISS_KEY = 'pwa-install-dismissed';

/** 7일(밀리초) */
export const DISMISS_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * 침묵이 아직 유효한지 반환한다.
 * @param {number} nowMs - 현재 시각(ms)
 * @param {number|null|undefined} storedMs - 저장된 dismissed 타임스탬프
 * @returns {boolean} true = 7일 이내(칩 숨김), false = 기간 초과 또는 미저장(칩 표시)
 */
export function isDismissalActive(nowMs, storedMs) {
    if (storedMs == null) return false;
    return (nowMs - storedMs) < DISMISS_MS;
}

/**
 * UA 문자열과 display-mode로 설치 지원 플랫폼을 판별한다.
 * @param {string} ua - navigator.userAgent
 * @param {'standalone'|'browser'|string} displayMode - 현재 display-mode
 * @returns {'standalone'|'ios'|'android'|'desktop-chromium'|'unsupported'}
 */
export function detectPlatform(ua, displayMode) {
    if (displayMode === 'standalone') return 'standalone';

    // iOS Safari: iPhone/iPad/iPod이면서 Chrome for iOS(CriOS)/Firefox for iOS(FxiOS)/Opera(OPiOS) 아님
    const isIOS = /iPad|iPhone|iPod/.test(ua) && !/CriOS|FxiOS|OPiOS/.test(ua);
    if (isIOS) return 'ios';

    const isAndroid = /Android/.test(ua);
    // 크로미움 계열: Chrome/ 포함, Opera(OPR/) 제외. Edge는 UA에 Chrome/ 포함 → 여기 해당
    const isChromium = /Chrome\//.test(ua) && !/OPR\//.test(ua);

    if (isChromium) {
        return isAndroid ? 'android' : 'desktop-chromium';
    }

    return 'unsupported';
}

/**
 * 칩의 표시 모드를 결정한다.
 * @param {string} platform - detectPlatform 반환값
 * @param {boolean} dismissed - 침묵 활성 여부
 * @returns {'hidden'|'prompt'|'ios-hint'}
 */
export function decideChip(platform, dismissed) {
    if (platform === 'standalone') return 'hidden';
    if (dismissed) return 'hidden';
    if (platform === 'ios') return 'ios-hint';
    if (platform === 'android' || platform === 'desktop-chromium') return 'prompt';
    return 'hidden';
}

// ──────────────────────────────────────────────────────────
// 브라우저 전용 초기화 (node/vitest 환경 제외)
// ──────────────────────────────────────────────────────────

if (typeof window !== 'undefined') {
    _initInstallChip();
}

function _initInstallChip() {
    let deferred = null;

    const storedRaw = localStorage.getItem(DISMISS_KEY);
    const dismissed = isDismissalActive(Date.now(), storedRaw ? Number(storedRaw) : null);

    const isStandalone = window.matchMedia('(display-mode: standalone)').matches
        || window.navigator.standalone === true; // iOS Safari 독립 실행 감지
    const displayMode = isStandalone ? 'standalone' : 'browser';

    const platform = detectPlatform(navigator.userAgent, displayMode);
    const mode = decideChip(platform, dismissed);

    if (mode === 'hidden') return;

    if (mode === 'prompt') {
        // 크로미움: beforeinstallprompt 이벤트를 가로채 칩 클릭 시 사용
        window.addEventListener('beforeinstallprompt', (e) => {
            e.preventDefault();
            deferred = e;
            _showChip(mode, deferred);
        });
    } else {
        // iOS: 즉시 칩 표시
        _showChip(mode, null);
    }

    // 이미 설치 완료되면 칩 제거
    window.addEventListener('appinstalled', () => {
        const chip = document.getElementById('pwa-install-chip');
        if (chip) chip.remove();
    });
}

function _showChip(mode, deferred) {
    if (document.getElementById('pwa-install-chip')) return; // 중복 방지

    const chip = document.createElement('div');
    chip.id = 'pwa-install-chip';
    chip.setAttribute('role', 'group');
    chip.setAttribute('aria-label', '홈 화면에 앱 추가');
    chip.style.cssText = [
        'position:fixed',
        'bottom:calc(env(safe-area-inset-bottom,0px) + 72px)',
        'right:16px',
        'z-index:100',
        'display:flex',
        'align-items:center',
        'gap:6px',
        'padding:8px 10px 8px 14px',
        'background:rgba(110,138,106,0.93)',
        'color:#fff',
        'border-radius:9999px',
        'font-size:13px',
        'font-weight:500',
        'box-shadow:0 2px 10px rgba(0,0,0,.28)',
        'user-select:none',
        'backdrop-filter:blur(4px)',
        '-webkit-backdrop-filter:blur(4px)',
        'transition:opacity .2s',
        'cursor:default',
    ].join(';');

    // 메인 라벨 (클릭 가능)
    const label = document.createElement('button');
    label.type = 'button';
    label.textContent = '📲 홈 화면에 추가';
    label.setAttribute('aria-label', '홈 화면에 추가');
    label.style.cssText = [
        'background:none',
        'border:none',
        'color:inherit',
        'font:inherit',
        'cursor:pointer',
        'padding:0',
    ].join(';');

    // 닫기 버튼
    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.setAttribute('aria-label', '설치 안내 닫기');
    closeBtn.textContent = '×';
    closeBtn.style.cssText = [
        'background:none',
        'border:none',
        'color:rgba(255,255,255,.8)',
        'font-size:20px',
        'line-height:1',
        'cursor:pointer',
        'padding:0 2px',
    ].join(';');

    chip.appendChild(label);
    chip.appendChild(closeBtn);
    document.body.appendChild(chip);

    // 메인 클릭 동작
    label.addEventListener('click', () => {
        if (mode === 'prompt' && deferred) {
            deferred.prompt();
            deferred.userChoice.then(() => chip.remove());
        } else if (mode === 'ios-hint') {
            _showIosOverlay(chip);
        }
    });

    // 닫기 동작 — 7일 침묵
    closeBtn.addEventListener('click', () => {
        localStorage.setItem(DISMISS_KEY, String(Date.now()));
        chip.remove();
    });
}

function _showIosOverlay(chip) {
    // 토글: 이미 열려 있으면 닫기
    const existing = document.getElementById('pwa-ios-overlay');
    if (existing) {
        existing.remove();
        return;
    }

    const overlay = document.createElement('div');
    overlay.id = 'pwa-ios-overlay';
    overlay.style.cssText = [
        'position:fixed',
        'bottom:0',
        'left:0',
        'right:0',
        'background:rgba(255,255,255,.97)',
        'border-top:1px solid #d8d8d8',
        'border-radius:16px 16px 0 0',
        'padding:24px 24px calc(env(safe-area-inset-bottom,0px) + 28px)',
        'z-index:200',
        'box-shadow:0 -4px 24px rgba(0,0,0,.14)',
        'font-family:-apple-system,sans-serif',
    ].join(';');

    overlay.innerHTML = `
        <div style="text-align:center;margin-bottom:18px;font-size:17px;font-weight:700;color:#1a1a1a">
            홈 화면에 추가하기
        </div>
        <ol style="padding-left:22px;color:#444;font-size:15px;line-height:2">
            <li>하단 <strong>공유</strong> 버튼(<span style="font-size:18px">⎙</span>) 탭</li>
            <li><strong>홈 화면에 추가</strong> 탭</li>
            <li>오른쪽 위 <strong>추가</strong> 탭</li>
        </ol>
        <div style="text-align:center;margin-top:20px">
            <button id="pwa-ios-overlay-close"
                style="padding:11px 36px;background:#6E8A6A;color:#fff;border:none;border-radius:9999px;font-size:15px;font-weight:600;cursor:pointer">
                확인
            </button>
        </div>
    `;

    document.body.appendChild(overlay);

    document.getElementById('pwa-ios-overlay-close').addEventListener('click', () => overlay.remove());
    overlay.addEventListener('click', (e) => { if (e.target === overlay) overlay.remove(); });
}
