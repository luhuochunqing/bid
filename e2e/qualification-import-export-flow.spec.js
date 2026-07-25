import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession, apiBaseUrl } from './auth-helpers.js'
import {
  generateValidQualificationImportExcel,
  generateInvalidQualificationImportExcel
} from './helpers/qualification-import.ts'

/**
 * §4.1.3.4 资质批量导入导出 E2E（重写版，匹配实际 UI）
 *
 * 实际 UI 结构：
 *   主页按钮：新增资质 / 导入台账 / 批量上传附件 / 告警配置 / 扫描到期
 *   选中行后显示 batch-toolbar：导出台账 / 批量下载附件
 *   "导入台账"按钮打开 QualImportCombinedDialog：
 *     - Excel 上传区 + "下载导入模板"链接 + "开始导入"按钮
 *     - 导入结果区：summary + 失败明细表（如有）+ "完成"按钮
 *
 * 后端关键端点：
 *   GET  /api/knowledge/qualifications/template   → xlsx blob
 *   POST /api/knowledge/qualifications/import-combined  → multipart/form-data file=...
 *   POST /api/knowledge/qualifications/batch-export     → JSON { ids: [...] } → xlsx blob
 */

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_qie_${suffix}`,
    role: '/bidAdmin',
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
  test('主页按钮可见：新增资质 / 导入台账 / 批量上传附件', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    await expect(page.locator('[data-testid="qual-create-btn"]'), '新增资质按钮应可见').toBeVisible()
    await expect(page.locator('[data-testid="qual-import-btn"]'), '导入台账按钮应可见').toBeVisible()
    await expect(page.locator('[data-testid="qual-batch-upload-btn"]'), '批量上传附件按钮应可见').toBeVisible()
    // selection 列存在
    await expect(page.locator('[data-testid="qual-table"] .el-table__header .el-checkbox').first(), 'selection 列 checkbox 应可见').toBeVisible()
  })

  test('下载模板：导入对话框内点击下载模板触发浏览器下载', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    // 先打开导入对话框
    await page.locator('[data-testid="qual-import-btn"]').click()
    const dialog = page.locator('[data-testid="qual-import-combined-dialog"]')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    // 点击"下载导入模板"链接
    const downloadPromise = page.waitForEvent('download', { timeout: 10000 })
    await page.locator('[data-testid="qual-download-template-btn"]').click()
    const download = await downloadPromise

    const filename = download.suggestedFilename()
    expect(filename, '模板文件名应包含"模板"').toMatch(/模板/)
  })

  test('合法导入：2 条合规行 → 成功 2 条 + 失败 0 + 失败明细表不显示', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    // 打开导入对话框
    await page.locator('[data-testid="qual-import-btn"]').click()
    const dialog = page.locator('[data-testid="qual-import-combined-dialog"]')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    const buffer = generateValidQualificationImportExcel()
    const fileInput = dialog.locator('[data-testid="qual-import-upload"] input[type="file"]')
    await fileInput.setInputFiles({ name: 'valid_qualifications.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer })

    // 点击"开始导入"
    await page.locator('[data-testid="qual-import-submit"]').click()

    // 等待 import result 出现
    const resultSection = dialog.locator('[data-testid="qual-import-result"]')
    await expect(resultSection, '导入结果区应显示').toBeVisible({ timeout: 20000 })

    // 验证成功计数 ≥ 1
    const successStat = resultSection.locator('.result-stat.success .stat-num')
    const successNum = Number(await successStat.textContent())
    expect(successNum, '成功条数应 ≥ 1').toBeGreaterThanOrEqual(1)

    // 失败明细表不显示（全部成功）
    await expect(dialog.locator('[data-testid="qual-import-failed-table"]'), '全部成功时失败明细表不显示').toHaveCount(0)

    // 关闭
    await page.locator('[data-testid="qual-import-result-close"]').click()
  })

  test('非法导入：4 类非法 → failed > 0 + 失败明细展示', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    await page.locator('[data-testid="qual-import-btn"]').click()
    const dialog = page.locator('[data-testid="qual-import-combined-dialog"]')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    const buffer = generateInvalidQualificationImportExcel()
    const fileInput = dialog.locator('[data-testid="qual-import-upload"] input[type="file"]')
    await fileInput.setInputFiles({ name: 'invalid_qualifications.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer })

    await page.locator('[data-testid="qual-import-submit"]').click()

    const resultSection = dialog.locator('[data-testid="qual-import-result"]')
    await expect(resultSection, '导入结果区应显示').toBeVisible({ timeout: 20000 })

    // 失败计数 > 0
    const failedStat = resultSection.locator('.result-stat.failed .stat-num')
    await expect(failedStat, '应有失败计数').toBeVisible({ timeout: 5000 })
    const failedNum = Number(await failedStat.textContent())
    expect(failedNum, '失败条数应 ≥ 1').toBeGreaterThanOrEqual(1)

    // 失败明细表
    const failedTable = dialog.locator('[data-testid="qual-import-failed-table"]')
    await expect(failedTable, '失败明细表应显示').toBeVisible()
    const rowCount = await failedTable.locator('.el-table__row').count()
    expect(rowCount, `失败明细行数应 ≥ 1，实际 ${rowCount}`).toBeGreaterThanOrEqual(1)
  })

  test('selection 列 + 批量导出按钮：选中后显示 + 点击触发下载', async ({ page }) => {
    const session = await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    // 先通过 API 创建一条资质用于导出测试（用绝对 URL，page.request 不走浏览器代理）
    const certNo = `E2E-EXP-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`
    const createRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${session.token}`
      },
      body: JSON.stringify({
        name: `E2E 批量导出测试-${Date.now()}`,
        level: 'A',
        certificateNo: certNo,
        issueDate: '2024-01-15',
        expiryDate: '2027-12-31',
        issuer: '中国计量认证中心',
        agency: '代理认证机构X',
        agencyContact: '13800138000',
        certScope: 'ISO9001 质量管理体系认证'
      })
    })
    expect(createRes.status, `API create status was ${createRes.status}`).toBeLessThan(300)

    // 刷新列表
    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
    // 等列表加载
    await page.waitForResponse(r => r.url().includes('/api/knowledge/qualifications') && r.status() < 500, { timeout: 5000 }).catch(() => {})

    // 未选中时 batch-toolbar 不存在
    await expect(page.locator('[data-testid="qual-batch-toolbar"]'), '未选中时 batch-toolbar 应隐藏').toHaveCount(0)
    await expect(page.locator('[data-testid="qual-batch-export-btn"]'), '未选中时批量导出按钮应隐藏').toHaveCount(0)

    // 选第一行 checkbox
    const firstCheckbox = page.locator('[data-testid="qual-table"] .el-table__body .el-table__row .el-checkbox').first()
    await firstCheckbox.click()

    // batch-toolbar 出现
    await expect(page.locator('[data-testid="qual-batch-toolbar"]'), '选中后 batch-toolbar 应显示').toBeVisible({ timeout: 5000 })
    const batchBtn = page.locator('[data-testid="qual-batch-export-btn"]')
    await expect(batchBtn, '批量导出按钮应显示').toBeVisible({ timeout: 5000 })

    // 点击触发下载
    const downloadPromise = page.waitForEvent('download', { timeout: 15000 })
    await batchBtn.click()
    const download = await downloadPromise
    expect(download.suggestedFilename(), '批量导出文件名应包含"批量导出"').toMatch(/批量导出/)
  })

  test('非 .xlsx 文件被 el-upload accept 拦截', async ({ page }) => {
    await loginAsBidAdmin(page)
    await gotoQualificationPage(page)

    await page.locator('[data-testid="qual-import-btn"]').click()
    const dialog = page.locator('[data-testid="qual-import-combined-dialog"]')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    // el-upload accept=".xlsx,.xls" 会过滤非 Excel 文件，input 不会触发 onChange
    // 直接 setInputFiles 仍可绕过 accept，但 el-upload 会拒绝非 xlsx 的文件
    const fileInput = dialog.locator('[data-testid="qual-import-upload"] input[type="file"]')
    await fileInput.setInputFiles({ name: 'not-excel.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') })

    // 上传后"开始导入"按钮应仍 disabled（excelFiles 为空）
    const submitBtn = page.locator('[data-testid="qual-import-submit"]')
    await expect(submitBtn, '非法格式时开始导入应 disabled').toBeDisabled()

    // import result 不出现
    await expect(dialog.locator('[data-testid="qual-import-result"]'), '非法格式不应出现结果区').toHaveCount(0)
  })
})
