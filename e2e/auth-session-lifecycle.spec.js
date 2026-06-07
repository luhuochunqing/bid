// @ui-cover:dashboard
import { test, expect } from '@playwright/test'

const expectEmptyAxiosPostBody = (request) => {
  expect([null, 'null']).toContain(request.postData())
}

async function seedSession(page) {
  await page.addInitScript(() => {
    localStorage.clear()
    sessionStorage.clear()
    sessionStorage.setItem('token', 'expired-access-token')
    sessionStorage.setItem('refreshToken', 'refresh-token-initial')
  })
}

test.describe('auth session lifecycle', () => {
  test('refreshes session after a 401 and retries current-user request', async ({ page }) => {
    let meCallCount = 0

    await seedSession(page)

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

      const authHeader = route.request().headers().authorization
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
            role: 'ADMIN'
          }
        }),
        headers: {
          'x-observed-authorization': authHeader || ''
        }
      })
    })

    await page.route('**/api/auth/refresh', async (route) => {
      expectEmptyAxiosPostBody(route.request())

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            token: 'access-token-rotated',
            refreshToken: 'refresh-token-rotated',
            username: 'alice',
            fullName: 'Alice',
            email: 'alice@example.com',
            role: 'ADMIN'
          }
        })
      })
    })

    await page.goto('/dashboard')

    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.getByText('工作台').first()).toBeVisible()

    const storageState = await page.evaluate(() => ({
      localToken: localStorage.getItem('token'),
      sessionToken: sessionStorage.getItem('token'),
      sessionUser: sessionStorage.getItem('user')
    }))

    expect(storageState.localToken).toBe('access-token-rotated')
    expect(storageState.sessionUser).toContain('alice')
    expect(storageState.sessionToken).toBe('expired-access-token')
  })

  test('logout sends refresh token and clears local session state', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.clear()
      sessionStorage.clear()
      sessionStorage.setItem('token', 'access-token-live')
      sessionStorage.setItem('refreshToken', 'refresh-token-live')
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
            role: 'ADMIN'
          }
        })
      })
    })

    await page.route('**/api/auth/logout', async (route) => {
      expectEmptyAxiosPostBody(route.request())
      expect(route.request().headers().authorization).toBe('Bearer access-token-live')

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

    const storageState = await page.evaluate(() => ({
      token: sessionStorage.getItem('token'),
      user: sessionStorage.getItem('user')
    }))

    expect(storageState.token).toBeNull()
    expect(storageState.user).toBeNull()
  })
})
