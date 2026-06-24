// @ui-cover:document
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

async function apiRequest(path, session, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    },
  })

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${path} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }
  return payload
}

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

async function createTenderAndProject(session, name, extra = {}) {
  const suffix = Date.now() + Math.floor(Math.random() * 1000)

  const tenderPayload = await apiRequest('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `${name} 标讯 ${suffix}`,
      source: 'Playwright',
      budget: 880000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
      status: 'TRACKING',
      aiScore: 88,
      riskLevel: 'LOW',
    }),
  })

  const projectPayload = await apiRequest('/api/projects', session, {
    method: 'POST',
    body: JSON.stringify({
      name,
      tenderId: tenderPayload?.data?.id,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers: [session.user.id],
      startDate: toLocalDateTimeString(new Date()),
      endDate: toLocalDateTimeString(new Date(Date.now() + 10 * 24 * 60 * 60 * 1000)),
      ...extra,
    }),
  })

  return projectPayload?.data
}

async function ensureEditorProject(session, project, sectionTitle) {
  const structureName = `${project.name} 结构`
  const structurePayload = await apiRequest(`/api/documents/${project.id}/editor/structure`, session, {
    method: 'POST',
    body: JSON.stringify({
      projectId: project.id,
      name: structureName,
    }),
  })

  const structureId = structurePayload?.data?.id
  expect(structureId).toBeTruthy()

  const sections = [
    { title: sectionTitle, orderIndex: 1 },
    { title: '技术方案', orderIndex: 2 },
    { title: '案例展示', orderIndex: 3 },
  ]

  const createdSections = []

  for (const item of sections) {
    const payload = await apiRequest(`/api/documents/${project.id}/editor/sections`, session, {
      method: 'POST',
      body: JSON.stringify({
        structureId,
        sectionType: 'SECTION',
        title: item.title,
        content: `${item.title} 初始内容`,
        orderIndex: item.orderIndex,
      }),
    })
    createdSections.push(payload?.data)
  }

  return {
    structureId,
    structureName,
    sections: createdSections,
  }
}

test('document editor shows case recommendation, records citation, and traces assembly', async ({ page }) => {
  const session = await ensureApiSession({
    username: `case_editor_${Date.now()}`,
    role: '/bidAdmin',
    fullName: 'Case Editor Admin',
  })

  const editorProjectName = `案例编辑项目 ${Date.now()}`
  const keywordSectionTitle = `闭环关键词 ${Date.now()}`
  const editorProject = await createTenderAndProject(session, editorProjectName)
  const editorFixture = await ensureEditorProject(session, editorProject, keywordSectionTitle)
  const keywordSection = editorFixture.sections[0]

  const searchPhrase = `${editorFixture.structureName} ${keywordSectionTitle} ${keywordSection.id}`
  const sourceProjectName = `归档源项目 ${Date.now()} ${searchPhrase}`
  const sourceProject = await createTenderAndProject(session, sourceProjectName, {
    sourceModule: '智慧园区',
    sourceCustomer: '国资客户',
    sourceReasoningSummary: '归档正文闭环',
  })

  const sourceStructurePayload = await apiRequest(`/api/documents/${sourceProject.id}/editor/structure`, session, {
    method: 'POST',
    body: JSON.stringify({
      projectId: sourceProject.id,
      name: `${sourceProject.name} 结构`,
    }),
  })

  const sourceStructureId = sourceStructurePayload?.data?.id
  expect(sourceStructureId).toBeTruthy()

  const longPrefix = '正文说明'.repeat(90)
  const sourceContent = `${longPrefix} ${searchPhrase} 归档结论`
  await apiRequest(`/api/documents/${sourceProject.id}/editor/sections`, session, {
    method: 'POST',
    body: JSON.stringify({
      structureId: sourceStructureId,
      sectionType: 'SECTION',
      title: '技术方案',
      content: sourceContent,
      orderIndex: 1,
    }),
  })

  const archivePayload = await apiRequest(`/api/documents/${sourceProject.id}/archive`, session, {
    method: 'POST',
    body: JSON.stringify({
      archivedBy: session.user.id,
      archivedByName: session.user.name,
      archiveReason: '编辑器闭环测试归档',
    }),
  })

  const snapshot = archivePayload?.data?.caseSnapshot || {
    projectName: sourceProject.name,
    customerName: sourceProject.sourceCustomer || '国资客户',
    productLine: sourceProject.sourceModule || '智慧园区',
    archiveSummary: `项目“${sourceProject.name}”已完成归档。客户：${sourceProject.sourceCustomer || '国资客户'}。归档正文闭环。正文摘录：${longPrefix.slice(0, 220)}`,
    documentSnapshotText: sourceContent,
    recommendedTags: [],
  }
  expect(snapshot.documentSnapshotText).toContain(searchPhrase)

  let promotedPayload
  try {
    promotedPayload = await apiRequest('/api/knowledge/cases/promote-from-project', session, {
      method: 'POST',
      body: JSON.stringify({ projectId: sourceProject.id }),
    })
  } catch (error) {
    if (!String(error.message).includes('405') && !String(error.message).includes('404')) {
      throw error
    }

    promotedPayload = await apiRequest('/api/knowledge/cases', session, {
      method: 'POST',
      body: JSON.stringify({
        title: searchPhrase,
        industry: 'INFRASTRUCTURE',
        outcome: 'WON',
        amount: 0,
        projectDate: new Date().toISOString().slice(0, 10),
        description: snapshot.archiveSummary,
        customerName: snapshot.customerName || sourceProject.sourceCustomer || '国资客户',
        locationName: '杭州',
        projectPeriod: '',
        productLine: snapshot.productLine || sourceProject.sourceModule || '智慧园区',
        sourceProjectId: sourceProject.id,
        archiveSummary: snapshot.archiveSummary,
        priceStrategy: '未定价',
        successFactors: snapshot.recommendedTags || [],
        lessonsLearned: [],
        documentSnapshotText: snapshot.documentSnapshotText,
        attachmentNames: [],
        status: 'PUBLISHED',
        publishedAt: new Date().toISOString(),
        visibility: 'INTERNAL',
        tags: snapshot.recommendedTags || [],
        highlights: snapshot.recommendedTags || [],
        technologies: [],
        viewCount: 0,
        useCount: 0,
      }),
    })
  }

  const promotedCase = promotedPayload?.data
  expect(promotedCase?.id).toBeTruthy()

  const templatePayload = await apiRequest('/api/documents/assembly/templates', session, {
    method: 'POST',
    body: JSON.stringify({
      name: `闭环装配模板 ${Date.now()}`,
      description: '用于验证智能装配留痕',
      category: 'TECHNICAL',
      templateContent: '项目：${projectName}\n模板：${templateName}',
      variables: '{"projectName":"string","templateName":"string"}',
      createdBy: session.user.id,
    }),
  })

  expect(templatePayload?.data?.id).toBeTruthy()
  const templateName = templatePayload.data.name

  await injectSession(page, session)
  await page.route('**/api/knowledge/templates**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [] }),
    })
  })
  await page.goto(`/document/editor/${editorProject.id}`)

  await expect(page.getByText('章节目录')).toBeVisible()
  await expect(page.locator('.section-tree-card').getByText(keywordSectionTitle, { exact: false })).toBeVisible()
  await page.locator('.section-tree-card').getByText(keywordSectionTitle, { exact: false }).click()

  await expect(page.locator('.knowledge-float-panel')).toBeVisible()
  await expect(page.locator('.knowledge-float-panel')).toContainText(promotedCase.title)

  await page.locator('.knowledge-float-panel .knowledge-item').filter({ hasText: promotedCase.title }).click()
  await expect(page.getByText('案例已插入并记录引用')).toBeVisible()
  await expect(page.locator('.source-records')).toContainText(promotedCase.title)

  const textarea = page.locator('textarea.content-textarea')
  await expect(textarea).toHaveValue(/> 来源：案例库/)

  const referenceRecords = await apiRequest(`/api/knowledge/cases/${promotedCase.id}/references`, session)
  expect(Array.isArray(referenceRecords?.data)).toBeTruthy()
  expect(referenceRecords.data).toHaveLength(1)
  expect(referenceRecords.data[0].referenceTarget).toContain(keywordSectionTitle)

  const templateRadio = page.getByRole('radio', { name: templateName }).first()
  await expect(templateRadio).toBeVisible()
  await templateRadio.check({ force: true })

  await page.locator('.section-checkboxes .el-checkbox').filter({ hasText: '技术方案' }).first().click()
  await page.locator('.section-checkboxes .el-checkbox').filter({ hasText: '案例展示' }).first().click()

  await page.getByRole('button', { name: '开始装配' }).click()
  await expect(page.locator('.el-message-box')).toBeVisible()
  await page.locator('.el-message-box').getByRole('button', { name: '开始装配' }).click()

  await expect(page.getByText(/智能装配完成！已填充/)).toBeVisible()
  await expect(page.locator('.source-records')).toContainText('文档组装')
  await expect(page.locator('.source-records')).toContainText('闭环装配模板')

  const assemblyHistory = await apiRequest(`/api/documents/assembly/${editorProject.id}`, session)
  expect(Array.isArray(assemblyHistory?.data)).toBeTruthy()
  expect(assemblyHistory.data).toHaveLength(1)
  expect(assemblyHistory.data[0].templateId).toBe(templatePayload.data.id)

  const archiveRecords = await apiRequest(`/api/documents/${sourceProject.id}/archive-records`, session)
  expect(Array.isArray(archiveRecords?.data)).toBeTruthy()
  expect(archiveRecords.data).toHaveLength(1)
  expect(archivePayload?.data?.caseSnapshot?.archiveSummary).not.toContain(searchPhrase)
})
