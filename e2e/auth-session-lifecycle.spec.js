// @ui-cover:dashboard
import { test, expect } from '@playwright/test'

test.describe('auth session lifecycle', () => {
  test('refreshes session after a 401 and retries current-user request', async ({ page }) => {
    let meCallCount = 0

    // H13: 用户 hint 存储在 storage，token 走 HttpOnly cookie
    await page.addInitScript(() => {
      localStorage.clear()
      sessionStorage.clear()
      sessionStorage.setItem('user', JSON.stringify({
        id: 1,
        name: 'Alice',
        username: 'alice',
        email: 'alice@example.com',
        role: 'bid_admin'
      }))
    })

    await page.route('**/api/auth/me', async (route) => {
      meCallCount += 1
      if (meCallCount === 1) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ success: false, message: 'expired' })
        })
        return
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            id: 1,
            username: 'alice',
            fullName: 'Alice',
            email: 'alice@example.com',
            role: 'bid_admin',
            roleCode: 'bid_admin',
            menuPermissions: ['all']
          }
        })
      })
    })

    // H13: refresh 返回 Set-Cookie 更新 access_token，body 不含 token
    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: {
          'set-cookie': 'access_token=rotated-token; Path=/; HttpOnly; SameSite=Lax'
        },
        body: JSON.stringify({
          success: true,
          data: {
            id: 1,
            username: 'alice',
            fullName: 'Alice',
            email: 'alice@example.com',
            role: 'bid_admin',
            roleCode: 'bid_admin',
            menuPermissions: ['all']
          }
        })
      })
    })

    await page.goto('/dashboard')

    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.getByText('工作台').first()).toBeVisible()

    // H13: 只验证 user hint，不再检查 token（token 走 HttpOnly cookie）
    const storageState = await page.evaluate(() => ({
      sessionUser: sessionStorage.getItem('user') || localStorage.getItem('user')
    }))

    expect(storageState.sessionUser).toContain('alice')
  })

  test('logout sends request and clears local session state', async ({ page }) => {
    // H13: 用户 hint 存储在 storage，token 走 HttpOnly cookie
    await page.addInitScript(() => {
      localStorage.clear()
      sessionStorage.clear()
      sessionStorage.setItem('user', JSON.stringify({
        id: 1,
        name: 'Alice',
        username: 'alice',
        email: 'alice@example.com',
        role: 'admin'
      }))
    })

    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            id: 1,
            username: 'alice',
            fullName: 'Alice',
            email: 'alice@example.com',
            role: 'bid_admin',
            roleCode: 'bid_admin',
            menuPermissions: ['all']
          }
        })
      })
    })

    // H13: logout 使用 cookie 认证，不需要检查 Authorization header
    await page.route('**/api/auth/logout', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, message: 'Logout successful' })
      })
    })

    await page.goto('/dashboard')
    await page.locator('.user-info').click()
    await page.getByRole('menuitem', { name: /退出登录/ }).click()

    await expect(page).toHaveURL(/\/login$/)

    // H13: 验证 user hint 被清除
    const storageState = await page.evaluate(() => ({
      user: sessionStorage.getItem('user') || localStorage.getItem('user'),
      token: sessionStorage.getItem('token') || localStorage.getItem('token')
    }))

    expect(storageState.user).toBeNull()
    expect(storageState.token).toBeNull()
  })
})
