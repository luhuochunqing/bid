import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * 标讯文件上传回显 E2E 测试。
 * 覆盖：编辑模式上传文件 → 保存 → 详情页显示文件名链接。
 *
 * 回归防护：
 * - TenderBasicInfoTab.vue on-change 绑定必须使用函数引用，不能用内联表达式
 * - useTenderAiParse.js handleFileChange 必须正确设置 sourceDocumentFileUrl
 * - BasicInfoReadOnly.vue 必须正确显示文件名链接
 */

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_upload_${suffix}`,
    role: '/bidAdmin',
    fullName: 'E2E 文件上传测试'
  })
  await injectSession(page, session)
  return session
}

async function createTenderViaApi(session) {
  const payload = {
    title: `E2E-文件上传测试-${Date.now()}`,
    region: '上海',
    purchaserName: 'E2E测试采购中心',
    deadline: new Date(Date.now() + 7 * 86400000).toISOString(),
    bidOpeningTime: new Date(Date.now() + 14 * 86400000).toISOString(),
    customerType: '政府机关',
    priority: 'B',
    source: '人工录入',
    contactName: '测试联系人',
    contactPhone: '13800138000',
    status: 'PENDING_ASSIGNMENT'
  }

  const response = await fetch(`${apiBaseUrl}/api/tenders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Cookie': `access_token=${session.token}`
    },
    body: JSON.stringify(payload)
  })
  const result = await response.json()
  if (!result?.success) {
    throw new Error(`创建标讯失败: ${JSON.stringify(result)}`)
  }
  return result.data.id
}

test.describe('标讯文件上传回显', () => {

  test('编辑模式上传文件 → 保存 → 详情页显示文件名', async ({ page }) => {
    // 1. 登录并创建标讯
    const session = await loginAsBidAdmin(page)
    const tenderId = await createTenderViaApi(session)

    // 2. 进入编辑模式
    await page.goto(`/bidding/create?edit=${tenderId}`)
    await page.waitForSelector('.el-form', { timeout: 10000 })

    // 3. 上传 PDF 文件
    const fileInput = page.locator('.el-upload input[type="file"]').first()
    await fileInput.setInputFiles({
      name: '测试招标文件.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4 test content')
    })

    // 4. 等待上传完成（应看到成功提示或 AI 解析提示）
    // 注意：AI 解析可能超时，但文件存储应成功
    await page.waitForResponse(resp => resp.url().includes('/api/doc-insight/') && resp.status() === 200)
      .catch(() => {/* AI 解析可能超时，不阻塞测试 */})

    // 5. 保存标讯
    const saveButton = page.getByRole('button', { name: '保存' })
    await saveButton.click()

    // 6. 等待跳转到详情页
    await page.waitForURL(new RegExp(`/bidding/${tenderId}`), { timeout: 15000 })

    // 7. 验证"标讯文件"区域显示文件名（不是 "-"）
    const fileSection = page.locator('.el-form-item').filter({ hasText: '标讯文件' })
    await expect(fileSection).toBeVisible()

    // 应显示文件名链接，不是 "-"
    const fileLink = fileSection.locator('.el-link')
    await expect(fileLink).toBeVisible({ timeout: 5000 })
    await expect(fileLink).toContainText('测试招标文件.pdf')

    // 8. 验证链接可点击（href 包含 /api/doc-insight/download）
    const href = await fileLink.getAttribute('href')
    expect(href).toContain('/api/doc-insight/download')
    expect(href).toContain('doc-insight%3A')
  })

  test('人工录入模式上传文件 → 保存 → 详情页显示文件名', async ({ page }) => {
    // 1. 登录
    await loginAsBidAdmin(page)

    // 2. 进入人工录入页面
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    // 3. 填写必填字段
    await page.fill('input[placeholder="请输入项目名称"]', `E2E-人工录入-${Date.now()}`)
    await page.fill('input[placeholder="请输入招标主体"]', '测试招标主体')
    await page.fill('input[placeholder="联系人姓名"]', '测试联系人')
    await page.fill('input[placeholder="手机号"]', '13800138000')

    // 4. 上传 PDF 文件
    const fileInput = page.locator('.el-upload input[type="file"]').first()
    await fileInput.setInputFiles({
      name: '人工录入测试文件.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4 test content')
    })

    // 5. 等待上传完成
    await page.waitForResponse(resp => resp.url().includes('/api/doc-insight/') && resp.status() === 200)
      .catch(() => {/* AI 解析可能超时，不阻塞测试 */})

    // 6. 保存
    const saveButton = page.getByRole('button', { name: '保存入库' })
    await saveButton.click()

    // 7. 等待跳转到详情页
    await page.waitForURL(/\/bidding\/\d+/, { timeout: 15000 })

    // 8. 验证"标讯文件"区域显示文件名
    const fileSection = page.locator('.el-form-item').filter({ hasText: '标讯文件' })
    await expect(fileSection).toBeVisible()

    const fileLink = fileSection.locator('.el-link')
    await expect(fileLink).toBeVisible({ timeout: 5000 })
    await expect(fileLink).toContainText('人工录入测试文件.pdf')
  })
})
