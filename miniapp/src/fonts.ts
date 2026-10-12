import { whenPageLoaded } from './screens/GuestHome';

/**
 * 웹폰트 CSS — 웹 app.css와 본문·세리프를 같은 패밀리로 맞춘다. **두 벌뿐이다**: 고운돋움(본문) +
 * 고운바탕(값·제목·인용·이니셜). 손글씨(개구)는 톤 조율 A(2026-09-24)에서 걷었다(warm-palette.test).
 */
export const FONT_CSS_URL =
  'https://fonts.googleapis.com/css2?family=Gowun+Batang:wght@700&family=Gowun+Dodum&display=swap';

/**
 * load 뒤 `<link id="bt-webfonts" rel="stylesheet">`를 한 번 붙인다(이미 있으면 안 붙인다). 정리 함수를 돌려준다.
 *
 * <p>예전엔 `global.css` 맨 앞 `@import`였다 — 빌드 CSS가 그 응답을 기다리고, 모듈 스크립트는 그 CSS를
 * 기다려서 **구글 폰트 CSS 응답이 앱 시작의 선행 조건**이었다(합성 스파이크: 4초 지연 → 스크립트 실행 4,056ms).
 * load 뒤로 미루면 첫 화면은 대체 글꼴로 먼저 서고, 첫 방문에 한 번 고운 글꼴로 바뀐다(`display=swap`).
 */
export function loadWebFonts(doc: Document, win: Window): () => void {
  return whenPageLoaded(doc, win, () => {
    if (doc.getElementById('bt-webfonts')) return;
    const link = doc.createElement('link');
    link.id = 'bt-webfonts';
    link.rel = 'stylesheet';
    link.href = FONT_CSS_URL;
    doc.head.appendChild(link);
  });
}
