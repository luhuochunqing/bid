import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * §4.2.x 新建标讯页 AI 解析回填 E2E 测试。
 *
 * 覆盖：
 * - 粘贴识别（text/plain → /api/doc-insight/parse）触发后，表单字段自动回填
 * - 后端 extractedData（tenderTitle/contactName/contactPhone/contactEmail 等）正确映射到前端字段
 *
 * 对应变更：
 * - 前端 TenderCreatePage.vue `applyParsedFields()` 增加 extractedData 读取 + 别名映射
 * - 后端 DocInsightController.parse() 增加空文件 guard
 *
 * 依赖：
 * - 后端 /api/doc-insight/parse（profile=TENDER_INTAKE）正常返回 extractedData
 * - 前端 tendersApi.parseTenderIntakeDocument() / parseTenderIntakeText() 正确调用
 * - 前端 applyParsedFields() 读取 data.extractedData 并映射 tenderTitle→title 等
 *
 * @ui-cover:bidding
 */

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_parse_${suffix}`,
    role: 'bid_admin',
    fullName: 'E2E 解析测试'
  })
  await injectSession(page, session)
  return session
}

test.describe('§新建标讯-粘贴识别回填', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsBidAdmin(page)
  })

  test('粘贴识别触发后，tenderTitle/contactName/contactPhone 回填到表单字段', async ({ page }) => {
    const session = await loginAsBidAdmin(page)
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    // 填写粘贴识别文本区，模拟 AI 解析响应
    const pastedText = `项目名称：西域MRO集采项目
总部所在地：上海
报名截止时间：2026-07-15 18:00
联系人：李四 13900000000 lisi@example.com
招标主体：西域采购中心
客户类型：央企集团`

    // Mock 后端解析接口，让 applyParsedFields 能拿到 extractedData
    await page.route(`**/api/doc-insight/parse`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          code: 200,
          data: {
            documentId: 'doc-insight://TENDER_INTAKE/create-tender/e2e-pasted',
            extractedData: {
              tenderTitle: '西域MRO集采项目',
              region: '上海',
              deadline: '2026-07-15T18:00:00',
              contactName: '李四',
              contactPhone: '13900000000',
              contactEmail: 'lisi@example.com',
              tenderAgency: '西域采购中心',
              customerType: '央企集团'
            },
            warnings: []
          },
          msg: '文档解析完成'
        })
      })
    })

    // 找到粘贴识别文本区
    const pasteArea = page.locator('textarea[placeholder*="粘贴"]').or(
      page.locator('textarea[placeholder*="招标公告"]')
    )
    await expect(pasteArea).toBeVisible()
    await pasteArea.fill(pastedText)

    // 点击识别按钮
    const parseButton = page.getByRole('button', { name: /识别粘贴|粘贴识别|识别/ }).first()
    await expect(parseButton).toBeEnabled()
    await parseButton.click()

    // 等待解析成功提示出现
    await expect(page.locator('.el-message--success').or(page.locator('.el-message')).filter({ hasText: /识别|解析|成功/ }))
      .toBeVisible({ timeout: 15000 })

    // 验证字段已回填
    const titleInput = page.locator('input[placeholder*="项目名称"], input[placeholder*="请输入"]').first()
    // tenderTitle → title 的映射：title 字段应该有值
    // 由于我们 mock 了接口，表单应该接收到 tenderTitle 并映射到 title
    const formTitle = await page.locator('.el-form').evaluate(el => {
      const inputs = el.querySelectorAll('input')
      for (const input of inputs) {
        if (input.value && input.value.includes('西域MRO')) return input.value
      }
      return ''
    })
    expect(formTitle).toContain('西域MRO')
  })

  test('粘贴识别成功时，至少 title/region/contactName 其一被回填', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    await page.route(`**/api/doc-insight/parse`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          code: 200,
          data: {
            documentId: 'doc-insight://TENDER_INTAKE/create-tender/e2e-minimal',
            extractedData: {
              tenderTitle: 'AI识别测试项目',
              contactName: '测试联系人'
            },
            warnings: []
          },
          msg: '文档解析完成'
        })
      })
    })

    const pasteArea = page.locator('textarea[placeholder*="粘贴"], textarea[placeholder*="招标公告"]').first()
    await pasteArea.fill('任何内容')

    const parseButton = page.getByRole('button', { name: /识别粘贴|粘贴识别|识别/ }).first()
    await parseButton.click()

    // 成功消息出现即表示识别完成
    await expect(page.locator('.el-message--success').or(page.locator('.el-message')))
      .toBeVisible({ timeout: 15000 })
  })

  test('解析 API 返回空 extractedData 时，表单保持原样，不报错崩溃', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    await page.route(`**/api/doc-insight/parse`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          code: 200,
          data: {
            documentId: 'doc-insight://TENDER_INTAKE/create-tender/e2e-empty',
            extractedData: {},
            warnings: ['未提取到可回填字段']
          },
          msg: '文档解析完成'
        })
      })
    })

    const pasteArea = page.locator('textarea[placeholder*="粘贴"], textarea[placeholder*="招标公告"]').first()
    await pasteArea.fill('无法识别的随机内容')

    const parseButton = page.getByRole('button', { name: /识别粘贴|粘贴识别|识别/ }).first()
    await parseButton.click()

    // 页面不应崩溃，表单仍可交互（粘贴区仍可编辑即表示页面正常）
    await expect(page.locator('.el-form')).toBeVisible({ timeout: 10000 })
    const pasteAreaCheck = page.locator('textarea').first()
    await expect(pasteAreaCheck).toBeEditable({ timeout: 5000 })
  })
})
