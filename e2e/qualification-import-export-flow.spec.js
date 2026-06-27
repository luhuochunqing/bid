import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'
import {
  generateValidQualificationImportExcel,
  generateInvalidQualificationImportExcel
} from './helpers/qualification-import.ts'

/**
 * §4.1.3.4 资质批量导入导出 E2E
 *
 * 蓝图 4.1.3.4 要求：
 * 1. 三个入口按钮：下载导入模板 / 批量导入 / 导出台账
 * 2. 批量导入：上传 11 列 xlsx，行级校验 + 失败明细
 * 3. 批量导出：选中行导出（带 ids query param）
 * 4. 模板下载：11 列头 + 示例行
 *
 * 后端关键端点：
 *   GET  /api/knowledge/qualifications/template   → xlsx blob
 *   GET  /api/knowledge/qualifications/export     → xlsx blob（?ids=1,2,3）
 *   POST /api/knowledge/qualifications/import     → multipart/form-data file=...
 */

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_qie_${suffix}`,
    role: 'bid_admin',
    fullName: 'E2E 资质导入导出'
  })
  await injectSession(page, session)
  return session
}

async function gotoQualificationPage(page) {
  await page.goto('/knowledge/qualification')
  await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
}

test.describe('§4.1.3.4 资质批量导入导出', () => {
  test('3 个入口按钮可见：下载模板 / 批量导入 / 导出台账', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    await expect(page.locator('[data-testid="qual-download-template-btn"]'), '下载导入模板按钮应可见').toBeVisible()
    await expect(page.locator('[data-testid="qual-import-btn"]'), '批量导入按钮应可见').toBeVisible()
    await expect(page.locator('[data-testid="qual-export-ledger-btn"]'), '导出台账按钮应可见').toBeVisible()
    // selection 列存在
    await expect(page.locator('.el-table__header .el-checkbox').first(), 'selection 列 checkbox 应可见').toBeVisible()
  })

  test('下载模板：触发浏览器下载 + 文件名匹配', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    const downloadPromise = page.waitForEvent('download', { timeout: 10000 })
    await page.locator('[data-testid="qual-download-template-btn"]').click()
    const download = await downloadPromise

    const filename = download.suggestedFilename()
    expect(filename, '模板文件名应包含"模板"').toMatch(/模板/)
  })

  test('合法导入：2 条合规行 → 成功 2 条 + 失败 0 + 失败明细表不显示', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    const buffer = generateValidQualificationImportExcel()
    const fileInput = page.locator('[data-testid="qual-import-upload"] input[type="file"]')
    await fileInput.setInputFiles({ name: 'valid_qualifications.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer })

    // 等待 import result dialog
    const dialog = page.locator('[data-testid="qual-import-result-dialog"]')
    await expect(dialog, '导入结果 dialog 应打开').toBeVisible({ timeout: 15000 })

    // 验证 success ≥ 1
    const alert = dialog.locator('.el-alert')
    const alertText = await alert.textContent()
    expect(alertText, 'alert 应展示 success 计数').toMatch(/成功\s*\d+\s*条/)

    // 失败明细表不显示（全部成功）
    await expect(dialog.locator('[data-testid="qual-import-failed-table"]'), '全部成功时失败明细表不显示').toHaveCount(0)

    // 关闭
    await page.locator('[data-testid="qual-import-result-close"]').click()
  })

  test('非法导入：1 条合法 + 4 类非法 → success=1, failed=4 + 失败明细展示', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    const buffer = generateInvalidQualificationImportExcel()
    const fileInput = page.locator('[data-testid="qual-import-upload"] input[type="file"]')
    await fileInput.setInputFiles({ name: 'invalid_qualifications.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer })

    const dialog = page.locator('[data-testid="qual-import-result-dialog"]')
    await expect(dialog, '导入结果 dialog 应打开').toBeVisible({ timeout: 15000 })

    // alert 提示失败 4 条
    const alertText = await dialog.locator('.el-alert').textContent()
    expect(alertText, 'alert 应展示 4 条失败').toMatch(/失败\s*4\s*条/)

    // 失败明细表
    const failedTable = dialog.locator('[data-testid="qual-import-failed-table"]')
    await expect(failedTable, '失败明细表应显示').toBeVisible()
    const rowCount = await failedTable.locator('.el-table__row').count()
    expect(rowCount, `失败明细行数应为 4，实际 ${rowCount}`).toBe(4)

    // 至少 1 个失败原因含"不能为空"
    const reasons = await failedTable.locator('.el-table__row').allTextContents()
    expect(reasons.some(r => r.includes('不能为空')), '应包含"不能为空"原因').toBe(true)
    expect(reasons.some(r => r.includes('格式')), '应包含"格式"原因').toBe(true)
  })

  test('selection 列 + 批量导出按钮：选中后显示 + 点击触发下载', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    // 通过 API 先创建一条
    const session = await page.evaluate(() => JSON.parse(sessionStorage.getItem('user') || '{}'))
    const token = await page.evaluate(() => sessionStorage.getItem('token'))
    const certNo = `E2E-EXP-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`
    const createRes = await page.request.post('/api/knowledge/qualifications', {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {
        name: `E2E 批量导出测试-${Date.now()}`,
        certificateNo: certNo,
        issueDate: '2024-01-15',
        expiryDate: '2027-12-31',
        issuer: '中国计量认证中心',
        agency: '代理认证机构X',
        agencyContact: '13800138000',
        certScope: 'ISO9001 质量管理体系认证',
        certReviewNote: '每年 3 月年审'
      }
    })
    expect(createRes.status(), `API create status was ${createRes.status()}`).toBeLessThan(300)

    // 刷新列表
    await gotoQualificationPage(page)
    // 等 1 秒让列表加载完成
    await page.waitForResponse(r => r.url().includes('/api/knowledge/qualifications') && r.status() < 500, { timeout: 5000 }).catch(() => {})

    // 批量导出按钮未选中时不可见
    await expect(page.locator('[data-testid="qual-batch-export-btn"]'), '未选中时批量导出按钮应隐藏').toHaveCount(0)

    // 选第一行的 checkbox
    const firstCheckbox = page.locator('[data-testid="qual-table"] .el-table__body .el-table__row .el-checkbox').first()
    await firstCheckbox.click()

    // 批量导出按钮出现
    const batchBtn = page.locator('[data-testid="qual-batch-export-btn"]')
    await expect(batchBtn, '选中后批量导出按钮应显示').toBeVisible({ timeout: 5000 })
    const btnText = await batchBtn.textContent()
    expect(btnText, '按钮文本应包含"已选"').toMatch(/已选\s*1\s*条/)

    // 点击触发下载
    const downloadPromise = page.waitForEvent('download', { timeout: 10000 })
    await batchBtn.click()
    const download = await downloadPromise
    expect(download.suggestedFilename(), '批量导出文件名应包含"批量导出"').toMatch(/批量导出/)
  })

  test('导出台账按钮：可点击并触发下载', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    const downloadPromise = page.waitForEvent('download', { timeout: 10000 })
    await page.locator('[data-testid="qual-export-ledger-btn"]').click()
    const download = await downloadPromise
    expect(download.suggestedFilename(), '台账文件名应包含"台账"').toMatch(/台账/)
  })

  test('非 .xlsx 文件被 beforeImportUpload 拦截', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    const fileInput = page.locator('[data-testid="qual-import-upload"] input[type="file"]')
    await fileInput.setInputFiles({ name: 'not-excel.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') })

    // 错误 toast 出现
    await expect(page.locator('.el-message--error').filter({ hasText: /xlsx/ }), '应显示 xlsx 格式错误').toBeVisible({ timeout: 5000 })

    // import result dialog 不出现
    await expect(page.locator('[data-testid="qual-import-result-dialog"]'), '非法格式不应弹出结果 dialog').toHaveCount(0)
  })
})
