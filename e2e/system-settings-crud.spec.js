// @ui-cover:settings
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('system settings CRUD', () => {
  test('settings tabs render for admin role', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_set_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Settings Admin'
    })

    await injectSession(page, session)
    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: '系统设置' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '角色权限' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '用户组织归属' })).toBeVisible()
  })

  test('system info tab displays version, milestones and author details', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_sys_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E System Info Admin'
    })

    await injectSession(page, session)
    await page.goto('/settings?tab=system-info')
    
    await expect(page.locator('.system-info-panel')).toBeVisible()
    await expect(page.getByRole('heading', { name: '系统信息' })).toBeVisible()
    await expect(page.getByText('西域数智化投标管理平台').first()).toBeVisible()
    await expect(page.getByText('平台作者')).toBeVisible()
    await expect(page.getByText('卢文融').first()).toBeVisible()
    await expect(page.getByText('13761778461')).toBeVisible()
    await expect(page.getByText('技术与运维诊断信息 (开发联调专用)')).toBeVisible()
  })
})
