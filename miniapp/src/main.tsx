import { TDSMobileProvider } from '@toss/tds-mobile';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App';
import './global.css';

/**
 * TDS가 요구하는 플랫폼 변수 — WebView라 UA로 충분하다(폰트 배율은 TDS 기본값에 맡긴다).
 *
 * `colorPreference`를 비워 두면 TDS가 시스템 `prefers-color-scheme`을 따라 다크로 적응하는데,
 * 이 앱은 크림색 라이트 고정 디자인이라 TDS만 다크로 가면 컴포넌트가 몰래 어두워진다
 * (실측 2026-08-13 — 목표 휠의 위아래 안개가 다크 배경색 `#202027` 박스로 떴다). 라이트로 못 박는다.
 */
const userAgent = {
  fontA11y: undefined,
  fontScale: undefined,
  isAndroid: /android/i.test(navigator.userAgent),
  isIOS: /iphone|ipad|ipod/i.test(navigator.userAgent),
  colorPreference: 'light' as const,
};

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <TDSMobileProvider userAgent={userAgent}>
      <App />
    </TDSMobileProvider>
  </StrictMode>,
);
