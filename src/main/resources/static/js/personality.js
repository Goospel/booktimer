// 책BTI 캐러셀 — 데스크탑 좌우 화살표 버튼으로 한 칸씩 넘기기.
// 모바일/터치는 네이티브 스와이프를 그대로 쓰고 버튼은 CSS(@media pointer:coarse)로 숨긴다.
// scroll-snap-type:x mandatory 가 scrollBy 후 가장 가까운 카드 중앙으로 정렬한다.
(function () {
  var wrap = document.querySelector('.personality-carousel-wrap');
  if (!wrap) return;
  var track = wrap.querySelector('.personality-carousel');
  var prev = wrap.querySelector('.carousel-nav-prev');
  var next = wrap.querySelector('.carousel-nav-next');
  if (!track || !prev || !next) return;

  // 한 칸 = 카드 폭 + gap. (flex-basis 80% + 첫/끝 margin 덕에 step 만큼 밀면 다음 카드가 정확히 중앙)
  function step() {
    var slide = track.querySelector('.personality-slide');
    if (!slide) return track.clientWidth;
    var gap = parseFloat(getComputedStyle(track).columnGap) || 0;
    return slide.getBoundingClientRect().width + gap;
  }

  // behavior 생략 → CSS scroll-behavior 따름(smooth, reduced-motion 시 auto). JS 미디어쿼리 불필요.
  prev.addEventListener('click', function () { track.scrollBy({ left: -step() }); });
  next.addEventListener('click', function () { track.scrollBy({ left:  step() }); });

  // 양끝/단일 카드 상태 반영 — 끝이면 해당 버튼 dim, 넘칠 게 없으면 버튼 자체 숨김.
  function sync() {
    var overflow = track.scrollWidth - track.clientWidth;
    if (overflow <= 1) { wrap.classList.add('nav-hidden'); return; }
    wrap.classList.remove('nav-hidden');
    prev.disabled = track.scrollLeft <= 1;
    next.disabled = track.scrollLeft >= overflow - 1;
  }

  var ticking = false;
  track.addEventListener('scroll', function () {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(function () { ticking = false; sync(); });
  });
  window.addEventListener('resize', sync);
  sync();
})();
