import { TDSMobileProvider } from '@toss/tds-mobile';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App';

/** TDS가 요구하는 플랫폼 변수 — WebView라 UA로 충분하다(폰트 배율은 TDS 기본값에 맡긴다). */
const userAgent = {
  fontA11y: undefined,
  fontScale: undefined,
  isAndroid: /android/i.test(navigator.userAgent),
  isIOS: /iphone|ipad|ipod/i.test(navigator.userAgent),
};

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <TDSMobileProvider userAgent={userAgent}>
      <App />
    </TDSMobileProvider>
  </StrictMode>,
);
