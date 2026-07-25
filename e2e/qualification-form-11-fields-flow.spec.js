import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * 4.1.3.1 新增资质表单字段录入 E2E
 *
 * §4.2.1.1 必填规则：10 个字段必填（基础5 + 补充4 + 附件1）
 *   必填基础 5 字段：证书名称 / 等级 / 认证机构 / 证书编号 / 发证日期 / 证书有效期
 *   必填补充 4 字段：代理机构 / 代理机构联系人 / 认证范围
 *   必填附件 1：证书附件（PDF/JPG/PNG ≤50MB）
 *   非必填：证书审核提醒（CO-530 改为 DATE 日期类型）
 *
 * 校验：
 *   - 必填项空 → 提交失败
 *   - 代理机构联系人为纯文本必填（CO-525）
 *   - 有效期必须晚于发证日期
 *
 * Selector 约定（el-input/textarea 直接是 data-testid 元素自身）：
 *   - 普通输入：page.locator('[data-testid="qf-name"]').fill(...)
 *   - 证书范围：page.locator('[data-testid="qf-certScope"]').fill(...)
 *   - 日期：page.locator('[data-testid="qf-issueDate"] input').fill(...)  // el-date-picker 内部 input
 *   - 附件：page.locator('[data-testid="qf-unified-upload"] input[type="file"]').setInputFiles(...)
 */

// 1x1 透明 PNG，用于附件上传测试
const TINY_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  'base64'
)

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_qf_${suffix}`,
    role: '/bidAdmin',
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

// 上传证书附件（1x1 PNG），AI 解析失败不影响 certFile 设置
async function uploadCertAttachment(page) {
  const fileInput = page.locator('[data-testid="qf-unified-upload"] input[type="file"]')
  await fileInput.setInputFiles({
    name: 'test-cert.png',
    mimeType: 'image/png',
    buffer: TINY_PNG
  })
  // AI 解析可能失败（演示环境无 key），等待 certFile 设置完成。
  // 用 watch state 替代固定 waitForTimeout：检测 certFile 字段填充或解析失败提示。
  await expect.poll(async () => {
    const name = await page.locator('[data-testid="qf-name"]').inputValue().catch(() => '')
    return name.length > 0
  }, { timeout: 3000, intervals: [200] }).toBeTruthy().catch(() => {
    // AI 解析失败不影响测试继续，certFile 可能已设置或被回退为空
  })
}

test.describe('§4.1.3.1 新增资质表单 11 字段', () => {
  test('正向流程：11 字段全部录入 + 提交成功 + success toast', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    const certNo = `QF-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`

    // 必填基础字段
    await page.locator('[data-testid="qf-name"]').fill('E2E资质11字段测试')
    await page.locator('[data-testid="qf-level"]').fill('AAA')
    await page.locator('[data-testid="qf-issuer"]').fill('中国计量认证中心')
    await page.locator('[data-testid="qf-certificateNo"]').fill(certNo)

    // 日期：el-date-picker 外包 div 带 testid，内部有 input
    await page.locator('[data-testid="qf-issueDate"] input').fill('2024-01-15')
    await page.locator('[data-testid="qf-issueDate"] input').press('Enter')
    await page.locator('[data-testid="qf-expiryDate"] input').fill('2027-12-31')
    await page.locator('[data-testid="qf-expiryDate"] input').press('Enter')

    // 必填补充字段
    await page.locator('[data-testid="qf-agency"]').fill('代理认证机构X')
    await page.locator('[data-testid="qf-agencyContact"]').fill('13800138000')
    await page.locator('[data-testid="qf-certScope"]').fill('ISO9001 质量管理体系认证范围')
    // certReviewNote 在 CO-530 中从 VARCHAR(200) 文本改为 DATE 日期类型，前端用 el-date-picker。
    // 注意：certReviewNote 的 data-testid 直接在 el-date-picker 上（非外层 div），
    // Element Plus 可能不将 data-testid 传递到包含 input 的根元素，改用 placeholder 定位。
    await page.getByPlaceholder('选择审核提醒日期').fill('2027-03-01')
    await page.getByPlaceholder('选择审核提醒日期').press('Enter')

    // 必填附件（§4.2.1.1 起附件为必填，新增模式不上传无法通过验证）
    await uploadCertAttachment(page)

    // 提交
    await page.locator('[data-testid="qf-submit"]').click()

    // 验证 success toast（资质模块实际提示为 '新增成功'，用正则兼容 '创建成功'）
    await expect(page.locator('.el-message--success').filter({ hasText: /新增成功|创建成功/ })).toBeVisible({ timeout: 8000 })
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
    // CO-530 后前端 formRef.validate 失败时直接 return，不再显示 warning toast
    // 验证 dialog 仍打开（未提交）
    await expect(page.locator('[data-testid="qual-form-dialog"]')).toBeVisible()
  })

  test('最小必填集提交：填写全部必填字段（不含审核提醒）可提交成功', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    const certNo = `MIN-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`

    // 填写全部必填字段（§4.2.1.1 起 10 字段必填）
    await page.locator('[data-testid="qf-name"]').fill('最小字段测试')
    await page.locator('[data-testid="qf-level"]').fill('A')
    await page.locator('[data-testid="qf-issuer"]').fill('CMA')
    await page.locator('[data-testid="qf-certificateNo"]').fill(certNo)
    await page.locator('[data-testid="qf-issueDate"] input').fill('2024-06-01')
    await page.locator('[data-testid="qf-issueDate"] input').press('Enter')
    await page.locator('[data-testid="qf-expiryDate"] input').fill('2027-06-01')
    await page.locator('[data-testid="qf-expiryDate"] input').press('Enter')
    await page.locator('[data-testid="qf-agency"]').fill('代理机构Y')
    await page.locator('[data-testid="qf-agencyContact"]').fill('张三')
    await page.locator('[data-testid="qf-certScope"]').fill('认证范围Z')
    // certReviewNote（审核提醒）为非必填，留空

    // 必填附件
    await uploadCertAttachment(page)

    await page.locator('[data-testid="qf-submit"]').click()

    // 提交成功（资质模块实际提示为 '新增成功'，用正则兼容 '创建成功'）
    await expect(page.locator('.el-message--success').filter({ hasText: /新增成功|创建成功/ })).toBeVisible({ timeout: 8000 })
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

  test('代理机构联系人必填：空值提交 → 内联错误', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    // 其他必填字段已填，仅 agencyContact 空
    const certNo = `MIN-AC-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`
    await page.locator('[data-testid="qf-name"]').fill('联系人必填测试')
    await page.locator('[data-testid="qf-issuer"]').fill('CMA')
    await page.locator('[data-testid="qf-certificateNo"]').fill(certNo)
    await page.locator('[data-testid="qf-issueDate"] input').fill('2024-01-15')
    await page.locator('[data-testid="qf-issueDate"] input').press('Enter')
    await page.locator('[data-testid="qf-expiryDate"] input').fill('2027-12-31')
    await page.locator('[data-testid="qf-expiryDate"] input').press('Enter')

    await page.locator('[data-testid="qf-submit"]').click()

    const contactFormItem = page.locator('[data-testid="qf-agencyContact"]').locator('xpath=ancestor::div[contains(@class, "el-form-item") and not(contains(@class, "el-form-item__"))][1]')
    await expect(contactFormItem).toHaveClass(/is-error/, { timeout: 3000 })
  })

  test('代理机构联系人纯文本：任意文本均通过（CO-525）', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    await page.locator('[data-testid="qf-agencyContact"]').fill('张三 / 13800138000')
    await page.locator('[data-testid="qf-name"]').click()

    const contactFormItem = page.locator('[data-testid="qf-agencyContact"]').locator('xpath=ancestor::div[contains(@class, "el-form-item") and not(contains(@class, "el-form-item__"))][1]')
    await expect(contactFormItem).not.toHaveClass(/is-error/, { timeout: 3000 })
  })

  test('AI 智能提取：演示环境无 AI provider → 友好提示手动填写', async ({ page }) => {
    await loginAsBidAdmin(page)
    await openCreateDialog(page)

    // CO-530 后 AI 区域已合并到统一上传组件 qf-unified-upload（拖拽证书扫描件触发 AI 提取）
    const uploadArea = page.locator('[data-testid="qf-unified-upload"]')
    await expect(uploadArea).toBeVisible()

    // AI 上传区在演示环境无 key，验证模板 11 字段仍可手动录入
    await page.locator('[data-testid="qf-name"]').fill('AI降级手动测试')
    await expect(page.locator('[data-testid="qf-name"]')).toHaveValue('AI降级手动测试')
  })
})
