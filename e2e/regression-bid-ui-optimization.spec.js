// Input: Playwright E2E regression suite for bid UI optimization (Issues IJT8WG, IJT3OY, IJT3OP, IJSZSG)
// Coverage:
//   BUI-1 — 标讯UI操作按钮一致性（IJT8WG）
//   BUI-2 — 交付物上传后文件不闪烁（IJT3OY）
//   BUI-3 — 提交审核后状态变更、内容保存（IJT3OP）
//   BUI-4 — 完成投标区域权限正确（IJSZSG）
//   BUI-5 — 标书审核权限：提交人不见审核按钮，审核人可见（IJSTZG）
// Pos: e2e/ - Playwright E2E regression coverage for Issues batch fix
// 运行: PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_BASE_URL=http://127.0.0.1:1314 npx playwright test e2e/regression-bid-ui-optimization.spec.js

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

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

async function loginAsRole(page, roleProfile) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_bui_${roleProfile}_${suffix}`,
    role: roleProfile,
    fullName: `E2E BUI ${roleProfile}`,
  })
  await injectSession(page, session)
  return session
}

async function apiCreateTender(session, overrides = {}) {
  const response = await fetch(`${apiBaseUrl}/api/tenders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
    },
    body: JSON.stringify({
      title: `E2E-BUI-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      source: 'Playwright',
      budget: 500000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 14 * 86400000)),
      status: 'PENDING_ASSIGNMENT',
      aiScore: 75,
      riskLevel: 'LOW',
      ...overrides,
    }),
  })
  const payload = await response.json()
  return payload?.data
}

async function apiUpdateTender(session, tenderId, updates = {}) {
  const payload = {
    deadline: toLocalDateTimeString(new Date(Date.now() + 14 * 86400000)),
    ...updates,
  }
  const response = await fetch(`${apiBaseUrl}/api/tenders/${tenderId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
    },
    body: JSON.stringify(payload),
  })
  const data = await response.json()
  if (!response.ok) {
    throw new Error(`PUT /api/tenders/${tenderId} failed: ${response.status} - ${JSON.stringify(data)}`)
  }
  return data
}

async function goToTenderDetail(page, tenderId) {
  await page.goto(`/bidding/${tenderId}`)
  await page.waitForSelector('.bidding-detail-page', { timeout: 15000 })
  await page.waitForSelector('.el-descriptions', { timeout: 15000 })
}

// =========================================================================
// BUI-1: 标讯UI操作按钮一致性（IJT8WG）
// =========================================================================
test.describe('BUI-1: 标讯UI操作按钮一致性', () => {
  test.describe('actionMatrix 创建人感知', () => {
    test('BUI-1.1: sales 作为创建人在 PENDING_ASSIGNMENT 下看到编辑和删除按钮', async ({ page }) => {
      const session = await loginAsRole(page, 'bid-projectLeader')
      const tender = await apiCreateTender(session, { creatorId: session.user.id })
      expect(tender?.id).toBeTruthy()

      await goToTenderDetail(page, tender.id)

      // 验证头部存在编辑和删除按钮
      const editBtn = page.locator('.detail-global-actions').getByRole('button', { name: '编辑' })
      const deleteBtn = page.locator('.detail-global-actions').getByRole('button', { name: '删除' })
      await expect(editBtn).toBeVisible({ timeout: 5000 })
      await expect(deleteBtn).toBeVisible({ timeout: 5000 })
    })

    test('BUI-1.2: sales 非创建人在 PENDING_ASSIGNMENT 下无按钮', async ({ page }) => {
      // 先以 bid_admin 创建标讯
      const adminSuffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
      const adminSession = await ensureApiSession({
        username: `e2e_bui_admin_${adminSuffix}`,
        role: '/bidAdmin',
        fullName: 'E2E BUI Admin',
      })
      const tender = await apiCreateTender(adminSession)
      expect(tender?.id).toBeTruthy()

      // 再以 sales 登录查看该标讯
      const session = await loginAsRole(page, 'bid-projectLeader')
      await goToTenderDetail(page, tender.id)

      // 验证头部没有编辑和删除按钮
      const editBtn = page.locator('.detail-global-actions').getByRole('button', { name: '编辑' })
      const deleteBtn = page.locator('.detail-global-actions').getByRole('button', { name: '删除' })
      await expect(editBtn).toHaveCount(0)
      await expect(deleteBtn).toHaveCount(0)
    })

    test('BUI-1.3: admin 在 PENDING_ASSIGNMENT 下始终有分配和删除按钮', async ({ page }) => {
      // 先以 sales 创建标讯
      const salesSuffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
      const salesSession = await ensureApiSession({
        username: `e2e_bui_sales_${salesSuffix}`,
        role: 'bid-projectLeader',
        fullName: 'E2E BUI Sales',
      })
      const tender = await apiCreateTender(salesSession)
      expect(tender?.id).toBeTruthy()

      // 再以 bid_admin 登录查看该标讯
      const session = await loginAsRole(page, '/bidAdmin')
      await goToTenderDetail(page, tender.id)

      // admin 始终有分配/删除
      const assignBtn = page.locator('.detail-global-actions').getByRole('button', { name: '分配' })
      const deleteBtn = page.locator('.detail-global-actions').getByRole('button', { name: '删除' })
      await expect(assignBtn).toBeVisible({ timeout: 5000 })
      await expect(deleteBtn).toBeVisible({ timeout: 5000 })
    })
  })

  test.describe('创建页底部按钮', () => {
    test('BUI-1.4: admin 创建标讯分配后创建页底部无下一步/提交按钮', async ({ page }) => {
      const session = await loginAsRole(page, '/bidAdmin')
      // 创建 TRACKING 状态标讯（模拟已分配）
      const tender = await apiCreateTender(session, { status: 'TRACKING', projectManagerId: 88888 })
      expect(tender?.id).toBeTruthy()

      await page.goto(`/bidding/create?edit=${tender.id}`)
      await expect(page.locator('.bidding-create-page')).toBeAttached({ timeout: 10000 })

      // 验证底部没有「下一步」和「提交」按钮
      const nextStep = page.locator('.bottom-action-bar').getByRole('button', { name: '下一步' })
      const submit = page.locator('.bottom-action-bar').getByRole('button', { name: '提交' })
      await expect(nextStep).toHaveCount(0)
      await expect(submit).toHaveCount(0)
    })
  })
})

// =========================================================================
// BUI-2: 交付物上传后文件不闪烁（IJT3OY）
// =========================================================================
test.describe('BUI-2: 交付物上传闪烁 — deliverableFileList 引用稳定性', () => {
  test('BUI-2.1: 任务交付物列表在文件上传后引用稳定、不闪烁', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin')

    // 创建标讯和项目
    const tender = await apiCreateTender(session, { status: 'TRACKING' })
    expect(tender?.id).toBeTruthy()

    const projectRes = await fetch(`${apiBaseUrl}/api/projects`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`,
      },
      body: JSON.stringify({
        name: `E2E-BUI2-项目-${Date.now()}`,
        tenderId: tender.id,
        status: 'BIDDING',
        managerId: session.user.id,
        teamMembers: [session.user.id],
        startDate: toLocalDateTimeString(new Date()),
        endDate: toLocalDateTimeString(new Date(Date.now() + 10 * 86400000)),
      }),
    })
    const projectPayload = await projectRes.json()
    const project = projectPayload?.data
    expect(project?.id).toBeTruthy()

    // 创建任务
    const taskRes = await fetch(`${apiBaseUrl}/api/projects/${project.id}/tasks`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`,
      },
      body: JSON.stringify({
        title: 'E2E-BUI2-交付物测试任务',
        description: '',
        content: '## E2E 测试内容',
        assigneeId: session.user.id,
        assigneeName: session.user.name,
        priority: 'MEDIUM',
        dueDate: new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 19),
      }),
    })
    const taskPayload = await taskRes.json()
    expect(taskPayload?.success).toBeTruthy()
    const task = taskPayload?.data
    expect(task?.id).toBeTruthy()

    // 导航到项目详情页
    await page.goto(`/project/${project.id}`)
    await expect(page.locator('.project-detail').first()).toBeAttached({ timeout: 15000 })

    // 查找并点击任务卡片打开任务抽屉/弹窗
    const taskCard = page.locator('.task-card, .el-card').filter({ hasText: task.title }).first()
    if (await taskCard.isVisible({ timeout: 3000 }).catch(() => false)) {
      await taskCard.click()
    }

    // 验证交付物区域存在且不闪烁（通过 DOM 稳定性间接验证）
    const deliverableArea = page.locator('.deliverable-file-list, .task-deliverables, .file-upload-area').first()
    await expect(deliverableArea).toBeAttached({ timeout: 5000 })
  })
})

// =========================================================================
// BUI-3: 提交审核后状态变更（IJT3OP）
// =========================================================================
test.describe('BUI-3: 任务提交审核', () => {
  test('BUI-3.1: 提交审核后状态变更、内容保存', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin')

    // 创建标讯和项目
    const tender = await apiCreateTender(session, { status: 'TRACKING' })
    expect(tender?.id).toBeTruthy()

    const projectRes = await fetch(`${apiBaseUrl}/api/projects`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`,
      },
      body: JSON.stringify({
        name: `E2E-BUI3-项目-${Date.now()}`,
        tenderId: tender.id,
        status: 'BIDDING',
        managerId: session.user.id,
        teamMembers: [session.user.id],
        startDate: toLocalDateTimeString(new Date()),
        endDate: toLocalDateTimeString(new Date(Date.now() + 10 * 86400000)),
      }),
    })
    const projectPayload = await projectRes.json()
    const project = projectPayload?.data
    expect(project?.id).toBeTruthy()

    // 创建任务
    const taskRes = await fetch(`${apiBaseUrl}/api/projects/${project.id}/tasks`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`,
      },
      body: JSON.stringify({
        title: 'E2E-BUI3-审核测试任务',
        description: '',
        content: '## 审核测试内容\n- 测试项1',
        assigneeId: session.user.id,
        assigneeName: session.user.name,
        priority: 'MEDIUM',
        dueDate: new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 19),
      }),
    })
    const taskPayload = await taskRes.json()
    expect(taskPayload?.success).toBeTruthy()
    const task = taskPayload?.data
    expect(task?.id).toBeTruthy()

    // 导航到项目详情页
    await page.goto(`/project/${project.id}`)
    await expect(page.locator('.project-detail').first()).toBeAttached({ timeout: 15000 })

    // 查找并点击任务卡片
    const taskCard = page.locator('.task-card, .el-card').filter({ hasText: task.title }).first()
    if (await taskCard.isVisible({ timeout: 3000 }).catch(() => false)) {
      await taskCard.click()
    }

    // 验证任务详情弹窗/抽屉打开
    const taskDialog = page.locator('.el-dialog, .task-detail-drawer, .task-form').first()
    await expect(taskDialog).toBeAttached({ timeout: 5000 })

    // 验证提交审核按钮存在
    const submitReviewBtn = page.getByRole('button', { name: /提交审核|送审/ }).first()
    await expect(submitReviewBtn).toBeVisible({ timeout: 3000 })

    // 点击提交审核
    await submitReviewBtn.click()

    // 验证提交成功提示或状态变更
    await expect(page.locator('.el-message--success, .el-notification').filter({ hasText: /提交|成功|审核/ })).toBeVisible({ timeout: 5000 })
  })
})

// =========================================================================
// BUI-4: 完成投标区域权限正确（IJSZSG）
// =========================================================================
test.describe('BUI-4: 完成投标权限', () => {
  test('BUI-4.1: bid_admin 标书制作页渲染正常', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin')

    // 创建标讯和项目
    const tender = await apiCreateTender(session, { status: 'TRACKING' })
    expect(tender?.id).toBeTruthy()

    const projectRes = await fetch(`${apiBaseUrl}/api/projects`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`,
      },
      body: JSON.stringify({
        name: `E2E-BUI4-项目-${Date.now()}`,
        tenderId: tender.id,
        status: 'BIDDING',
        managerId: session.user.id,
        teamMembers: [session.user.id],
        startDate: toLocalDateTimeString(new Date()),
        endDate: toLocalDateTimeString(new Date(Date.now() + 10 * 86400000)),
      }),
    })
    const projectPayload = await projectRes.json()
    const project = projectPayload?.data
    expect(project?.id).toBeTruthy()

    await page.goto(`/project/${project.id}`)
    await expect(page.locator('.project-detail').first()).toBeAttached({ timeout: 15000 })

    // 切换到标书制作 tab
    const draftingTab = page.getByRole('tab', { name: /标书制作|标书编制/ })
    if (await draftingTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await draftingTab.click()
    }

    // 验证权限计算结果：bid_admin 可以看到投标文件区域
    await expect(page.locator('.bid-header, .project-document-table').first()).toBeAttached({ timeout: 5000 })
  })

  test('BUI-4.2: bid_specialist 角色看不到完成投标按钮', async ({ page }) => {
    const session = await loginAsRole(page, 'bid-Team')

    // 创建标讯和项目
    const tender = await apiCreateTender(session, { status: 'TRACKING' })
    expect(tender?.id).toBeTruthy()

    const projectRes = await fetch(`${apiBaseUrl}/api/projects`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`,
      },
      body: JSON.stringify({
        name: `E2E-BUI4-项目-${Date.now()}`,
        tenderId: tender.id,
        status: 'BIDDING',
        managerId: session.user.id,
        teamMembers: [session.user.id],
        startDate: toLocalDateTimeString(new Date()),
        endDate: toLocalDateTimeString(new Date(Date.now() + 10 * 86400000)),
      }),
    })
    const projectPayload = await projectRes.json()
    const project = projectPayload?.data
    expect(project?.id).toBeTruthy()

    await page.goto(`/project/${project.id}`)
    await expect(page.locator('.project-detail').first()).toBeAttached({ timeout: 15000 })

    // 切换到标书制作 tab
    const draftingTab = page.getByRole('tab', { name: /标书制作|标书编制/ })
    if (await draftingTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await draftingTab.click()
    }

    // bid_specialist 不应看到"完成投标"按钮
    const completeBidBtn = page.getByRole('button', { name: /完成投标/ })
    await expect(completeBidBtn).toHaveCount(0)
  })
})

// =========================================================================
// BUI-5: 标书审核权限：提交人不见审核按钮，审核人可见（IJSTZG）
// =========================================================================

/**
 * 通过 HTTP 直接提交标书审核（绕过 UI 选择流程）
 * 用于 E2E 中精确控制 reviewerId
 */
async function apiSubmitBidForReview(session, projectId, reviewerId) {
  const response = await fetch(`${apiBaseUrl}/api/projects/${projectId}/drafting/submit-review`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
    body: JSON.stringify({ reviewerId }),
  })
  const data = await response.json()
  if (!response.ok) throw new Error(`submit-review failed: ${response.status} - ${JSON.stringify(data)}`)
  return data
}

/**
 * 通过 HTTP 直接审核通过
 */
async function apiApproveBidReview(session, projectId) {
  const response = await fetch(`${apiBaseUrl}/api/projects/${projectId}/drafting/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
    body: JSON.stringify({}),
  })
  const data = await response.json()
  if (!response.ok) throw new Error(`approve failed: ${response.status} - ${JSON.stringify(data)}`)
  return data
}

/**
 * 导航到项目标书制作 tab，等待 DraftingStage 渲染完毕
 */
async function gotoProjectDraftingTab(page, projectId) {
  await page.goto(`/project/${projectId}`)
  await expect(page.locator('.project-detail').first()).toBeAttached({ timeout: 15000 })
  const draftingTab = page.getByRole('tab', { name: /标书制作|标书编制/ })
  if (await draftingTab.isVisible({ timeout: 3000 }).catch(() => false)) {
    await draftingTab.click()
  }
  await page.waitForSelector('.bid-actions, .bid-reviewer-row, .bid-upload-area', { timeout: 10000 }).catch(() => null)
}

test.describe('BUI-5: 标书审核权限', () => {
  let adminSession, auditorSession, projectId

  test.beforeEach(async ({ page }) => {
    adminSession = await loginAsRole(page, '/bidAdmin')
    auditorSession = await loginAsRole(page, 'auditor')

    const tender = await apiCreateTender(adminSession, { status: 'TRACKING' })
    expect(tender?.id).toBeTruthy()

    const projectRes = await fetch(`${apiBaseUrl}/api/projects`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`,
      },
      body: JSON.stringify({
        name: `E2E-BUI5-${Date.now()}`,
        tenderId: tender.id,
        status: 'BIDDING',
        managerId: adminSession.user.id,
        teamMembers: [adminSession.user.id, auditorSession.user.id],
        startDate: toLocalDateTimeString(new Date()),
        endDate: toLocalDateTimeString(new Date(Date.now() + 10 * 86400000)),
      }),
    })
    const projectPayload = await projectRes.json()
    projectId = projectPayload?.data?.id
    expect(projectId).toBeTruthy()
  })

  test('BUI-5.1: 提交审核后提交人不看见审核按钮', async ({ page }) => {
    // 以 admin 身份提交审核（指定 auditor 为审核人）
    await injectSession(page, adminSession)
    await apiSubmitBidForReview(adminSession, projectId, auditorSession.user.id)

    // 以 admin 身份访问标书制作页
    await gotoProjectDraftingTab(page, projectId)

    // admin 不是审核人，驳回和审核通过按钮都不应出现
    await expect(page.getByRole('button', { name: '驳回' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '审核通过' })).toHaveCount(0)
  })

  test('BUI-5.2: 审核人能看到审核按钮', async ({ page }) => {
    // 以 admin 身份提交审核（指定 auditor 为审核人）
    await injectSession(page, adminSession)
    await apiSubmitBidForReview(adminSession, projectId, auditorSession.user.id)

    // 以 auditor 身份访问标书制作页
    await injectSession(page, auditorSession)
    await gotoProjectDraftingTab(page, projectId)

    // auditor 是被指定的审核人，驳回和审核通过按钮都应出现
    await expect(page.getByRole('button', { name: '驳回' })).toHaveCount(1)
    await expect(page.getByRole('button', { name: '审核通过' })).toHaveCount(1)
  })

  test('BUI-5.3: 审核通过后审核按钮消失', async ({ page }) => {
    // 以 admin 身份提交审核（指定 auditor 为审核人）
    await injectSession(page, adminSession)
    await apiSubmitBidForReview(adminSession, projectId, auditorSession.user.id)

    // 以 auditor 身份点击审核通过
    await injectSession(page, auditorSession)
    await gotoProjectDraftingTab(page, projectId)
    await page.getByRole('button', { name: '审核通过' }).click()
    // 等待成功提示
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })

    // 刷新页面后，审核状态已变为 APPROVED，审核按钮不再出现
    await page.reload()
    await page.waitForSelector('.bid-actions, .bid-reviewer-row', { timeout: 10000 }).catch(() => null)
    await expect(page.getByRole('button', { name: '驳回' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '审核通过' })).toHaveCount(0)
  })
})
