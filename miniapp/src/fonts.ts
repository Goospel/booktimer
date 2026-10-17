import { whenPageLoaded } from './screens/GuestHome';

/**
 * 웹폰트 CSS — 에디토리얼(D1=③): **두 패밀리 세 face뿐이다** — Noto Serif KR 500(제목·값·이니셜) +
 * Noto Sans KR 400·500(본문·강조). 굵기 축이 400·500뿐이라(`font-synthesis: none`) 다른 굵기는 싣지 않는다
 * (fonts.test가 `family=` 둘을 센다 — 셋째가 몰래 실리면 첫 화면 조각·바이트가 는다).
 */
export const FONT_CSS_URL =
  'https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@500&family=Noto+Sans+KR:wght@400;500&display=swap';

/**
 * load 뒤 `<link id="bt-webfonts" rel="stylesheet">`를 한 번 붙인다(이미 있으면 안 붙인다). 정리 함수를 돌려준다.
 *
 * <p>예전엔 `global.css` 맨 앞 `@import`였다 — 빌드 CSS가 그 응답을 기다리고, 모듈 스크립트는 그 CSS를
 * 기다려서 **구글 폰트 CSS 응답이 앱 시작의 선행 조건**이었다(합성 스파이크: 4초 지연 → 스크립트 실행 4,056ms).
 * load 뒤로 미뤄도 첫 방문의 「대체 글꼴 → 웹폰트」 교체(`display=swap`)는 예전에도 있었다(콜드 실측: main FCP 440ms ·
 * (당시) 고운돋움 loaded 674ms). 새로 생길 수 있는 것은 **재방문(캐시 있음)** 교체다 — 예전엔 CSS가 첫 렌더 전에 face를 선언했지만
 * 지금은 load가 와야 선언하므로, 폰에서 첫 페인트가 load보다 앞서면 실행할 때마다 바뀔 수 있다(실기기 미검증 — 설계 D8 폰 확인).
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
