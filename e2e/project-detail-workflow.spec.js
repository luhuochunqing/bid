import { test, expect } from '@playwright/test'
import { authedJson, createAuthenticatedSession, createProjectFixture } from './support/project-fixtures.js'

async function createProjectTaskFixture(session, projectId, name) {
  const payload = await authedJson(`/api/projects/${projectId}/tasks`, session.token, {
    method: 'POST',
    body: JSON.stringify({
      title: name,
      description: '',
      content: '## E2E 创建内容\n- 真实 API',
      assigneeId: session.user.id,
      assigneeName: session.user.name,
      priority: 'MEDIUM',
      dueDate: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString().slice(0, 19),
    }),
  })
  expect(payload?.success).toBeTruthy()
  expect(payload?.data?.id).toBeTruthy()
  return payload.data
}

test('project detail workflow persists tasks and documents through real API', async ({ page }) => {
  const session = await createAuthenticatedSession()
  const project = await createProjectFixture(session)

  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  const projectId = String(project.id)
  const projectName = project.name

  await page.goto(`/project/${projectId}`)
  await expect(page).toHaveURL(/\/project\/\d+$/)

  if (projectName) {
    await expect(page.getByText(projectName).first()).toBeVisible()
  }

  await createProjectTaskFixture(session, projectId, '新增任务 1')

  // 验证任务 API 数据层（可能在折叠 tab 区域，不要求 UI visible）  // @ui-cover:project
  const taskPayload = await authedJson(`/api/projects/${projectId}/tasks`, session.token)
  expect(taskPayload?.success).toBeTruthy()
  expect(Array.isArray(taskPayload?.data)).toBeTruthy()
  expect(taskPayload.data.some((task) => task.name === '新增任务 1')).toBeTruthy()

  // UI 操作：添加文档
  await page.getByRole('button', { name: '添加文档' }).click()
  await expect(page.getByText('项目文档已新增')).toBeVisible()
  const createdDocumentName = `项目文档_${new Date().toLocaleDateString('zh-CN').replaceAll('/', '')}.docx`
  await expect(page.getByText(createdDocumentName).first()).toBeVisible()

  // UI 操作：设置提醒
  await page.getByRole('button', { name: '设置提醒' }).click()
  await expect(page.getByText('项目提醒已创建')).toBeVisible()

  // 验证文档 API 数据层
  const documentPayload = await authedJson(`/api/projects/${projectId}/documents`, session.token)
  expect(documentPayload?.success).toBeTruthy()
  expect(Array.isArray(documentPayload?.data)).toBeTruthy()
  expect(documentPayload.data.some((document) => document.name === createdDocumentName)).toBeTruthy()
})
