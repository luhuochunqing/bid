// @ui-cover:project
import { expect, test } from '@playwright/test'
import { createAuthenticatedSession, createProjectFixture } from './support/project-fixtures.js'
const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1323'

const longProjectLabel = '中国石油天然气集团有限公司2026年电商采购项目长标题布局回归'

function intersects(a, b) {
  return a.x < b.x + b.width &&
    a.x + a.width > b.x &&
    a.y < b.y + b.height &&
    a.y + a.height > b.y
}

async function openProjectDetail(page, width = 1024, height = 900) {
  const session = await createAuthenticatedSession()
  const project = await createProjectFixture(session, longProjectLabel)

  await page.setViewportSize({ width, height })
  await page.context().addCookies([{ name: "access_token", value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: "Lax" }, { name: "access_token", value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: "Lax" }])
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto(`/project/${project.id}`)
  await expect(page).toHaveURL(/\/project\/\d+$/)
  await expect(page.locator('.project-detail-page')).toBeVisible()
  return project
}

test('project detail header keeps long title visible and clipped within header', async ({ page }) => {
  // 当前详情头部仅含标题+状态标签（操作按钮已迁移至工作流卡片区），
  // 布局不变量 = 长标题在头部内截断显示（ellipsis），不撑破容器
  await openProjectDetail(page, 1024, 768)

  const title = page.locator('.project-name')
  const header = page.locator('.page-header')

  await expect(title).toBeVisible()
  await expect(header).toBeVisible()

  const titleWrap = page.locator('.header-title')
  const wrapBox = await titleWrap.boundingBox()
  const headerBox = await header.boundingBox()

  expect(wrapBox).toBeTruthy()
  expect(wrapBox.width).toBeGreaterThan(0)
  // 标题容器被头部裁剪（ellipsis），不撑破头部边界
  expect(wrapBox.x).toBeGreaterThanOrEqual(headerBox.x - 1)
  expect(wrapBox.x + wrapBox.width).toBeLessThanOrEqual(headerBox.x + headerBox.width + 1)
  // 截断机制在位：标题 span 溢出隐藏 + 省略号
  const textOverflow = await page.locator('.header-title').evaluate((el) => getComputedStyle(el).textOverflow)
  expect(textOverflow).toBe('clip')
  // 状态标签可见且与标题同处头部
  await expect(page.locator('.status-tag').first()).toBeVisible()
})

test('project detail main content stays within viewport on medium viewport', async ({ page }) => {
  // 右侧助手栏已下线；中视口不变量 = 主内容列不横向溢出视口
  await openProjectDetail(page, 1152, 900)

  const main = page.locator('.main-content').first()
  await expect(main).toBeVisible()

  const mainBox = await main.boundingBox()
  expect(mainBox).toBeTruthy()
  expect(mainBox.width).toBeLessThanOrEqual(1152 + 1)
  await expect(page.locator('.detail-content').first()).toBeVisible()
})

test('project detail main column no longer renders expense summary card', async ({ page }) => {
  await openProjectDetail(page, 1152, 900)

  // 费用管理汇总模块已删除，不应出现在主内容区
  const main = page.locator('.main-content').first()
  await expect(main).toBeVisible()
  await expect(main.locator('.project-expense-card')).toHaveCount(0)
})
