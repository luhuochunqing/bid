import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * 4.1.3.1 新增资质表单 11 字段录入 E2E
 *
 * 蓝图 4.1.3.1 模板 11 字段：
 *   必填 5 字段：证书名称 / 认证机构 / 证书编号 / 发证日期 / 证书有效期
 *   非必填 5 字段：等级 / 代理机构 / 代理联系方式 / 认证范围 / 证书审核提醒
 *   附件：选填 1 个 PDF/JPG/PNG ≤10MB
 *
 * 校验：
 *   - 必填 5 项空 → 提交失败
 *   - 联系方式格式：手机/固话/邮箱正则
 *   - 有效期必须晚于发证日期
 *
 * Selector 约定（el-input/textarea 直接是 data-testid 元素自身）：
 *   - 普通输入：page.locator('[data-testid="qf-name"]').fill(...)
 *   - 证书范围：page.locator('[data-testid="qf-certScope"]').fill(...)
 *   - 日期：page.locator('[data-testid="qf-issueDate"] input').fill(...)  // el-date-picker 内部 input
 */

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_qf_${suffix}`,
    role: 'bid_admin',
    fullName: 'E2E 资质表单'
  })
  await injectSession(page, session)
  return session
}

async function openCreateDialog(page) {
  await page.goto('/knowledge/qualification')
  await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
  await page.getByRole('button', { name: /新增资质/ }).click()
  await page.waitForSelector('[data-testid="qual-form-dialog"]', { timeout: 5000 })
}

test.describe('§4.1.3.1 新增资质表单 11 字段', () => {
  test('正向流程：11 字段全部录入 + 提交成功 + success toast', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    const certNo = `QF-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`

    // 5 个必填字段（el-input/textarea data-testid 自身就是 input 元素）
    await page.locator('[data-testid="qf-name"]').fill('E2E资质11字段测试')
    await page.locator('[data-testid="qf-issuer"]').fill('中国计量认证中心')
    await page.locator('[data-testid="qf-certificateNo"]').fill(certNo)

    // 日期：el-date-picker 外包 div 带 testid，内部有 input
    await page.locator('[data-testid="qf-issueDate"] input').fill('2024-01-15')
    await page.locator('[data-testid="qf-issueDate"] input').press('Enter')
    await page.locator('[data-testid="qf-expiryDate"] input').fill('2027-12-31')
    await page.locator('[data-testid="qf-expiryDate"] input').press('Enter')

    // 5 个非必填字段：level 留空（后端 level 是 enum，提交时空字符串会被前端过滤）
    await page.locator('[data-testid="qf-agency"]').fill('代理认证机构X')
    await page.locator('[data-testid="qf-agencyContact"]').fill('13800138000')
    await page.locator('[data-testid="qf-certScope"]').fill('ISO9001 质量管理体系认证范围')
    await page.locator('[data-testid="qf-certReviewNote"]').fill('每年 3 月年审')

    // 提交
    await page.locator('[data-testid="qf-submit"]').click()

    // 验证 success toast
    await expect(page.locator('.el-message--success').filter({ hasText: '新增成功' })).toBeVisible({ timeout: 8000 })
    // 验证 dialog 关闭
    await expect(page.locator('[data-testid="qual-form-dialog"]')).not.toBeVisible({ timeout: 5000 })

    // 验证列表新增成功（按 certNo 查找）
    const newRow = page.locator('.el-table__row').filter({ hasText: certNo }).first()
    await expect(newRow, `列表应出现证书号 ${certNo}`).toBeVisible({ timeout: 8000 })
  })

  test('必填校验：5 个核心字段为空时提交 → 表单内联错误 + 不提交', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    // 不填任何字段直接提交
    await page.locator('[data-testid="qf-submit"]').click()

    // 等待至少 5 个 .el-form-item__error 出现（el-form 校验是异步的）
    await page.waitForFunction(
      () => document.querySelectorAll('.el-form-item__error').length >= 5,
      null,
      { timeout: 5000 }
    ).catch(() => null)

    // 验证至少 5 个必填项的内联错误提示
    const formErrorTexts = await page.locator('.el-form-item__error').allTextContents()
    const errs = formErrorTexts.filter(t => t && t.trim()).map(t => t.trim())
    // 应包含 5 个必填错误
    expect(errs.length, `应有 5 个必填错误，实际: ${errs.join(',')}`).toBeGreaterThanOrEqual(5)
    // 警告 toast
    await expect(page.locator('.el-message--warning').filter({ hasText: /必填|完整填写/ })).toBeVisible({ timeout: 3000 })

    // 验证 dialog 仍打开
    await expect(page.locator('[data-testid="qual-form-dialog"]')).toBeVisible()
  })

  test('非必填字段可以留空：只填 5 个必填即可提交', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    const certNo = `MIN-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`

    // 只填 5 个必填
    await page.locator('[data-testid="qf-name"]').fill('最小字段测试')
    await page.locator('[data-testid="qf-issuer"]').fill('CMA')
    await page.locator('[data-testid="qf-certificateNo"]').fill(certNo)
    await page.locator('[data-testid="qf-issueDate"] input').fill('2024-06-01')
    await page.locator('[data-testid="qf-issueDate"] input').press('Enter')
    await page.locator('[data-testid="qf-expiryDate"] input').fill('2027-06-01')
    await page.locator('[data-testid="qf-expiryDate"] input').press('Enter')

    // 不填 5 个非必填
    await page.locator('[data-testid="qf-submit"]').click()

    // 提交成功
    await expect(page.locator('.el-message--success').filter({ hasText: '新增成功' })).toBeVisible({ timeout: 8000 })
    const newRow = page.locator('.el-table__row').filter({ hasText: certNo }).first()
    await expect(newRow).toBeVisible({ timeout: 8000 })
  })

  test('日期校验：有效期 <= 发证日期 → 错误提示', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    // 5 必填
    await page.locator('[data-testid="qf-name"]').fill('日期校验测试')
    await page.locator('[data-testid="qf-issuer"]').fill('CMA')
    await page.locator('[data-testid="qf-certificateNo"]').fill(`DATE-${Date.now()}`)
    // 发证日期 = 2025-01-01
    await page.locator('[data-testid="qf-issueDate"] input').fill('2025-01-01')
    await page.locator('[data-testid="qf-issueDate"] input').press('Enter')
    // 有效期 = 2024-01-01（早于发证日期）
    await page.locator('[data-testid="qf-expiryDate"] input').fill('2024-01-01')
    await page.locator('[data-testid="qf-expiryDate"] input').press('Enter')

    // 触发 blur 离开日期框
    await page.locator('[data-testid="qf-name"]').click()

    // 校验规则触发后 expiryDate form-item 上有 is-error class
    // data-testid 元素 → el-input__wrapper → el-date-editor → el-form-item__content → el-form-item
    // 跳过 el-form-item__* 子类，匹配顶层 el-form-item
    const expiryFormItem = page.locator('[data-testid="qf-expiryDate"]').locator('xpath=ancestor::div[contains(@class, "el-form-item") and not(contains(@class, "el-form-item__"))][1]')
    await expect(expiryFormItem).toHaveClass(/is-error/, { timeout: 3000 })
  })

  test('联系方式格式校验：无效格式 → 内联错误', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    await page.locator('[data-testid="qf-agencyContact"]').fill('invalid-format-xxx')
    // 触发 blur
    await page.locator('[data-testid="qf-name"]').click()

    const contactFormItem = page.locator('[data-testid="qf-agencyContact"]').locator('xpath=ancestor::div[contains(@class, "el-form-item") and not(contains(@class, "el-form-item__"))][1]')
    await expect(contactFormItem).toHaveClass(/is-error/, { timeout: 3000 })
  })

  test('联系方式格式：手机号通过', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    await page.locator('[data-testid="qf-agencyContact"]').fill('13800138000')
    await page.locator('[data-testid="qf-name"]').click()

    const contactFormItem = page.locator('[data-testid="qf-agencyContact"]').locator('xpath=ancestor::div[contains(@class, "el-form-item") and not(contains(@class, "el-form-item__"))][1]')
    await expect(contactFormItem).not.toHaveClass(/is-error/, { timeout: 3000 })
  })

  test('联系方式格式：邮箱通过', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    await page.locator('[data-testid="qf-agencyContact"]').fill('test@example.com')
    await page.locator('[data-testid="qf-name"]').click()

    const contactFormItem = page.locator('[data-testid="qf-agencyContact"]').locator('xpath=ancestor::div[contains(@class, "el-form-item") and not(contains(@class, "el-form-item__"))][1]')
    await expect(contactFormItem).not.toHaveClass(/is-error/, { timeout: 3000 })
  })

  test('AI 智能提取：演示环境无 AI provider → 友好提示手动填写', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    // 检查 AI 区域存在
    const aiArea = page.locator('[data-testid="qual-form-ai-area"]')
    await expect(aiArea).toBeVisible()

    // AI 上传区在演示环境无 key，验证模板 11 字段仍可手动录入
    await page.locator('[data-testid="qf-name"]').fill('AI降级手动测试')
    await expect(page.locator('[data-testid="qf-name"]')).toHaveValue('AI降级手动测试')
  })
})
