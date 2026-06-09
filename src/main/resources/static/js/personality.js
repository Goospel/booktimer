// 책BTI 캐러셀 — 데스크탑(마우스) 드래그로 좌우 넘기기.
// 모바일/펜은 네이티브 터치 스크롤이 이미 동작하므로 가로채지 않는다(pointerType 'mouse'만).
// scroll-snap-type:x mandatory 는 그대로 — 놓는 순간 가장 가까운 카드 중앙으로 탄력 스냅.
(function () {
  var track = document.querySelector('.personality-carousel');
  if (!track) return;

  var DRAG_THRESHOLD = 6;            // 이 미만 이동은 '클릭'으로 간주(카드 안 버튼/링크 보호)
  var down = false, dragging = false, startX = 0, startLeft = 0, pid = null;

  track.addEventListener('pointerdown', function (e) {
    if (e.pointerType !== 'mouse') return;     // 터치/펜: 네이티브 스크롤에 맡김
    down = true; dragging = false;
    startX = e.clientX; startLeft = track.scrollLeft; pid = e.pointerId;
    // 여기서 setPointerCapture 안 함 — 즉시 캡처하면 카드 안 버튼 클릭을 가로채 버림.
  });

  track.addEventListener('pointermove', function (e) {
    if (!down) return;
    var dx = e.clientX - startX;
    if (!dragging) {
      if (Math.abs(dx) < DRAG_THRESHOLD) return;  // 아직 클릭일 수 있음
      dragging = true;
      track.classList.add('is-dragging');         // cursor:grabbing + user-select:none
      track.style.scrollBehavior = 'auto';        // 드래그 중엔 손 따라 1:1 — CSS의 smooth가 scrollLeft 대입을 애니메이션화하는 것 차단
      track.setPointerCapture(pid);               // 드래그 확정 후에만 캡처
    }
    e.preventDefault();
    track.scrollLeft = startLeft - dx;            // 손 따라 스크롤(놓으면 snap이 정리)
  });

  function end() {
    if (!down) return;
    down = false;
    if (dragging) {
      track.classList.remove('is-dragging');
      track.style.scrollBehavior = '';            // CSS smooth 복원 — 놓는 순간 가장 가까운 카드 중앙으로 탄력 스냅

      // 드래그 직후 발생하는 click을 1회 흡수 — '대표 선택' 버튼/프로필 링크 오발동 방지.
      var swallow = function (ev) { ev.stopPropagation(); ev.preventDefault(); };
      track.addEventListener('click', swallow, { capture: true, once: true });
      // click이 안 오는 경우(빈 영역에서 끝) 대비 — 다음 틱에 해제해 이후 정상 클릭은 안 먹게.
      setTimeout(function () { track.removeEventListener('click', swallow, true); }, 0);
    }
    dragging = false; pid = null;
  }
  track.addEventListener('pointerup', end);
  track.addEventListener('pointercancel', end);
})();
