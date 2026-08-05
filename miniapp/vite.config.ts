import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 미니앱은 토스 인프라(앱인토스 CLI/콘솔)로 배포된다 — 우리 서버 static 번들(frontend/)과 무관.
export default defineConfig({
  plugins: [react()],
  server: { port: 5174 },
});
