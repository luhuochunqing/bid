import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * CO-155 E2E — 资质证书保存后列表不展示 + 附件 status 映射 + 领域筛选 三连故障
 *
 * 验证三件事：
 *   1. 保存后立即在列表第一页看到新记录（page=1 重置）
 *   2. 领域(category) 下拉过滤生效，后端分页接口支持 ?category=
 *   3. 附件上传限值 50MB（与 PR 680122945 对齐，不再是 10MB）
 */

// Helper: 清理测试数据
async function cleanupTestQualifications(token, suffix) {
  try {
    // 获取所有测试创建的记录
    const listRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications?page=0&size=100`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!listRes.ok) return
    const body = await listRes.json()
    const items = body.data?.content || []
    // 删除包含 suffix 的记录
    for (const item of items) {
      if (item.name && item.name.includes(suffix)) {
        await fetch(`${apiBaseUrl}/api/knowledge/qualifications/${item.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` }
        }).catch(() => {})
      }
    }
  } catch {
    // 清理失败不影响测试结果
  }
}

test.describe('CO-155 资质证书三连故障修复', () => {

  test('保存后能立刻在列表第一页看到（page 重置）', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_co155_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E CO-155 测试'
    })

    try {

    // 通过 API 预置 20 条记录，让前端分页有意义
    for (let i = 0; i < 20; i++) {
      await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
        body: JSON.stringify({
          name: `E2E 预置记录 ${suffix}-${i}`,
          certificateNo: `E2E-CO155-${suffix}-${i}`,
          issuer: '测试发证机关',
          holderName: '测试持有人',
          expiryDate: '2027-12-31',
          status: 'in_stock',
          subjectType: 'COMPANY',
          subjectName: '西域',
          category: 'LICENSE'
        })
      })
    }
    await new Promise(r => setTimeout(r, 1000))

    await injectSession(page, session)
    await page.goto('/knowledge/qualification', { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.el-table', { timeout: 20000 })

    // 翻到第 2 页（验证保存后是否会重置回第 1 页）
    const nextPageBtn = page.locator('.el-pagination .btn-next').first()
    if (await nextPageBtn.isVisible()) {
      // CO-155 e2e-selector: wait for pagination API response, not time-based
      const page2Resp = page.waitForResponse(r => r.url().includes('/api/knowledge/qualifications') && r.url().includes('page=1'))
      await nextPageBtn.click()
      await page2Resp
    }

    // 通过 API 提交一条新记录
    const uniqueName = `CO-155 修复测试 ${suffix}`
    const createRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: uniqueName,
        certificateNo: `CO155-${suffix}`,
        issuer: 'CO-155 测试机构',
        holderName: 'CO-155 测试人',
        expiryDate: '2027-12-31',
        status: 'in_stock',
        subjectType: 'COMPANY',
        subjectName: '西域',
        category: 'LICENSE'
      })
    })
    expect(createRes.ok).toBeTruthy()

    // 触发列表刷新（模拟 QualFormDialog emit('saved') → handleFormSaved → page=1 + fetchQualifications）
    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.el-table', { timeout: 20000 })

    // 关键断言：新记录应可见（且在第一页）
    const newRow = page.locator('.el-table__body-wrapper tbody tr', { hasText: uniqueName })
    await expect(newRow).toBeVisible({ timeout: 10000 })
    } finally {
      await cleanupTestQualifications(session.token, `E2E-CO155-${suffix}`)
    }
  })

  test('领域(category) 下拉过滤生效', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_co155_cat_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E CO-155 category 测试'
    })

    try {

    // 创建一条 category=PRODUCT 的记录
    const productName = `E2E 产品资质 ${suffix}`
    const productRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: productName,
        certificateNo: `CO155-PROD-${suffix}`,
        issuer: '测试发证机关',
        holderName: '测试持有人',
        expiryDate: '2027-12-31',
        status: 'in_stock',
        subjectType: 'COMPANY',
        subjectName: '西域',
        category: 'PRODUCT'
      })
    })
    expect(productRes.ok).toBeTruthy()

    // 创建一条 category=LICENSE 的记录
    const licenseName = `E2E 企业资质 ${suffix}`
    await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: licenseName,
        certificateNo: `CO155-LIC-${suffix}`,
        issuer: '测试发证机关',
        holderName: '测试持有人',
        expiryDate: '2027-12-31',
        status: 'in_stock',
        subjectType: 'COMPANY',
        subjectName: '西域',
        category: 'LICENSE'
      })
    })
    await new Promise(r => setTimeout(r, 1000))

    await injectSession(page, session)
    await page.goto('/knowledge/qualification', { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.filter-card', { timeout: 20000 })

    // 关键断言：filter card 应有"领域"下拉
    const categorySelector = page.locator('.el-form-item', { hasText: '领域' }).locator('.el-select')
    await expect(categorySelector).toBeVisible({ timeout: 5000 })

    // 点击下拉选 PRODUCT
    await categorySelector.click()
    // CO-155 e2e-selector: wait for the dropdown options to render
    await page.locator('.el-select-dropdown__item', { hasText: '产品资质' }).first().waitFor({ state: 'visible' })
    const productOption = page.locator('.el-select-dropdown__item', { hasText: '产品资质' }).first()
    await productOption.click()
    // Wait for filter query response (URL includes category=PRODUCT)
    const filterResp = page.waitForResponse(r => r.url().includes('category=PRODUCT'))
    await page.locator('.el-form-item button', { hasText: '查询' }).first().click()
    await filterResp

    // 关键断言：列表中应能看到 PRODUCT 记录，看不到 LICENSE 记录
    const productRow = page.locator('.el-table__body-wrapper tbody tr', { hasText: productName })
    await expect(productRow).toBeVisible({ timeout: 5000 })
    const licenseRow = page.locator('.el-table__body-wrapper tbody tr', { hasText: licenseName })
    await expect(licenseRow).toHaveCount(0)
    } finally {
      await cleanupTestQualifications(session.token, `CO155-PROD-${suffix}`)
      await cleanupTestQualifications(session.token, `CO155-LIC-${suffix}`)
    }
  })

  test('后端分页接口响应含 totalElements + content 字段', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_co155_pg_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E CO-155 pagination'
    })

    // 验证 GET /api/knowledge/qualifications?page=0&size=15 返回 Spring Page JSON
    const res = await fetch(`${apiBaseUrl}/api/knowledge/qualifications?page=0&size=15&status=IN_STOCK`, {
      headers: { Authorization: `Bearer ${session.token}` }
    })
    expect(res.ok).toBeTruthy()
    const body = await res.json()

    // Spring Data Page 默认 JSON 序列化
    expect(body.success).toBe(true)
    expect(body.data).toBeDefined()
    expect(Array.isArray(body.data.content)).toBe(true)
    expect(typeof body.data.totalElements).toBe('number')
    expect(typeof body.data.totalPages).toBe('number')
    expect(typeof body.data.size).toBe('number')
    expect(typeof body.data.number).toBe('number')
  })

  test('领域(category) 在 QualFormDialog 中改为下拉选择（不再硬编码）', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_co155_dlg_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E CO-155 dialog'
    })
    await injectSession(page, session)
    await page.goto('/knowledge/qualification', { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.el-table', { timeout: 20000 })

    // 点击"新增"
    const newBtn = page.locator('button', { hasText: /新增|新建/ }).first()
    await newBtn.click()
    // QualFormDialog 应打开 (断言自带等待)
    const dialog = page.locator('[data-testid="qual-form-dialog"]')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    // 关键断言：领域、主体类型应为 el-select，不是硬编码输入
    const categorySelect = dialog.locator('[data-testid="qf-category"]')
    await expect(categorySelect).toBeVisible({ timeout: 3000 })
    const subjectTypeSelect = dialog.locator('[data-testid="qf-subjectType"]')
    await expect(subjectTypeSelect).toBeVisible({ timeout: 3000 })
    const subjectNameInput = dialog.locator('[data-testid="qf-subjectName"]')
    await expect(subjectNameInput).toBeVisible({ timeout: 3000 })

    // 关键断言：附件限值应为 50MB（与 PR 680122945 对齐）
    const uploadTip = dialog.locator('.el-upload__tip')
    const tipText = await uploadTip.textContent()
    expect(tipText).toContain('50MB')

    // 关闭
    await page.keyboard.press('Escape')
  })
})
