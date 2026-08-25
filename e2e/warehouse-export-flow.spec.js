import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession, apiBaseUrl } from './auth-helpers.js'

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

// 仓库表在 E2E 环境默认为空，导出/勾选场景需先经 API 造一条唯一仓库
async function createWarehouseViaApi(session, name) {
  const pad = (n) => String(n).padStart(2, '0')
  const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  const start = new Date()
  const end = new Date(start); end.setFullYear(end.getFullYear() + 3)
  const res = await fetch(`${apiBaseUrl}/api/knowledge/warehouses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
    body: JSON.stringify({
      name,
      type: 'SELF_OPERATED',
      region: '华南大区',
      province: '广东省',
      address: '广州市黄埔区E2E测试路1号',
      area: 120.5,
      contactPerson: 'E2E仓管',
      startDate: iso(start),
      endDate: iso(end),
      lessor: '西域出租方',
      lessee: '西域承租方',
      hasPropertyCert: false,
      hasLeaseContract: false,
      hasPhotos: false,
      hasInvoice: false
    })
  })
  if (!res.ok && res.status !== 400) throw new Error(`创建仓库失败: ${res.status}`)
}

test.describe('§4.4 仓库台账导出', () => {

  test('正向流程: bid_admin 导出台账', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin')
    await createWarehouseViaApi(session, `E2E台账仓库_${Date.now()}`)
    await page.goto('/knowledge/warehouse')
    await page.waitForSelector('.el-table, .data-card', { timeout: 10000 })

    // 点击"导出台账"按钮（精确匹配，避开"导出台账（含附件）"）
    const exportBtn = page.getByRole('button', { name: '导出台账', exact: true })
    await expect(exportBtn).toBeVisible()
    await exportBtn.click()

    // 等待台账导出对话框并开始导出
    const ledgerDialog = page.getByTestId('warehouse-ledger-export-dialog')
    await expect(ledgerDialog).toBeVisible({ timeout: 5000 })
    await ledgerDialog.locator('.el-button:has-text("开始导出")').click()

    // 等待导出完成 (轮询最多30秒)
    await expect(page.locator('.el-result__title:has-text("仓库台账导出")')).toBeVisible({ timeout: 30000 })

    // 点击下载按钮
    const downloadBtn = page.getByRole('button', { name: '下载台账' })
    await expect(downloadBtn).toBeVisible()
  })

  test('权限验证: bid_specialist 可以导出', async ({ page }) => {
    await loginAsRole(page, 'bid-Team')
    await page.goto('/knowledge/warehouse')
    await page.waitForSelector('.el-table, .data-card', { timeout: 10000 })

    const exportBtn = page.getByRole('button', { name: '导出台账', exact: true })
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

  test('第二个导出台账按钮应打开台账导出对话框而非附件导出对话框', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    await page.goto('/knowledge/warehouse')
    await page.waitForSelector('.el-table, .data-card', { timeout: 10000 })

    const exportButtons = page.getByRole('button', { name: /导出台账/ })
    await expect(exportButtons).toHaveCount(2)

    const secondExportBtn = exportButtons.nth(1)
    await secondExportBtn.click()

    await expect(page.getByTestId('warehouse-ledger-export-dialog')).toBeVisible({ timeout: 5000 })

    await expect(page.getByTestId('warehouse-export-dialog')).toBeHidden({ timeout: 3000 })
  })

  test('第二个导出台账按钮有勾选时默认选中"当前勾选"范围', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin')
    await createWarehouseViaApi(session, `E2E勾选仓库_${Date.now()}`)
    await page.goto('/knowledge/warehouse')
    await page.waitForSelector('.el-table, .data-card', { timeout: 10000 })

    // 等待至少一行数据出现后再勾选
    await expect(page.locator('.el-table__body-wrapper .el-checkbox__input').nth(1)).toBeVisible({ timeout: 10000 })
    const firstCheckbox = page.locator('.el-table__body-wrapper .el-checkbox__input').first()
    await firstCheckbox.click()

    const exportButtons = page.getByRole('button', { name: /导出台账/ })
    const secondExportBtn = exportButtons.nth(1)
    await secondExportBtn.click()

    await expect(page.getByTestId('warehouse-ledger-export-dialog')).toBeVisible({ timeout: 5000 })

    const idsRadio = page.locator('.scope-radio:has-text("当前勾选的仓库")')
    await expect(idsRadio).toHaveClass(/is-checked/)
  })

  test('第二个导出台账按钮无勾选时默认选中"当前筛选结果"范围', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    await page.goto('/knowledge/warehouse')
    await page.waitForSelector('.el-table, .data-card', { timeout: 10000 })

    const exportButtons = page.getByRole('button', { name: /导出台账/ })
    const secondExportBtn = exportButtons.nth(1)
    await secondExportBtn.click()

    await expect(page.getByTestId('warehouse-ledger-export-dialog')).toBeVisible({ timeout: 5000 })

    const filterRadio = page.locator('.scope-radio:has-text("当前筛选结果")')
    await expect(filterRadio).toHaveClass(/is-checked/)
  })
})
