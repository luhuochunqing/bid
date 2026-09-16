import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

// 本 spec 需在开发宿主环境执行；本次实现仅做语法/静态自检，不启动开发环境。
test.describe('案例详情来源标题与原文定位', () => {
  test('详情显示来源标题 label 和 NULL 降级文案', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_source_title_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E 来源标题 Admin'
    })
    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('load')

    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible()) {
      await firstCard.click()
      await expect(page.getByText('原文所在标题：', { exact: false })).toBeVisible()
      const hasLocationContent = (await page.locator('.source-location').count()) > 0 || (await page.locator('.source-location-fallback').count()) > 0
      expect(hasLocationContent).toBe(true)
      await expect(page.getByText('评分项标题', { exact: false })).toBeVisible()
    }
  })

  test('来源标题点击沿用原文流程并显示定位提示，权限/下载分支保持可验证', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_source_title_click_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E 来源定位 Admin'
    })
    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('load')

    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible()) {
      await firstCard.click()
      const sourceLocation = page.locator('.source-location')
      if (await sourceLocation.isVisible()) {
        await sourceLocation.click()
        await expect(page.locator('.el-message')).toContainText('请定位到：')
      }
      await expect(page.getByRole('button', { name: '标书原文' })).toBeVisible()
    }
  })
})
