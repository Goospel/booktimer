// 다크 테마 토글 — 빌드를 타지 않는 정적 ESM(`pwa-install.js`와 같은 꼴).
//
// 상태는 2단이다: 저장값이 정확히 'dark'면 다크, 그 외 전부 라이트.
// 시스템 설정(prefers-color-scheme)은 일부러 안 본다 — 요구가 「그때그때 켜고 끈다」이고,
// 첫 방문 기본이 다크가 되면 이 작업의 발단이 된 불만("사이트가 갑자기 어두워졌다")을 우리 손으로
// 재현하게 된다. 시스템 추종이 필요해지면 저장값 부재 시 matchMedia 한 줄이 붙는 자리다.
//
// 저장은 localStorage 한 키. 기기별 취향이라 서버에 안 올리고, 비로그인 화면(랜딩·로그인)까지
// 덮는 것도 localStorage뿐이다.
//
// ⚠️ 첫 페인트 전 적용은 이 모듈이 못 한다(type=module은 defer라 늦다). 그래서 head의
// `fragments/theme-head`가 같은 키를 인라인으로 한 번 더 읽는다 — 그 리터럴 동기는 themeHead.test.ts가 지킨다.

export const KEY = 'bt-theme';
export const DARK = 'dark';
export const LIGHT = 'light';

/** 저장값 → 테마. 'dark' 리터럴만 다크, 나머지(null·손상·대소문자 다름)는 전부 라이트. */
export function resolveTheme(stored) {
    return stored === DARK ? DARK : LIGHT;
}

export function nextTheme(current) {
    return current === DARK ? LIGHT : DARK;
}

/**
 * documentElement에 반영. 라이트는 속성을 **삭제**한다 — `data-theme="light"`로 남겨도 화면은 같지만,
 * 그러면 「속성 없음 = 라이트」라는 CSS의 기본 상태와 「속성 있음」이라는 두 표현이 생겨
 * 다음 사람이 `:root[data-theme="light"]`를 찾게 된다. 상태는 한 가지 모양으로만 둔다.
 */
export function applyTheme(doc, theme) {
    if (theme === DARK) doc.documentElement.dataset.theme = DARK;
    else delete doc.documentElement.dataset.theme;
}

/** 저장 — 사파리 프라이빗 등 접근 예외는 삼키고 false. 저장이 막혀도 그 세션의 전환은 된다. */
export function persist(storage, theme) {
    try {
        storage.setItem(KEY, theme);
        return true;
    } catch (e) {
        return false;
    }
}

export function readStored(storage) {
    try {
        return storage.getItem(KEY);
    } catch (e) {
        return null;
    }
}

// 달·해 라인아트 — 네비 아이콘과 같은 규격(24 뷰박스·stroke currentColor·굵기 1.6). 이모지를 쓰지 않는다.
const ICON = {
    dark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20.5 14.6A8.6 8.6 0 0 1 9.4 3.5a8.6 8.6 0 1 0 11.1 11.1z"/></svg>',
    light: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4.2"/><path d="M12 2.6v2.2M12 19.2v2.2M4.5 4.5l1.6 1.6M17.9 17.9l1.6 1.6M2.6 12h2.2M19.2 12h2.2M4.5 19.5l1.6-1.6M17.9 6.1l1.6-1.6"/></svg>',
};

/**
 * 헤더에 토글 버튼을 꽂는다. `<button>`이라 Tab·Space·Enter가 그냥 되고, 상태는 `aria-pressed`로
 * 읽힌다(스크린리더: 「다크 모드, 토글 버튼, 눌림」).
 *
 * <p>마크업을 36개 템플릿에 손으로 넣지 않는 이유: JS 없이는 어차피 저장·전환이 성립하지 않아
 * 서버가 그린 버튼은 동작하지 않는 장식이 된다. `pwa-install.js`가 칩을 주입하는 방식과 같다.
 */
export function mountToggle(doc, storage) {
    const host = doc.querySelector('header.brand, header.auth-brand');
    if (!host || doc.querySelector('.theme-toggle')) return null;

    let theme = resolveTheme(readStored(storage));
    applyTheme(doc, theme);

    const btn = doc.createElement('button');
    btn.type = 'button';
    btn.className = 'theme-toggle';

    const render = () => {
        btn.setAttribute('aria-pressed', String(theme === DARK));
        btn.setAttribute('aria-label', '다크 모드');
        btn.innerHTML = ICON[theme];
    };

    btn.addEventListener('click', () => {
        theme = nextTheme(theme);
        applyTheme(doc, theme);
        persist(storage, theme);
        render();
    });

    render();
    host.appendChild(btn);
    return btn;
}

// ⚠️ 자동 마운트(`mountToggle(document, localStorage)`)는 일부러 없다 — 이 PR은 다크런치다.
// 인프라(토큰·부트·강제 다크 차단)만 먼저 넣고, raw 색 160규칙이 토큰화되기 전에 토글을 켜면
// 다크 화면 곳곳에 크림색이 남는다. 점등은 그 소탕 PR에서 이 한 줄을 추가하는 것으로 끝난다.
