// 되돌릴 수 없는 폼(삭제·탈퇴)에 확인 대화상자를 건다.
//
// 예전엔 폼마다 onsubmit="return confirm('…')" 이었는데, CSP(script-src)가 인라인 이벤트 핸들러를
// 실행하지 않는다 — nonce를 붙일 수 있는 곳이 없어서 'unsafe-hashes' 없이는 방법이 아예 없다.
// 그대로 뒀다면 확인 없이 곧바로 삭제되는, 화면상 아무 표시도 없는 동작 변화가 됐을 것이다.
//
// 그래서 위임 리스너 하나로 옮겼다. 폼에는 data-confirm="문구" 만 남는다.
// 캡처 단계(true)에서 잡아 다른 submit 핸들러보다 먼저 판단한다.
document.addEventListener('submit', function (event) {
  var form = event.target;
  if (!form || typeof form.getAttribute !== 'function') return;
  var message = form.getAttribute('data-confirm');
  if (message && !window.confirm(message)) {
    event.preventDefault();
    event.stopPropagation();
  }
}, true);
