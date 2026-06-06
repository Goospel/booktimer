/*
 * BookTimer 내 책장 — 무한 스크롤(클라이언트 사이드).
 *
 * 서버는 책장의 책을 전부 한 번에 렌더한다(상태 필터는 서버사이드 ?status=).
 * 책이 많으면 리스트가 길어져 하단 네비(← 대시보드 등)까지 스크롤이 멀어진다.
 * 그래서 처음엔 BATCH개만 보이게 숨기고, 리스트 끝의 센티넬이 화면에 들어오면
 * (스크롤이 끝에 닿으면) 다음 BATCH개를 펼친다 — 숫자 페이지(1·2·3) 대신 스크롤로 넘긴다.
 *
 * 왜 클라이언트 사이드인가: 개인 책장은 보통 수십 권 규모라 전체를 이미 받아온다.
 * 서버 왕복 없이 즉시 펼쳐져 모바일에서 스크롤이 빠르다(사용자 요구). 표지 img는
 * loading="lazy"라 숨겨진(=화면 밖) 책의 표지는 펼쳐지기 전엔 받지 않는다.
 *
 * 폴백: JS가 꺼져 있으면 이 스크립트가 안 돌아 전체가 그대로 보인다(기존 동작).
 */
(function () {
    const BATCH = 5; // 처음 표시 수 = 이후 한 번에 펼치는 수

    const list = document.getElementById('shelf-list');
    if (!list) return; // 빈 책장 등 리스트 자체가 없으면 할 일 없음

    const items = Array.from(list.querySelectorAll('.book-row'));
    if (items.length <= BATCH) return; // 적으면 무한 스크롤 불필요 — 그대로 둔다

    // IntersectionObserver 미지원(아주 옛 브라우저)이면 전체 표시로 폴백.
    if (!('IntersectionObserver' in window)) return;

    let shown = BATCH;
    items.forEach((li, i) => { if (i >= shown) li.hidden = true; });

    // 센티넬: 리스트 바로 뒤에 둔다. 숨긴 항목은 자리를 차지하지 않으므로
    // 센티넬은 '마지막으로 보이는 책' 바로 아래에 위치한다.
    const sentinel = document.createElement('div');
    sentinel.className = 'shelf-sentinel';
    sentinel.setAttribute('aria-hidden', 'true');
    list.after(sentinel);

    function revealMore() {
        const next = Math.min(shown + BATCH, items.length);
        for (let i = shown; i < next; i++) items[i].hidden = false;
        shown = next;
        if (shown >= items.length) {
            observer.disconnect();
            sentinel.remove();
        }
    }

    // rootMargin으로 끝에 닿기 조금 전에 미리 펼쳐 끊김을 줄인다.
    const observer = new IntersectionObserver((entries) => {
        if (entries.some((e) => e.isIntersecting)) revealMore();
    }, { rootMargin: '160px 0px' });

    observer.observe(sentinel);
})();
