// @ui-cover:project
import { expect, test } from '@playwright/test'
import { createAuthenticatedSession, createProjectFixture } from './support/project-fixtures.js'

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
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto(`/project/${project.id}`)
  await expect(page).toHaveURL(/\/project\/\d+$/)
  await expect(page.locator('.project-detail-page')).toBeVisible()
  return project
}

test('project detail header keeps long title and actions from overlapping', async ({ page }) => {
  await openProjectDetail(page, 1024, 768)

  const title = page.locator('.project-name')
  const actions = page.locator('.header-actions')
  const header = page.locator('.page-header')

  await expect(title).toBeVisible()
  await expect(actions).toBeVisible()

  const titleBox = await title.boundingBox()
  const actionsBox = await actions.boundingBox()
  const headerBox = await header.boundingBox()

  expect(titleBox).toBeTruthy()
  expect(actionsBox).toBeTruthy()
  expect(headerBox).toBeTruthy()
  expect(intersects(titleBox, actionsBox)).toBe(false)
  expect(actionsBox.x + actionsBox.width).toBeLessThanOrEqual(headerBox.x + headerBox.width + 1)
})

test('project detail assistant rail stacks without covering content on medium viewport', async ({ page }) => {
  await openProjectDetail(page, 1152, 900)

  const main = page.locator('.main-content').first()
  const sidebar = page.locator('.right-sidebar')
  const content = page.locator('.detail-content')

  await expect(main).toBeVisible()
  await expect(sidebar).toBeVisible()

  const mainBox = await main.boundingBox()
  const sidebarBox = await sidebar.boundingBox()
  const contentBox = await content.boundingBox()

  expect(mainBox).toBeTruthy()
  expect(sidebarBox).toBeTruthy()
  expect(contentBox).toBeTruthy()
  expect(intersects(mainBox, sidebarBox)).toBe(false)
  expect(sidebarBox.width).toBeLessThanOrEqual(contentBox.width + 1)
  expect(sidebarBox.y).toBeGreaterThan(mainBox.y)
})

test('project detail main column no longer renders expense summary card', async ({ page }) => {
  await openProjectDetail(page, 1152, 900)

  // 费用管理汇总模块已删除，不应出现在主内容区
  const main = page.locator('.main-content').first()
  await expect(main).toBeVisible()
  await expect(main.locator('.project-expense-card')).toHaveCount(0)
})
