import { test, expect } from '@playwright/test';

// 정원(마을) 저장 플로 — 헤드리스로 못 잡던 런타임 회귀(#358 reactive proxy · #364 defer/TDZ)가
// 몰린 지점. 핵심은 "꾸미기 진입 시 Phaser 편집 캔버스가 마운트되고, 저장이 200, 콘솔 에러 0".
// canvas는 직접 단언할 수 없으므로 layout API 응답 가로채기 + 콘솔/페이지 에러 수집으로 대체 검증한다.
test('마을 진입 → 꾸미기 → 저장 시 layout API가 200이고 콘솔 에러가 없다', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));

  // 마을 진입 — Vue 앱이 /api/garden을 fetch해 HUD를 렌더(데이터 로드 앵커).
  await page.goto('/village');
  await expect(page.locator('.village-hud-greeting')).toBeVisible({ timeout: 15_000 });

  // 꾸미기 진입 — startEdit()가 기존 Phaser를 파괴하고 편집 캔버스를 새로 마운트한다(회귀 지점).
  await page.getByRole('button', { name: /꾸미기/ }).click();
  await expect(page.locator('#garden-phaser')).toBeVisible();

  const saveBtn = page.locator('.garden-edit-btn.primary');
  await expect(saveBtn).toBeEnabled();

  // 저장 — POST /api/garden/layout 응답을 가로채 200 확인(성공 시 앱은 reload).
  const saveResp = page.waitForResponse(
    (r) => r.url().includes('/api/garden/layout') && r.request().method() === 'POST',
  );
  await saveBtn.click();
  const resp = await saveResp;
  expect(resp.status()).toBe(200);

  // 진입~저장까지 앱 로직 콘솔 에러 0(Phaser/WebGL 무해 경고는 error 타입이 아님).
  expect(errors).toEqual([]);
});
