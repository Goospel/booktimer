import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 미니앱은 토스 인프라(앱인토스 CLI/콘솔)로 배포된다 — 우리 서버 static 번들(frontend/)과 무관.
export default defineConfig({
  plugins: [react()],
  // 5174가 아닌 이유: 이 개발 PC(Windows)에서 5174가 OS 예약 포트 구간에 걸려 바인딩이 실패했다(T-197).
  // 여기가 포트의 **단일 출처**다 — `screenshots/shoot.mjs`의 기본값과 `.claude/launch.json`(gitignore
  // 대상이라 머신마다 다르다)이 이 값을 따라간다. 바꾸려면 저 셋을 함께 본다.
  server: { port: 5300 },
});
