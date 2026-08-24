import { test, expect } from '@playwright/test'

test.describe('WeCom OAuth2 SSO Flow', () => {
  test('login page intentionally hides WeCom entry (fedbfcc56)', async ({ page }) => {
    await page.goto('/login')

    // 企业微信登录入口已按 fedbfcc56 有意下线（SocialLogin 组件不再挂载）；
    // OAuth 回调链路（code+state → /api/auth/wecom/callback）仍保留，见下方回调用例
    await expect(page.locator('.wecom-button')).toHaveCount(0)
  })

  test('handles successful WeCom callback', async ({ page }) => {
    // Mock the callback API
    await page.route('**/api/auth/wecom/callback*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          message: 'WeCom login successful',
          data: {
            token: 'fake-jwt-token',
            refreshToken: 'fake-refresh-token',
            username: 'wecom_user',
            fullName: 'WeCom User',
            email: 'wecom@example.com',
            role: 'USER'
          }
        })
      })
    })

    // Navigate to login with callback params
    await page.goto('/login?code=test_code&state=test_state')

    // Should redirect to dashboard
    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.getByText('工作台').first()).toBeVisible()
  })

  test('handles WeCom not bound error', async ({ page }) => {
    // Mock the callback API with 40101 (Not bound)
    await page.route('**/api/auth/wecom/callback*', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          code: 40101,
          message: 'WECOM_NOT_BOUND'
        })
      })
    })

    // Navigate to login with callback params
    await page.goto('/login?code=test_code&state=test_state')

    // Should stay on login page and show warning
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText('您的企业微信账号尚未绑定系统账号')).toBeVisible()
  })

  test('handles state validation failure', async ({ page }) => {
    // Mock the callback API with 403（msg 字段才是前端透传的错误文案）
    await page.route('**/api/auth/wecom/callback*', async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          code: 403,
          msg: 'INVALID_STATE'
        })
      })
    })

    // Navigate to login with callback params
    await page.goto('/login?code=test_code&state=wrong_state')

    // Should show error and stay on login
    const errorText = page.getByText(/INVALID_STATE|企业微信登录失败/)
    await expect(errorText).toBeVisible()
  })
})
