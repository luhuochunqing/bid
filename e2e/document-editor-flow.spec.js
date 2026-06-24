// @ui-cover:document
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

async function apiRequest(path, session, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
      ...(options.headers || {}),
    },
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${path} -> ${response.status}`)
  }

  return response.json()
}

test('document editor loads backend sections and persists saved content', async ({ page }) => {
  const session = await ensureApiSession({
    username: `document_editor_${Date.now()}`,
    role: '/bidAdmin',
    fullName: 'Document Editor Admin'
  })
  const projectId = 960100 + Date.now() % 10000

  const structurePayload = await apiRequest(`/api/documents/${projectId}/editor/structure`, session, {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      name: `ERI-96 文档结构 ${projectId}`,
    }),
  })

  const structureId = structurePayload?.data?.id
  expect(structureId).toBeTruthy()

  const sectionPayload = await apiRequest(`/api/documents/${projectId}/editor/sections`, session, {
    method: 'POST',
    body: JSON.stringify({
      structureId,
      sectionType: 'SECTION',
      title: '技术说明',
      content: '初始内容',
      orderIndex: 1,
    }),
  })

  const sectionId = sectionPayload?.data?.id
  expect(sectionId).toBeTruthy()

  await injectSession(page, session)

  await page.goto(`/document/editor/${projectId}`)
  await expect(page.getByText('章节目录')).toBeVisible()
  await expect(page.locator('.section-tree-card').getByText('技术说明', { exact: true })).toBeVisible()
  await expect(page.locator('.editor-card').getByText('技术说明', { exact: true })).toBeVisible()

  const textarea = page.locator('textarea.content-textarea')
  await expect(textarea).toHaveValue('初始内容')

  const updatedContent = `更新内容 ${projectId}`
  await textarea.fill(updatedContent)
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('保存成功')).toBeVisible()

  await page.reload()
  await expect(page.locator('.editor-card').getByText('技术说明', { exact: true })).toBeVisible()
  await expect(page.locator('textarea.content-textarea')).toHaveValue(updatedContent)
})

test('document editor inserts case knowledge and records backend reference', async ({ page }) => {
  const session = await ensureApiSession({
    username: `document_editor_case_${Date.now()}`,
    role: '/bidAdmin',
    fullName: 'Document Editor Case Admin'
  })
  const projectId = 970100 + Date.now() % 10000
  const structureName = `ERI-97 文档结构 ${projectId}`

  const structurePayload = await apiRequest(`/api/documents/${projectId}/editor/structure`, session, {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      name: structureName
    })
  })
  const structureId = structurePayload?.data?.id
  expect(structureId).toBeTruthy()

  const sectionPayload = await apiRequest(`/api/documents/${projectId}/editor/sections`, session, {
    method: 'POST',
    body: JSON.stringify({
      structureId,
      sectionType: 'SECTION',
      title: '技术说明',
      content: '等待插入案例',
      orderIndex: 1
    })
  })
  const sectionId = sectionPayload?.data?.id
  expect(sectionId).toBeTruthy()
  const editorKeyword = `${structureName} 技术说明 ${sectionId}`

  const casePayload = await apiRequest('/api/knowledge/cases', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `${editorKeyword} 案例`,
      industry: 'INFRASTRUCTURE',
      outcome: 'WON',
      amount: 660,
      projectDate: '2025-02-01',
      description: `用于技术说明章节的真实案例引用 ${editorKeyword}`,
      customerName: '编辑器测试客户',
      locationName: '杭州',
      projectPeriod: '2025-02-01 - 2025-12-31',
      productLine: '智慧园区',
      tags: ['技术说明', '智慧园区'],
      highlights: ['章节案例插入'],
      technologies: ['Vue', 'Spring Boot']
    })
  })
  const caseId = casePayload?.data?.id
  expect(caseId).toBeTruthy()

  await injectSession(page, session)
  await page.goto(`/document/editor/${projectId}`)
  await expect(page.locator('.section-tree-card').getByText('技术说明', { exact: true })).toBeVisible()
  await page.locator('.section-tree-card').getByText('技术说明', { exact: true }).click()

  await expect(page.getByText('知识库推荐')).toBeVisible()
  await page.getByText(`${editorKeyword} 案例`).click()
  await expect(page.getByText('案例已插入并记录引用')).toBeVisible()
  await expect(page.getByText('来源记录')).toBeVisible()
  await expect(page.getByText('案例库 · ' + `${editorKeyword} 案例`)).toBeVisible()

  const referencesPayload = await apiRequest(`/api/knowledge/cases/${caseId}/references`, session)
  expect(Array.isArray(referencesPayload?.data)).toBeTruthy()
  expect(referencesPayload.data.length).toBeGreaterThan(0)
  expect(referencesPayload.data[0].referenceContext || '').toContain('文档编辑器插入案例')
})
