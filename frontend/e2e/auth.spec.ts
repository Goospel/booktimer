import { test, expect } from '@playwright/test';

// 로그인 폼 자체를 검증하므로 저장된 세션을 비운다(콜드 세션).
test.use({ storageState: { cookies: [], origins: [] } });

test.describe('폼 로그인', () => {
  test('올바른 자격증명이면 대시보드로 들어간다', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#username', 'testid');
    await page.fill('#password', '1234qwer!!');
    await page.click('button[type=submit]');

    await expect(page.locator('#dashboard-app')).toBeVisible();
    await expect(page).toHaveURL(/\/(dashboard)?$/);
  });

  // 변별력 — "아무 입력이나 통과"가 아님을 못 박는 음성 테스트.
  test('비밀번호가 틀리면 로그인 실패로 되돌아간다', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#username', 'testid');
    await page.fill('#password', 'wrong-password-xyz');
    await page.click('button[type=submit]');

    // Spring Security 기본 실패 리다이렉트(/login?error) + 로그인 폼이 다시 보임(대시보드로 안 넘어감).
    await expect(page).toHaveURL(/\/login\?error/);
    await expect(page.locator('#username')).toBeVisible();
  });
});
