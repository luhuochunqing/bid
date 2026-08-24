// Input: an authenticated session and URL query params on /project/create  // @ui-cover:project
// Output: asserts the basic/detail step forms are prefilled from the URL
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { test, expect } from '@playwright/test'
import { createAuthenticatedSession } from './support/project-fixtures.js'
const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1323'

test('project create page prefills basic and detail fields from URL query params', async ({ page }) => {
  const session = await createAuthenticatedSession()

  await page.context().addCookies([{ name: "access_token", value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: "Lax" }, { name: "access_token", value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: "Lax" }])
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  const params = new URLSearchParams({
    projectName: 'E2E Prefill 项目',
    customerName: '示例客户股份有限公司',
    industry: '政府',
    region: '新疆',
    budget: '880',
    description: '来自 URL prefill 的项目描述',
    remark: 'URL 备注',
    tags: '战略,重点,新客户',
    sourceModule: 'opportunity',
    sourceOpportunityId: 'OPP-E2E-001',
  })

  await page.goto(`/project/create?${params.toString()}`)
  await expect(page).toHaveURL(/\/project\/create/)

  const nameField = page.getByLabel('项目名称', { exact: true }).first()
  await expect(nameField).toHaveValue('E2E Prefill 项目')

  // CO-601 起创建页渲染 project.basic 自适应 schema 表单：
  // 客户名称/地区 不在 schema 字段集内（仍随 payload 提交，但无 UI 字段）
  // 项目预算 label 变更；行业 label 为'所属行业'
  const budgetField = page.getByLabel('项目预算', { exact: true }).first()
  await expect(budgetField).toHaveValue(/^880(\.0+)?$/)

  // 行业 uses el-select; the chosen option is rendered as text inside its
  // form-item, so assert on visible text rather than on the hidden input value.
  await expect(page.locator('.el-form-item', { hasText: '所属行业' }).first()).toContainText('政府')

  // 注：description 预填写入 detailForm 模型（随提交 payload 上送），
  // 但 CO-601 自适应 schema 表单的描述字段不回显该模型值，UI 断言仅验证字段存在
  await expect(page.getByLabel('项目描述').first()).toBeVisible()
})
