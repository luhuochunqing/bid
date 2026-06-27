import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

async function apiRequest(path, session, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
      ...(options.headers || {}),
    },
  })
  if (!response.ok) {
    throw new Error(`API request failed: ${path} -> ${response.status}`)
  }
  return response.json()
}

async function loginAs(page, role) {
  const PWD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'
  const s = await ensureApiSession({
    username: `e2e_perf_doc_${role}_${Date.now()}_${Math.random().toString(36).slice(2,6)}`,
    role: 'ADMIN', fullName: `E2E PERF DOC ${role}`, password: PWD
  })
  await injectSession(page, s)
  return s
}

test.describe('业绩一键插入标书编辑器', () => {
  test('通过 sessionStorage 模拟业绩自动插入文档编辑器的案例展示章节', async ({ page }) => {
    const session = await loginAs(page, 'bid_admin')
    const projectId = 980100 + Date.now() % 10000

    // 1. 创建文档结构（带案例展示章节）
    const structurePayload = await apiRequest(`/api/documents/${projectId}/editor/structure`, session, {
      method: 'POST',
      body: JSON.stringify({ projectId, name: `业绩插入测试结构 ${projectId}` })
    })
    const structureId = structurePayload?.data?.id
    expect(structureId).toBeTruthy()

    // 创建案例展示章节
    const sectionPayload = await apiRequest(`/api/documents/${projectId}/editor/sections`, session, {
      method: 'POST',
      body: JSON.stringify({
        structureId,
        sectionType: 'SECTION',
        title: '案例展示',
        content: '## 案例展示\n\n在此处编辑案例展示...',
        orderIndex: 1
      })
    })
    expect(sectionPayload?.data?.id).toBeTruthy()

    // 2. 通过 UI 创建一条业绩记录（避免直接 API 的字段不匹配问题）
    await page.goto('/knowledge/performance')
    await expect(page.locator('.el-table__header:has-text("合同名称")')).toBeVisible({ timeout: 10000 })

    await page.getByRole('button', { name: '新增业绩' }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })

    const testContractName = `E2E业绩_插入测试_${Date.now()}`
    await page.locator('.el-dialog input[placeholder*="合同名称"]').fill(testContractName)
    await page.locator('.el-dialog input[placeholder*="签约单位"]').fill('中国测试集团')
    await page.locator('.el-dialog input[placeholder*="集团公司"]').fill('中国测试集团')

    await page.locator('.el-dialog label:has-text("客户类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("民企")').click()
    await page.locator('.el-dialog input[placeholder*="所属行业"]').fill('信息技术')

    await page.locator('.el-dialog label:has-text("项目类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("综合")').click()
    await page.locator('.el-dialog label:has-text("对接方式") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("API")').click()

    await page.locator('.el-dialog .el-tabs__item:has-text("关键日期")').click()
    const dates = page.locator('.el-dialog .el-tab-pane:not([style*="display: none"]) .el-date-editor .el-input__inner')
    await dates.nth(0).fill('2025-01-01')
    await dates.nth(1).fill('2027-12-31')

    await page.locator('.el-dialog .el-tabs__item:has-text("客户信息")').click()
    await page.locator('.el-dialog input[placeholder*="姓名（职务）"]').fill('李测试')
    await page.locator('.el-dialog input[placeholder*="手机/座机/邮箱"]').fill('13800000000')
    await page.locator('.el-dialog input[placeholder*="例如：四川省成都市"]').fill('北京市')
    await page.locator('.el-dialog input[placeholder*="详细的联系寄送地址"]').fill('北京市朝阳区')
    await page.locator('.el-dialog input[placeholder*="西域对接本项目的负责人"]').fill('王测试')

    await page.locator('.el-dialog .el-tabs__item:has-text("附件资料")').click()
    const ar = page.locator('.el-dialog .attachment-row')
    await ar.nth(0).locator('input').nth(0).fill('合同.pdf')
    await ar.nth(0).locator('input').nth(1).fill('http://dummy-oss.com/c.pdf')

    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

    // 3. 验证 sessionStorage 插入流程
    // 先设置 sessionStorage
    await page.goto('/about:blank')
    await page.evaluate(() => {
      sessionStorage.setItem('pendingPerformanceInsert', JSON.stringify({
        contractName: '直接测试合同',
        signingEntity: '直接测试单位',
        remainingDays: 365,
        reason: 'E2E测试',
        fullText: '合同名称：直接测试合同\n签约单位：直接测试单位',
        timestamp: Date.now()
      }))
    })

    // 打开编辑器
    await page.goto(`/document/editor/${projectId}`)
    await expect(page.locator('.document-editor-page')).toBeVisible({ timeout: 10000 })

    // 等待自动插入完成（通过消息判断）
    await expect(page.locator('.el-message--success:has-text("业绩信息已自动插入")')).toBeVisible({ timeout: 5000 })

    // 验证案例展示章节内容包含业绩信息
    const textarea = page.locator('textarea.content-textarea')
    await expect(textarea).toBeVisible()
    const content = await textarea.inputValue()
    expect(content).toContain('业绩信息')
    expect(content).toContain('直接测试合同')
    expect(content).toContain('直接测试单位')

    // 验证 sessionStorage 已被清理
    const raw = await page.evaluate(() => sessionStorage.getItem('pendingPerformanceInsert'))
    expect(raw).toBeNull()

    // 4. 清理：删除测试业绩
    await page.goto('/knowledge/performance')
    await expect(page.locator('.el-table__header:has-text("合同名称")')).toBeVisible({ timeout: 10000 })
    const delBtn = page.locator(`.el-table__body tr:has-text("${testContractName}") .el-button:has-text("删除")`)
    await delBtn.click()
    await page.locator('.el-message-box__btns .el-button:has-text("删除")').click()
    await expect(page.locator('.el-message-box')).toBeHidden({ timeout: 5000 })
  })
})
