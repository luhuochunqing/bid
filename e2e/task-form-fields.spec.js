// Input: Playwright E2E suite for TaskForm field presence and behavior
// @ui-cover:task-form
// Coverage:
//   N1 — 新增任务 drawer 打开，验证所有字段标签存在
//   N2 — 字段标签: "任务执行人" 存在，"负责人" 不存在
//   N3 — 任务创建人: 只读且自动填充当前用户
//   N4 — 交付物上传: 上传组件存在
//   N5 — 完成情况说明: 文本输入可填写
// Pos: e2e/ - Playwright E2E coverage for task form field adjustments
import { test, expect } from '@playwright/test'
import { advanceProjectToDrafting, createAuthenticatedSession, createProjectFixture, ensureRoleUser } from './support/project-fixtures.js'
const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1323'

async function switchToTaskBoardTab(page) {
  // 详情页阶段步进器为 el-steps（.el-step），点击"标书制作"步骤切换 activeStageTab
  await page.locator('.project-stage-timeline .el-step').filter({ hasText: '标书制作' }).click()
  await expect(page.locator('.drafting-tab-content')).toBeAttached({ timeout: 10000 })
}

async function bootstrap(page, label) {
  const session = await createAuthenticatedSession()
  const pl = await ensureRoleUser('bid-projectLeader')
  const project = await createProjectFixture(session, label, { teamMembers: [session.user.id, pl.id] })
  await advanceProjectToDrafting(session, project.id, pl)
  await page.context().addCookies([{ name: "access_token", value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: "Lax" }, { name: "access_token", value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: "Lax" }])
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)
  await page.goto(`/project/${project.id}`)
  await expect(page).toHaveURL(/\/project\/\d+$/)
  // Wait for project detail page to load — use a known element instead of networkidle
  await expect(page.locator('.project-detail-page').first()).toBeAttached({ timeout: 15000 })
  await switchToTaskBoardTab(page)
  return { session, projectId: String(project.id) }
}

test.describe('TaskForm — 字段存在性验证', () => {
  test('N1: 新增任务按钮可点击，打开 drawer', async ({ page }) => {
    await bootstrap(page, 'E2E-TF-N1')
    const addBtn = page.locator('[data-test="add-task-button"]')
    await expect(addBtn).toBeAttached()
    await addBtn.click()
    const drawer = page.getByRole('dialog', { name: '新增任务' })
    await expect(drawer).toBeVisible({ timeout: 5000 })
    await expect(drawer.locator('.el-drawer__title')).toContainText('新增任务')
  })

  test('N2: 字段标签正确 — "任务执行人" 存在，"负责人" 不存在', async ({ page }) => {
    await bootstrap(page, 'E2E-TF-N2')
    await page.locator('[data-test="add-task-button"]').click()
    const drawer = page.getByRole('dialog', { name: '新增任务' })
    await expect(drawer).toBeVisible({ timeout: 5000 })

    // 确认"负责人"不再出现
    await expect(drawer.getByText('负责人')).toHaveCount(0)

    // 确认新字段标签存在
    await expect(drawer.getByText('任务执行人')).toBeVisible()
    await expect(drawer.getByText('任务创建人')).toBeVisible()
    await expect(drawer.getByText('交付物上传')).toBeVisible()
    await expect(drawer.getByText('完成情况说明')).toBeVisible()
  })

  test('N3: 任务创建人为只读且自动填充当前用户', async ({ page }) => {
    const { session } = await bootstrap(page, 'E2E-TF-N3')
    await page.locator('[data-test="add-task-button"]').click()
    const drawer = page.getByRole('dialog', { name: '新增任务' })
    await expect(drawer).toBeVisible({ timeout: 5000 })

    const creatorInput = page.locator('[data-test="task-creator-input"]')
    await expect(creatorInput).toBeVisible()

    // 验证输入框不可编辑
    await expect(creatorInput).toBeDisabled()

    // 验证值等于当前登录用户名
    const inputValue = await creatorInput.inputValue()
    expect(inputValue).toBe(session.user.name)
  })

  test('N4: 交付物上传组件存在', async ({ page }) => {
    await bootstrap(page, 'E2E-TF-N4')
    await page.locator('[data-test="add-task-button"]').click()
    const drawer = page.getByRole('dialog', { name: '新增任务' })
    await expect(drawer).toBeVisible({ timeout: 5000 })

    const uploadBtn = page.locator('[data-test="task-deliverable-upload"]')
    await expect(uploadBtn).toBeVisible()
    // 上传按钮应在 drawer 内（el-upload 触发器 + 内部 button 双元素，取首个可见即可）
    await expect(uploadBtn.getByRole('button', { name: '上传交付物' }).first()).toBeVisible()
  })

  test('N5: 完成情况说明可输入文本', async ({ page }) => {
    await bootstrap(page, 'E2E-TF-N5')
    await page.locator('[data-test="add-task-button"]').click()
    const drawer = page.getByRole('dialog', { name: '新增任务' })
    await expect(drawer).toBeVisible({ timeout: 5000 })

    // 找到 "完成情况说明" 对应的 textarea
    const textarea = page.getByRole('dialog', { name: '新增任务' }).getByRole('textbox', { name: /完成情况说明/ })
    await expect(textarea).toBeVisible()
    await textarea.fill('已完成全部投标文件编制，已通过内部审核。')
    await expect(textarea).toHaveValue('已完成全部投标文件编制，已通过内部审核。')
  })
})
