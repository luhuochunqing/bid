// Input: an authenticated session and URL query params on /project/create  // @ui-cover:project
// Output: asserts the basic/detail step forms are prefilled from the URL
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { test, expect } from '@playwright/test'
import { createAuthenticatedSession } from './support/project-fixtures.js'

test('project create page prefills basic and detail fields from URL query params', async ({ page }) => {
  const session = await createAuthenticatedSession()

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

  const customerField = page.getByLabel('客户名称', { exact: true }).first()
  await expect(customerField).toHaveValue('示例客户股份有限公司')

  const regionField = page.getByLabel('地区', { exact: true }).first()
  await expect(regionField).toHaveValue('新疆')

  // el-input-number renders the value with locale formatting (e.g. "880.00"),
  // so match on the numeric value rather than an exact string.
  const budgetField = page.getByLabel('预算(万元)', { exact: true }).first()
  await expect(budgetField).toHaveValue(/^880(\.0+)?$/)

  // 行业 uses el-select; the chosen option is rendered as text inside its
  // form-item, so assert on visible text rather than on the hidden input value.
  await expect(page.locator('.el-form-item', { hasText: '行业' }).first()).toContainText('政府')
})
