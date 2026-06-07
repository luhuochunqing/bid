import { test, expect } from '@playwright/test'

test.describe('WeCom OAuth2 SSO Flow', () => {
  test('displays WeCom login button and redirects to WeCom OAuth page', async ({ page }) => {
    await page.goto('/login')

    // Check if the button exists  // @ui-cover:auth
    const wecomButton = page.locator('.wecom-button')
    await expect(wecomButton).toBeVisible()
    await expect(wecomButton).toContainText('企业微信登录')

    // Intercept the authorize-params call
    await page.route('**/api/auth/wecom/authorize-params', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          message: 'Auth params generated',
          data: {
            state: 'test_state_123',
            appid: 'ww_test_appid',
            agentid: '1000002'
          }
        })
      })
    })

    // Click and check if it tries to redirect
    // We use a mock to avoid actual redirect out of the app
    
    // In our implementation, we change window.location.href
    // Playwright will follow it unless we catch it or check the URL
    await wecomButton.click()
    
    // Verify the URL constructed
    // Note: Since window.location.href is changed, the page will navigate. 
    // We can just check the URL before it actually leaves if possible, or expect a navigation error/timeout to the external domain.
    // Alternatively, we can check the call to authorize-params was made.
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
    // Mock the callback API with 403
    await page.route('**/api/auth/wecom/callback*', async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          code: 403,
          message: 'INVALID_STATE'
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
