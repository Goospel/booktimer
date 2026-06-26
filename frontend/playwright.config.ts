import { defineConfig, devices } from '@playwright/test';

// BookTimer E2E — 로컬 수동 실행 모델.
// 전제: 별도 터미널에서 `./gradlew bootRun`(local 프로파일 + MySQL docker)이 8080에 떠 있어야 한다.
//   - local 프로파일이 LocalTestAccountSeeder로 시드계정 testid를 멱등 생성한다.
//   - webServer를 두지 않는 이유: Spring 기동(+docker)은 무겁고 워크트리·포트가 얽혀,
//     "이미 띄운 서버에 붙는다"가 로컬 수동의 정직한 형태다. 서버가 없으면 ECONNREFUSED로 명확히 실패한다.
// vitest(test/**)와 분리: vite.config.ts의 test.include가 test/만 잡고, 이 러너는 e2e/만 잡는다.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // 단일 bootRun 서버를 공유하므로 순차 실행이 안전
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: 'list',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: 'http://localhost:8080',
    trace: 'on-first-retry',
    viewport: { width: 1280, height: 720 },
  },
  projects: [
    // 1) 로그인해 세션(storageState)을 만든다 — 인증이 필요한 스펙들의 선행 의존.
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    // 2) 실제 스펙 — 저장된 세션을 기본으로 깐다. (auth.spec.ts는 콜드 세션으로 override)
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], storageState: 'e2e/.auth/user.json' },
      dependencies: ['setup'],
      testIgnore: /auth\.setup\.ts/,
    },
  ],
});
