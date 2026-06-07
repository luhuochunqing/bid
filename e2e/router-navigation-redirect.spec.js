import { test, expect } from '@playwright/test'

/**
 * TDD Test: Router Navigation Redirect
 *
 * This test verifies that 401 errors trigger proper Vue Router navigation
 * instead of window.location.href, ensuring navigation guards work correctly.
 */

test.describe('router navigation redirect', () => {
  // Clear all storage before each test to ensure clean state  // @ui-cover:project,dashboard,auth
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.clear()
      sessionStorage.clear()
    })
  })

test.skip('should use router.push for login redirect on 401, not window.location.href', async ({ page }) => {
    // Seed initial session with complete user data to avoid restoreSession API call
    await page.addInitScript(() => {
      sessionStorage.setItem('token', 'expired-access-token')
      sessionStorage.setItem('refreshToken', 'refresh-token')
      sessionStorage.setItem('user', JSON.stringify({
        id: 1,
        name: 'Test User',
        username: 'testuser',
        email: 'test@example.com',
        role: 'admin',
        allowedProjectIds: [],  // Required for restoreSession fast path
        allowedDepts: []          // Required for restoreSession fast path
      }))
    })

    // Intercept refresh token call and make it fail (simulating complete auth failure)
    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, message: 'Refresh token expired' })
      })
    })

    // Intercept all API calls to return 401
    await page.route('**/api/**', async (route) => {
      const url = route.request().url()
      if (url.includes('/api/auth/refresh')) {
        // Let the refresh handler above deal with this
        route.continue()
      } else {
        // Return 401 for all other API calls
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ success: false, message: 'Unauthorized' })
        })
      }
    })

    // Inject spy before any app code runs
    await page.addInitScript(() => {
      window.routerPushCalled = false
      
      const checkAndPatch = () => {
        const app = window.__VUE_APP__
        if (app && app.config && app.config.globalProperties && app.config.globalProperties.$router) {
          const router = app.config.globalProperties.$router
          if (router._patched) return true
          
          const originalPush = router.push
          router.push = function (...args) {
            window.routerPushCalled = true
            return originalPush.apply(this, args)
          }
          router._patched = true
          return true
        }
        return false
      }

      // Poll until patched or timeout
      const start = Date.now()
      const timer = setInterval(() => {
        if (checkAndPatch() || Date.now() - start > 5000) clearInterval(timer)
      }, 100)
    })

    // Navigate to a protected route
    await page.goto('/dashboard')

    // Wait for redirect with generous timeout
    await expect(page).toHaveURL(/\/login$/, { timeout: 15000 })

    // Verify router.push was used
    const pushCalled = await page.evaluate(() => window.routerPushCalled)
    expect(pushCalled).toBe(true)

    // Verify session was cleared
    const storageState = await page.evaluate(() => ({
      token: sessionStorage.getItem('token'),
      user: sessionStorage.getItem('user')
    }))

    expect(storageState.token).toBeNull()
    expect(storageState.user).toBeNull()

    // Verify error message was shown
    await expect(page.getByText('登录已过期，请重新登录')).toBeVisible()
  })

  test('should not trigger redirect when already on login page', async ({ page }) => {
    // Go directly to login page
    await page.goto('/login')

    // Mock a 401 response
    await page.route('**/api/**', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, message: 'Unauthorized' })
      })
    })

    // Trigger an API call that would return 401
    await page.evaluate(() => {
      fetch('/api/auth/me').catch(() => {})
    })

    // After the 401 response is processed, verify we're still on login page
    await expect(page).toHaveURL(/\/login$/)
  })

  test('should preserve router navigation guards during redirect', async ({ page }) => {
    // Seed session with complete user data
    await page.addInitScript(() => {
      sessionStorage.setItem('token', 'will-expire-token')
      sessionStorage.setItem('refreshToken', 'will-expire-refresh')
      sessionStorage.setItem('user', JSON.stringify({
        id: 2,
        name: 'Another User',
        username: 'another',
        email: 'another@example.com',
        role: 'user',
        allowedProjectIds: [],
        allowedDepts: []
      }))
    })

    // Inject spy
    await page.addInitScript(() => {
      window.routerPushCalled = false
      const checkAndPatch = () => {
        const app = window.__VUE_APP__
        if (app && app.config && app.config.globalProperties && app.config.globalProperties.$router) {
          const router = app.config.globalProperties.$router
          if (router._patched) return true
          const originalPush = router.push
          router.push = function (...args) {
            window.routerPushCalled = true
            return originalPush.apply(this, args)
          }
          router._patched = true
          return true
        }
        return false
      }
      const start = Date.now()
      const timer = setInterval(() => {
        if (checkAndPatch() || Date.now() - start > 5000) clearInterval(timer)
      }, 100)
    })

    // Navigate to protected route
    await page.goto('/project')

    // Should end up on login
    await expect(page).toHaveURL(/\/login$/, { timeout: 15000 })

    // Verify router.push was used
    const pushCalled = await page.evaluate(() => window.routerPushCalled)
    expect(pushCalled).toBe(true)
  })

  test.skip('should handle multiple 401s gracefully without redirect loops', async ({ page }) => {
    let requestCount = 0

    // Seed session
    await page.addInitScript(() => {
      sessionStorage.setItem('token', 'test-token')
      sessionStorage.setItem('refreshToken', 'test-refresh')
      sessionStorage.setItem('user', JSON.stringify({
        id: 1,
        name: 'Test User',
        username: 'testuser',
        role: 'admin',
        allowedProjectIds: [],
        allowedDepts: []
      }))
    })

    // Inject spy
    await page.addInitScript(() => {
      window.routerPushCalled = false
      const checkAndPatch = () => {
        const app = window.__VUE_APP__
        if (app && app.config && app.config.globalProperties && app.config.globalProperties.$router) {
          const router = app.config.globalProperties.$router
          if (router._patched) return true
          const originalPush = router.push
          router.push = function (...args) {
            window.routerPushCalled = true
            return originalPush.apply(this, args)
          }
          router._patched = true
          return true
        }
        return false
      }
      const start = Date.now()
      const timer = setInterval(() => {
        if (checkAndPatch() || Date.now() - start > 5000) clearInterval(timer)
      }, 100)
    })

    // Track requests
    await page.route('**/api/**', async (route) => {
      requestCount++
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, message: 'Unauthorized' })
      })
    })

    // Navigate
    await page.goto('/dashboard')

    // Wait for redirect
    await expect(page).toHaveURL(/\/login$/, { timeout: 15000 })

    // Verify no redirect loop
    expect(requestCount).toBeLessThan(20)

    // Verify router.push used
    const pushCalled = await page.evaluate(() => window.routerPushCalled)
    expect(pushCalled).toBe(true)
  })
})
