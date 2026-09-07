// PWA 설치 유도 칩 — 전역 ESM (빌드 없음, static 직접 서빙)
//
// 역할: 우하단 코너 알약 칩(+ × 닫기)을 document.body에 주입한다.
// 플랫폼 분기: 데스크톱 크로미움 → PWA 설치(beforeinstallprompt) / 모바일 → 미니앱 유도 / 그 외 → 숨김.
//   모바일에서 설치 대신 미니앱으로 보내는 것은 웹의 주 사용처를 데스크톱·태블릿에 두는 방침이다.
//   ⚠️ PWA 설치 자체를 미니앱으로 바꿀 수는 없다 — manifest의 start_url은 같은 오리진의 https여야 해서
//   intoss:// 스킴을 넣을 수 없고, 토스 SDK에도 홈 화면 추가 API가 없다. 그래서 「무엇을 설치하느냐」가
//   아니라 「어느 플랫폼에 무엇을 띄우느냐」로 푼다.
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

    // 모바일은 브라우저를 가리지 않는다 — 이 두 값은 PWA 설치가 아니라 미니앱 딥링크로 가고,
    // 딥링크는 beforeinstallprompt를 지원하지 않는 브라우저에서도 열린다(iOS Chrome·Android Firefox 포함).
    // 옛 판별은 「설치할 수 있는가」를 물었기에 그 둘을 unsupported로 버렸다.
    if (/iPad|iPhone|iPod/.test(ua)) return 'ios';
    if (/Android/.test(ua)) return 'android';

    // 설치 유도가 남은 곳은 데스크톱뿐이라, 크로미움 판별도 여기서만 한다.
    // Chrome/ 포함 · Opera(OPR/) 제외 — Edge는 UA에 Chrome/이 있어 여기 해당한다.
    const isChromium = /Chrome\//.test(ua) && !/OPR\//.test(ua);
    return isChromium ? 'desktop-chromium' : 'unsupported';
}

/**
 * 칩의 표시 모드를 결정한다.
 *
 * <p>모바일 두 갈래는 PWA 설치가 아니라 <b>미니앱</b>으로 보낸다 — 웹의 주 사용처를 데스크톱·태블릿에
 * 두고 모바일은 미니앱으로 유도하는 방침의 집행 지점이 여기다. 데스크톱 설치 칩은 그대로 둔다:
 * 그 바탕화면 바로가기는 이 방침에서 버릴 것이 아니라 남는 자산이다.
 *
 * @param {string} platform - detectPlatform 반환값
 * @param {boolean} dismissed - 침묵 활성 여부
 * @returns {'hidden'|'prompt'|'miniapp'}
 */
export function decideChip(platform, dismissed) {
    if (platform === 'standalone') return 'hidden';
    if (dismissed) return 'hidden';
    if (platform === 'ios' || platform === 'android') return 'miniapp';
    if (platform === 'desktop-chromium') return 'prompt';
    return 'hidden';
}

/** 미니앱 딥링크 — 앱인토스 콘솔 실측값. 토스 앱이 이 스킴으로 미니앱 화면까지 직접 연다. */
export const MINIAPP_SCHEME = 'intoss://booktimer/';

/** 설정의 토스 연결 카드 — 연결 코드 발급과 「토스 앱 열기」가 한자리에 있다. */
export const TOSS_LINK_ANCHOR = '/settings#toss-link';

/**
 * 미니앱 칩을 눌렀을 때 갈 곳.
 *
 * <p>⚠️ <b>로그인 상태에서 딥링크로 바로 보내면 안 된다.</b> 미니앱에서 토스로 로그인하는 순간 별개의
 * 계정이 만들어져 기록이 둘로 갈리고, {@code toss_user_key}는 once-set 불변이라 사후에 합칠 수 없다.
 * 그래서 로그인 상태에서는 연결 코드를 먼저 받는 설정 카드로 보낸다 — 탭 한 번이 더 들지만
 * 되돌릴 수 없는 사고를 막는다. 비로그인은 계정 자체가 없으므로 갈릴 것도 없어 바로 보낸다.
 *
 * @param {boolean} loggedIn - 로그인 여부
 * @returns {string} 이동할 주소
 */
export function miniappTarget(loggedIn) {
    return loggedIn ? TOSS_LINK_ANCHOR : MINIAPP_SCHEME;
}

/**
 * head의 {@code <meta name="bt-auth">} 값으로 로그인 여부를 읽는다.
 *
 * <p>두 목적지의 실패 대가가 <b>대칭이 아니다</b> — 로그인인데 비로그인으로 읽으면 딥링크로 직행해
 * 계정이 갈리고(되돌릴 수 없다), 비로그인인데 로그인으로 읽으면 설정을 거쳐 로그인 화면을 볼 뿐이다
 * (되돌릴 수 있다). 그래서 <b>「anon」이라고 명시됐을 때만 비로그인</b>으로 보고, meta가 없거나 값이
 * 낯설면 로그인 쪽으로 기운다.
 *
 * @param {string|null|undefined} metaContent - meta[name=bt-auth]의 content (없으면 null)
 * @returns {boolean} true = 로그인으로 취급(설정 경유)
 */
export function isLoggedInFromMeta(metaContent) {
    return metaContent !== 'anon';
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
        // miniapp: 즉시 표시 — 딥링크라 브라우저 설치 이벤트를 기다릴 것이 없다.
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

    // 모드에 따라 하는 말이 다르다 — 데스크톱은 설치, 모바일은 미니앱.
    const labelText = mode === 'miniapp' ? '토스 앱에서 이어보기' : '홈 화면에 추가';

    const chip = document.createElement('div');
    chip.id = 'pwa-install-chip';
    chip.setAttribute('role', 'group');
    chip.setAttribute('aria-label', labelText);
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
    label.textContent = labelText;
    label.setAttribute('aria-label', labelText);
    label.style.cssText = [
        'background:none',
        'border:none',
        'color:inherit',
        'font:inherit',
        'cursor:pointer',
        'padding:0',
        'white-space:nowrap',
    ].join(';');

    // 닫기 버튼
    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.setAttribute('aria-label', '안내 닫기');
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
        } else if (mode === 'miniapp') {
            // 로그인 여부는 서버가 head에 심는 meta 하나로 안다(pwa-head.html) — 전역 모델 속성도
            // 추가 DB 조회도 없다. 판정은 isLoggedInFromMeta가 안전한 쪽으로 기울여 준다.
            const meta = document.querySelector('meta[name="bt-auth"]');
            window.location.href = miniappTarget(isLoggedInFromMeta(meta && meta.content));
        }
    });

    // 닫기 동작 — 7일 침묵
    closeBtn.addEventListener('click', () => {
        localStorage.setItem(DISMISS_KEY, String(Date.now()));
        chip.remove();
    });
}

// iOS 수동 설치 안내 오버레이(_showIosOverlay)는 걷었다 — iOS는 이제 설치가 아니라 미니앱으로 가고,
// 그 유도는 칩 한 번의 탭으로 끝나 「공유 → 홈 화면에 추가 → 추가」 3단 안내가 설 자리가 없다.
// 덤으로 화면 하단을 덮던 시트가 사라졌다(웹에도 T-183의 「덮지 않는다」를 적용).
