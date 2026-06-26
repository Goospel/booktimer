import { test as setup, expect } from '@playwright/test';

// 인증 세션을 1회 만들어 저장 → garden.spec 등 인증 필요한 스펙이 재사용한다(매번 로그인 안 함).
// 시드계정은 LocalTestAccountSeeder(@Profile("local"))가 bootRun 때 멱등 생성한다.
const authFile = 'e2e/.auth/user.json';

setup('시드 계정으로 로그인해 세션을 저장한다', async ({ page }) => {
  await page.goto('/login');
  await page.fill('#username', 'testid');
  await page.fill('#password', '1234qwer!!');
  await page.click('button[type=submit]');

  // 성공 시 대시보드(/ 또는 /dashboard)로 — Vue 마운트 앵커로 확인(브리틀한 문구 단언 회피).
  await expect(page.locator('#dashboard-app')).toBeVisible();

  await page.context().storageState({ path: authFile });
});
