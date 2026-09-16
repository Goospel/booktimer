// 양옆 세로 바(side rails) 동작 — 빌드를 타지 않는 정적 ESM(`theme.js`와 같은 꼴).
// 마크업·활성·모드는 SSR(`fragments/side-rails`)이 그리고, 여기는 접힘/펼침만 맡는다.
// 설계: claude-docs/plans/2026-09-15-web-side-rails.md §2-3 · §4-④.
//
// 판별은 입력 장치다: 마우스(hover+fine) = 머물면 펼침, 그 외(태블릿) = 접힌 바 첫 탭은 펼치기만.
// 펼친 모양은 CSS `.rail.is-open`·`.rail:focus-within` 한 벌이다. JS가 없으면 접힌 채(라벨 있는 링크)로 동작한다.

export const HOVER_QUERY = '(hover: hover) and (pointer: fine)';
export const ENTER_DELAY_MS = 150; // 이만큼 머물러야 펼친다 — 스치면 안 펼쳐짐
export const LEAVE_DELAY_MS = 100;
export const MODE_KEY = 'booktimer.timerMode'; // timerMode.ts와 따로 적힌다 — 동기는 timer-mode.test.ts

/**
 * 홈이 열릴 모드를 기억한다 — #side-rails[data-remember]가 있을 때만(활성 키 있는 비홈 페이지).
 * 「로고가 홈이고, 홈은 내가 있던 바의 타이머로 열린다」. 저장 실패는 삼킨다.
 */
export function rememberMode(root, storage) {
    const mode = root.dataset.remember;
    if (mode !== 'reading' && mode !== 'study') return;
    try { storage?.setItem(MODE_KEY, mode); } catch { /* 사파리 프라이빗 — 오늘 동작으로 퇴화 */ }
}

/** 바 동작 배선. #side-rails가 없으면 null(바 없는 페이지에선 아무 일도 안 함). */
export function bindRails(doc, win) {
    const root = doc.getElementById('side-rails');
    if (!root) return null;
    // win.localStorage 접근 자체가 throw하는 환경(쿠키 차단)까지 여기서 삼킨다.
    try { rememberMode(root, win.localStorage); } catch { /* 기억만 못 할 뿐 펼침은 계속 */ }
    const rails = Array.from(root.querySelectorAll('.rail'));
    const mq = win.matchMedia(HOVER_QUERY);
    let timer = null;
    const collapse = () => {
        win.clearTimeout(timer);
        rails.forEach((r) => r.classList.remove('is-open'));
    };
    // :focus-within이 펼침을 붙잡지 않게 푼다 — 단 그 영역 안의 포커스만. 마우스가 스쳐 간 바 때문에
    // 다른 바에 둔 키보드 포커스가 body로 튀면 안 된다(#1137 리뷰).
    const blurIn = (el) => {
        if (el.contains(doc.activeElement)) doc.activeElement.blur();
    };
    const close = () => {
        collapse();
        blurIn(root);
    };

    rails.forEach((rail) => {
        // 데스크톱 머묾 — CSS transition-delay로는 안의 가로 배치가 즉시 뒤집혀 스침이 보인다(§2-3 정정)
        rail.addEventListener('mouseenter', () => {
            if (!mq.matches) return; // 터치 탭이 흉내 내는 mouseenter는 무시
            win.clearTimeout(timer);
            timer = win.setTimeout(() => {
                collapse(); // 다른 바는 닫되 포커스는 건드리지 않는다
                rail.classList.add('is-open');
            }, ENTER_DELAY_MS);
        });
        rail.addEventListener('mouseleave', () => {
            if (!mq.matches) return;
            win.clearTimeout(timer);
            timer = win.setTimeout(() => {
                collapse();
                blurIn(rail); // 막 떠난 이 바 안의 포커스만
            }, LEAVE_DELAY_MS);
        });
        rail.addEventListener('click', (e) => {
            if (mq.matches) return; // 데스크톱: 링크는 그대로 이동
            if (rail.classList.contains('is-open')) return; // 펼친 바의 링크 = 이동
            e.preventDefault(); // 접힌 바: 펼치기만
            close();
            rail.classList.add('is-open');
        });
    });
    doc.addEventListener('click', (e) => {
        if (!root.contains(e.target)) close();
    });
    doc.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') close();
    });
    mq.addEventListener?.('change', close); // 마우스를 붙이거나 떼면 열린 상태를 정리(옛 Safari<14엔 없다)
    return { close };
}

if (typeof document !== 'undefined') bindRails(document, window);
