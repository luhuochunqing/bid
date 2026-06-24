import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const PWD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_wh_exp_${role}_${suffix}`,
    role,
    fullName: `E2E WH Export ${role}`,
    password: PWD
  })
  await injectSession(page, session)
  return session
}

test.describe('§4.4 仓库台账导出', () => {

  test('正向流程: bid_admin 导出台账', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    await page.goto('/knowledge/warehouse')
    await page.waitForSelector('.el-table, .data-card', { timeout: 10000 })

    // 点击导出台账按钮
    const exportBtn = page.getByRole('button', { name: '导出台账' })
    await expect(exportBtn).toBeVisible()
    await exportBtn.click()

    // 等待导出对话框出现
    await expect(page.getByText('仓库台账导出')).toBeVisible({ timeout: 5000 })

    // 等待导出完成 (轮询最多30秒)
    await expect(page.getByText('导出完成')).toBeVisible({ timeout: 30000 })

    // 点击下载按钮
    const downloadBtn = page.getByRole('button', { name: '下载 Excel' })
    await expect(downloadBtn).toBeVisible()
  })

  test('权限验证: bid_specialist 可以导出', async ({ page }) => {
    await loginAsRole(page, 'bid-Team')
    await page.goto('/knowledge/warehouse')
    await page.waitForSelector('.el-table, .data-card', { timeout: 10000 })

    const exportBtn = page.getByRole('button', { name: '导出台账' })
    await expect(exportBtn).toBeVisible()
  })

  test('权限验证: sales 不应看到仓库入口', async ({ page }) => {
    await loginAsRole(page, 'bid-projectLeader')
    await page.goto('/')
  await page.waitForSelector('.el-table, .data-card, .sidebar-container', { timeout: 15000 })

    // sales 的菜单中不应该有"仓库信息管理"
    const warehouseMenu = page.getByRole('menuitem', { name: /仓库/ })
    await expect(warehouseMenu).toHaveCount(0)
  })
})
