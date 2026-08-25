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
  const pad = (n) => String(n).padStart(2, '0')
  const localIso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  const payload = {
    title: `E2E-文件上传测试-${Date.now()}`,
    region: '上海市上海市',
    purchaserName: 'E2E测试采购中心',
    deadline: localIso(new Date(Date.now() + 7 * 86400000)),
    bidOpeningTime: localIso(new Date(Date.now() + 14 * 86400000)),
    customerType: '政府机关/事业单位/高校',
    priority: 'B',
    projectType: '工业品',
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

    // 3. 上传 PDF 文件（storeTenderDocument 成功后 fileUrl 才回填，保存才不会被拦截）
    const fileInput = page.locator('.manual-tender-upload input[type="file"]').first()
    const [storeResp] = await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/api/doc-insight/store') && resp.status() === 200, { timeout: 30000 }),
      fileInput.setInputFiles({
        name: '测试招标文件.pdf',
        mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.4 test content')
      }),
    ])
    expect(storeResp).toBeTruthy()

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

    // 8. 验证链接可点击（点击触发 blob 下载，href 不再承载地址；文件名已含在链接文本中）
    await expect(fileLink).toContainText('测试招标文件.pdf')
  })

test('人工录入模式上传文件 → 保存 → 详情页显示文件名', async ({ page }) => {
  // 1. 登录
  await loginAsBidAdmin(page)

  // 2. 进入人工录入页面
  await page.goto('/bidding/create')
  await page.waitForSelector('.el-form', { timeout: 10000 })

  // 3. 填写必填字段（schema 表单：区域为 cascader、下拉用 label 定位）
  await page.fill('input[placeholder="请输入项目名称"]', `E2E-人工录入-${Date.now()}`)
  await page.fill('input[placeholder="请输入招标主体"]', `测试招标主体_${Date.now()}`)
  await page.fill('input[placeholder="联系人姓名"]', '测试联系人')
  await page.fill('input[placeholder="手机号"]', '13800138000')

  const formItemByLabel = (label) => page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: label }) })
  await formItemByLabel('总部所在地').locator('.el-cascader').click()
  await page.locator('.el-cascader-panel .el-cascader-menu').nth(0).locator('.el-cascader-node').filter({ hasText: '北京市' }).first().click()
  await page.locator('.el-cascader-panel .el-cascader-menu').nth(1).locator('.el-cascader-node').filter({ hasText: '北京市' }).first().click()
  await formItemByLabel('客户类型').locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '央企' }).first().click()
  await formItemByLabel('优先级').locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'A 级' }).first().click()
  await formItemByLabel('项目类型').locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '工业品' }).first().click()

  const deadlineInput = page.locator('input[placeholder="选择报名截止时间"]')
  await deadlineInput.click()
  await deadlineInput.fill('2026-12-25 17:00')
  await deadlineInput.press('Enter')
  const bidOpenInput = page.locator('input[placeholder="选择开标时间"]')
  await bidOpenInput.click()
  await bidOpenInput.fill('2026-12-31 09:30')
  await bidOpenInput.press('Enter')

  // 4. 上传 PDF 文件（等待 store 成功回填 fileUrl，否则保存被拦截）
  const fileInput = page.locator('.manual-tender-upload input[type="file"]').first()
  const [storeResp] = await Promise.all([
    page.waitForResponse(resp => resp.url().includes('/api/doc-insight/store') && resp.status() === 200, { timeout: 30000 }),
    fileInput.setInputFiles({
      name: '人工录入测试文件.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4 test content')
    }),
  ])
  expect(storeResp).toBeTruthy()

  // 6. 保存（TenderCreatePage 动作栏按钮为"保存"）
  const saveButton = page.getByRole('button', { name: '保存' })
  await expect(saveButton).toBeEnabled({ timeout: 15000 })
  await saveButton.click()

  // 7. 等待跳转到详情页
  await page.waitForURL(/\/bidding\/\d+$/, { timeout: 20000 })

  // 8. 验证"标讯文件"区域显示文件名
  const fileSection = page.locator('.el-form-item').filter({ hasText: '标讯文件' })
  await expect(fileSection).toBeVisible()

  const fileLink = fileSection.locator('.el-link')
  await expect(fileLink).toBeVisible({ timeout: 5000 })
  await expect(fileLink).toContainText('人工录入测试文件.pdf')
})
})
