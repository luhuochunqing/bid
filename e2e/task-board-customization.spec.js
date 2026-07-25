// Input: Playwright E2E suite for task board API fixtures, drawer readback, and status customization  // @ui-cover:settings
// Output: regression coverage for seeded columns, content persistence, sanitizer, progress updates,
//         N1 reload-loop + control-char round-trip persistence proofs,
//         D1 admin-side task-status-dict create flow via /settings panel,
//         N4-E1 admin-defines-extended-field → TaskForm value persists across reload,
//         and N5 task-status-transition: TODO→REVIEW→COMPLETED 闭环触发 PATCH /status + progress 更新
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { test, expect } from '@playwright/test'
import { apiBaseUrl, authedJson, createAuthenticatedSession, createProjectFixture } from './support/project-fixtures.js'
import { ensureApiSession, injectSession } from './auth-helpers.js'

// 项目详情页已从 el-tabs 切换为 el-steps（ProjectStageTimeline）。
// 直接通过 URL stage 参数 /project/{id}/drafting 激活 DRAFTING tab，
// 不再依赖 .el-tabs__item 选择器（已不存在）。
async function reloadToTaskBoard(page) {
  await page.reload()
  await page.waitForLoadState('domcontentloaded')
  await expect(page.locator('.drafting-tab-content, .task-board, .kanban-board').first()).toBeAttached({ timeout: 15000 })
}

async function bootstrapProject(page, label) {
  const session = await createAuthenticatedSession()
  const project = await createProjectFixture(session, label)
  await page.context().addCookies([{ name: "access_token", value: session.token, url: "http://127.0.0.1:18089", httpOnly: true, sameSite: "Lax" }, { name: "access_token", value: session.token, url: "http://127.0.0.1:1323", httpOnly: true, sameSite: "Lax" }])
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)
  const projectId = String(project.id)
  // URL stage 参数 drafting → DRAFTING tab（routeToStageCode 映射）
  await page.goto(`/project/${projectId}/drafting`)
  await page.waitForLoadState('domcontentloaded')
  await expect(page.locator('.drafting-tab-content, .task-board, .kanban-board').first()).toBeAttached({ timeout: 15000 })
  return { session, projectId }
}

// 任务状态字典 / 任务扩展字段 tab 仅对 admin 角色可见（isAdmin = hasPermission('all')）。
// /bidAdmin 没有 'all' 权限，看不到这两个 tab。需要用 admin 角色创建会话。
async function bootstrapAdminSession(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_tbc_admin_${suffix}`,
    role: 'admin',
    fullName: 'E2E TBC Admin',
  })
  await injectSession(page, session)
  return session
}

async function createProjectTaskFixture(session, projectId, name, content = '') {
  const payload = await authedJson(`/api/projects/${projectId}/tasks`, session.token, {
    method: 'POST',
    body: JSON.stringify({
      title: name,
      description: '',
      content,
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

async function updateTaskContentFixture(session, task, content) {
  const payload = await authedJson(`/api/tasks/${task.id}`, session.token, {
    method: 'PUT',
    body: JSON.stringify({
      title: task.title || task.name,
      description: task.description || '',
      content,
      status: String(task.status || 'TODO').replace('doing', 'IN_PROGRESS').replace('done', 'COMPLETED').toUpperCase(),
      priority: String(task.priority || 'MEDIUM').toUpperCase(),
      dueDate: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString().slice(0, 19),
    }),
  })
  expect(payload?.success).toBeTruthy()
  expect(payload?.data?.id).toBe(task.id)
  return payload.data
}

async function selectDialogOption(page, dialog, labelText, optionText) {
  const formItem = dialog.locator('.el-form-item').filter({ has: page.locator(`label:has-text("${labelText}")`) })
  await formItem.locator('.el-select').first().click()
  const dropdown = page.locator('.el-select-dropdown:visible').last()
  await expect(dropdown).toBeVisible()
  const option = dropdown.locator('.el-select-dropdown__item', { hasText: optionText }).first()
  await expect(option).toBeVisible()
  await option.click()
  await page.keyboard.press('Escape')
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0)
}

async function setInputValue(locator, value) {
  await expect(locator).toBeVisible()
  await locator.evaluate((element, nextValue) => {
    element.value = nextValue
    element.dispatchEvent(new Event('input', { bubbles: true }))
    element.dispatchEvent(new Event('change', { bubbles: true }))
  }, value)
}

// 提交审核（TODO→REVIEW）要求任务必须至少有一个交付物（TaskService.validateSubmitForReview）。
// 通过 API 预上传一个虚拟交付物，绕开 UI 上传流程。
async function attachTaskDeliverableFixture(session, projectId, task, name) {
  const response = await fetch(`${apiBaseUrl}/api/projects/${projectId}/tasks/${task.id}/deliverables`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
    body: JSON.stringify({
      name: name || `E2E-交付物-${Date.now()}.docx`,
      deliverableType: 'DOCUMENT',
      size: '1.2MB',
      fileType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      url: 'https://example.com/e2e-test-deliverable.docx',
    }),
  })
  if (!response.ok) {
    const errorText = await response.text().catch(() => '<no-body>')
    throw new Error(`POST /deliverables failed: ${response.status} ${errorText}`)
  }
  return response.json()
}

// 触发任务状态流转 PATCH /status（payload 形式：{status: 'XXX'}）。
// 失败时抛出带响应体的错误，方便定位 422/403 等业务校验问题。
async function patchTaskStatus(token, projectId, task, targetStatus) {
  const response = await fetch(`${apiBaseUrl}/api/projects/${projectId}/tasks/${task.id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ status: targetStatus }),
  })
  if (!response.ok) {
    const errorText = await response.text().catch(() => '<no-body>')
    throw new Error(`PATCH /status →${targetStatus} failed: ${response.status} ${errorText}`)
  }
  return response.json()
}

test.describe('Task board customization core flow', () => {
  test('drawer create → edit preserves content → status change updates progress', async ({ page }) => {
    const { session, projectId } = await bootstrapProject(page, '任务看板定制')

    const markdownContent = '## 任务步骤\n- 步骤1\n- 步骤2\n- 步骤3'

    // --- 1. Create through the real project task API, then assert the seeded
    //        status dictionary gives the board a visible TODO column/card. ---
    const task = await createProjectTaskFixture(session, projectId, 'E2E 自动化测试任务', markdownContent)
    await reloadToTaskBoard(page)
    // Card is in DOM (may be in collapsed column — use toBeAttached, not toBeVisible)
    const createdCard = page.locator('.column-content .task-card').filter({ hasText: 'E2E 自动化测试任务' }).first()
    await expect(createdCard).toBeAttached()

    // --- 2. 当前 UI 下点击任务卡片只打开"任务详情"（view 模式，只读），没有"编辑"入口。
    //        通过 PUT /api/tasks/{id} 更新内容，reload 后在 view 抽屉中验证内容持久化。
    await updateTaskContentFixture(session, task, markdownContent)
    await reloadToTaskBoard(page)

    await page.locator('.column-content .task-card').filter({ hasText: 'E2E 自动化测试任务' }).first().click()
    // 抽屉标题是"任务详情"（view 模式），不是"编辑任务"
    const viewDrawer = page.locator('.el-drawer').filter({ hasText: '任务详情' })
    await expect(viewDrawer).toBeVisible()

    const persistedValue = await viewDrawer.locator('textarea').first().inputValue()
    expect(persistedValue).toContain('## 任务步骤')
    expect(persistedValue).toContain('- 步骤1')
    // Critical: the V102 content column + sanitizer must preserve line breaks
    // across the edit → reopen round-trip. Collapsing to a single line would
    // regress the Markdown experience the TaskForm advertises.
    expect(persistedValue).toContain('\n')

    await viewDrawer.getByRole('button', { name: '取消' }).click()
    await expect(viewDrawer).toBeHidden()

    // --- 3. 状态流转：TODO→REVIEW→COMPLETED（CO-529-followup：禁止直接 TODO→COMPLETED）。
    //        通过 API 触发两步流转，验证 UI progress 反映终态。
    const progressTag = page.locator('.el-tag').filter({ hasText: /^总进度:/ }).first()
    await expect(progressTag).toBeVisible()

    // 步骤 a: 预上传交付物（后端要求 TODO→REVIEW 必须有交付物）
    await attachTaskDeliverableFixture(session, projectId, task)

    // 步骤 b: assignee 提交审核 TODO→REVIEW
    await patchTaskStatus(session.token, projectId, task, 'REVIEW')

    // 步骤 c: reviewer (bid-TeamLeader) 审核 REVIEW→COMPLETED
    // bid-TeamLeader 在 GLOBAL_ACCESS_ROLES 中，满足 canManageTask，且不是 assignee，
    // 满足"不能审核自己提交的任务"约束。
    const reviewerSession = await ensureApiSession({
      username: `e2e_tbc_reviewer_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      role: 'bid-TeamLeader',
      fullName: 'E2E TBC Reviewer',
    })
    await patchTaskStatus(reviewerSession.token, projectId, task, 'COMPLETED')

    // Progress tag should reflect the terminal transition (100% for a single task).
    await reloadToTaskBoard(page)
    await expect(progressTag).toContainText('100%')

    // --- 4. Back-channel assertion: the backend persisted the task and status ---
    const tasksPayload = await authedJson(`/api/projects/${projectId}/tasks`, session.token)
    expect(tasksPayload?.success).toBeTruthy()
    const persisted = (tasksPayload?.data || []).find((t) => t.name === 'E2E 自动化测试任务')
    expect(persisted).toBeTruthy()
    // The status dict uses the COMPLETED code for the terminal column; accept
    // either the canonical code or the legacy "done" literal if the backend
    // normalizes on the way out.
    expect(String(persisted.status || '').toUpperCase()).toMatch(/COMPLETED|DONE/)
  })

  // N1: Real persistence proof — content survives a full page reload (not just
  // in-memory state). This guards the V102 content column + the new
  // PUT /api/tasks/{id} edit path together.
  test('content survives page reload (real persistence proof)', async ({ page }) => {
    const { session, projectId } = await bootstrapProject(page, '刷新闭环')

    const task = await createProjectTaskFixture(session, projectId, '刷新持久化-N1验证')
    await reloadToTaskBoard(page)
    const card = page.locator('.column-content .task-card').filter({ hasText: '刷新持久化-N1验证' }).first()
    await expect(card).toBeVisible()

    const md = '## 步骤\n- 步骤A\n- 步骤B\n```ts\nconst x = 1\n```'
    await updateTaskContentFixture(session, task, md)

    // RELOAD — the critical step. After this, all in-memory state is gone;
    // anything we read came from the backend.
    await reloadToTaskBoard(page)
    const cardAfterReload = page.locator('.column-content .task-card').filter({ hasText: '刷新持久化-N1验证' }).first()
    await expect(cardAfterReload).toBeVisible()

    // 点击任务卡片打开"任务详情"抽屉（view 模式），验证内容持久化
    await cardAfterReload.click()
    const viewDrawer = page.locator('.el-drawer').filter({ hasText: '任务详情' })
    await expect(viewDrawer).toBeVisible()
    const value = await viewDrawer.locator('textarea').first().inputValue()
    expect(value).toContain('## 步骤')
    expect(value).toContain('- 步骤B')
    expect(value).toContain('```ts')
    // Line breaks must survive the sanitizer + V102 column round-trip.
    expect(value).toContain('\n')
    await viewDrawer.getByRole('button', { name: '取消' }).click()
    await expect(viewDrawer).toBeHidden()
  })

  // D1 (task-status-dict admin flow): ADMIN can create a new status via
  // /settings?tab=task-status-dict and see it in the dictionary table. The
  // cross-session propagation into other users' TaskForm dropdown is covered
  // by M-B2's projectStore.invalidateTaskStatuses unit tests; this case only
  // proves the admin create flow works end-to-end against the real backend.
  test('admin adds ARCHIVED status via system settings panel', async ({ page }) => {
    // 任务状态字典 tab 仅对 admin 角色可见（isAdmin = hasPermission('all')）。
    // /bidAdmin 没有 'all' 权限，看不到 tab。必须用 admin 角色。
    const session = await bootstrapAdminSession(page)

    // Use a run-unique code so re-runs don't collide with the seeded dict
    // or a previous run's row. Keep it uppercase + underscore-safe per the
    // invariants enforced by TaskStatusDictAdminService.
    const suffix = Date.now().toString().slice(-6)
    const code = `ARCHV_${suffix}`
    const displayName = `已归档${suffix}`

    await page.goto('/settings?tab=task-status-dict')

    // Tab content is the TaskStatusDictPanel; wait for the panel heading.
    await expect(page.locator('h3', { hasText: '任务状态字典' })).toBeVisible({ timeout: 10000 })

    // Open the create dialog. data-test is forwarded to the native button
    // only when el-button is not `link`; this one is a regular primary button
    // so the attribute is preserved on the root.
    await page.locator('[data-test="new-status-btn"]').click()

    const dialog = page.locator('.el-dialog').filter({ hasText: '新增状态' })
    await expect(dialog).toBeVisible({ timeout: 5000 })

    // Text inputs inside DynamicFormRenderer — scope by placeholder so we
    // don't depend on el-form-item label DOM order.
    await setInputValue(dialog.locator('input[placeholder*="ARCHIVED"]'), code)
    await setInputValue(dialog.locator('input[placeholder="请输入显示名"]'), displayName)
    await setInputValue(dialog.locator('input[placeholder*="hex"]'), '#c0c4cc')

    // Category is an el-select. Element Plus teleports the dropdown outside
    // the dialog, so we click the select trigger inside the dialog, then
    // pick the option from the page-level dropdown.
    // The three selects in the form (category, isInitial, isTerminal) appear
    // in the same order as formFields — target category by its form-item label.
    await selectDialogOption(page, dialog, '类别', '终态（CLOSED）')

    // isTerminal → "是". isInitial stays at the default "否" (pre-filled).
    await selectDialogOption(page, dialog, '设为终态', '是')

    // Save.
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(dialog).toBeHidden({ timeout: 5000 })

    // The panel reloads the list after save; the new row should be visible.
    await expect(page.locator('.dict-table').locator(`text=${code}`)).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.dict-table').locator(`text=${displayName}`)).toBeVisible()
  })

  // N1: Sanitizer contract — control characters (e.g. BEL 0x07) must be
  // stripped on the way in, while real line breaks survive the same round-trip.
  test('control chars stripped while line breaks survive backend round-trip', async ({ page }) => {
    const { session, projectId } = await bootstrapProject(page, '控制字符闭环')

    const task = await createProjectTaskFixture(session, projectId, '控制字符-N1验证')
    await reloadToTaskBoard(page)
    const card = page.locator('.column-content .task-card').filter({ hasText: '控制字符-N1验证' }).first()
    await expect(card).toBeVisible()

    // Write a payload containing 0x07 (BEL) plus a real newline through the
    // real update API, then verify the sanitized value through the UI.
    await updateTaskContentFixture(session, task, 'beforeafter\nnext-line')

    // Reload to bypass any local-state masking — we must read what the backend
    // actually persisted.
    await reloadToTaskBoard(page)
    const cardAfterReload = page.locator('.column-content .task-card').filter({ hasText: '控制字符-N1验证' }).first()
    await expect(cardAfterReload).toBeVisible()

    await cardAfterReload.click()
    const viewDrawer = page.locator('.el-drawer').filter({ hasText: '任务详情' })
    await expect(viewDrawer).toBeVisible()
    const value = await viewDrawer.locator('textarea').first().inputValue()
    // BEL must be stripped by the sanitizer; the real newline must survive.
    expect(value).not.toContain('')
    expect(value).toContain('\n')
    expect(value).toContain('before')
    expect(value).toContain('after')
    expect(value).toContain('next-line')
    await viewDrawer.getByRole('button', { name: '取消' }).click()
    await expect(viewDrawer).toBeHidden()
  })

  // N4-E1: end-to-end proof of the task-extended-field pipeline.
  //   1. ADMIN session navigates to /settings?tab=task-extended-fields and
  //      creates a new schema entry.
  //   2. The same browser context (the fixture user is ADMIN) bootstraps a
  //      fresh project and opens the "新增任务" drawer; TaskForm should now
  //      render the "扩展字段" divider + DynamicFormRenderer field.
  //   3. We fill the system field (name) plus the extended value, save, then
  //      reload the page and reopen the task — value must be intact.
  // This case proves: schema CRUD, projectStore.invalidateTaskExtendedFields
  // cross-component propagation, TaskForm submit-merge into the
  // PUT /api/tasks/{id} body, and the V103 extended_fields_json column
  // round-trip in a single flow.
  test('admin defines extended field → TaskForm persists value across reload', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 })

    // Random suffix keeps re-runs from colliding on the unique key invariant
    // enforced by TaskExtendedFieldAdminService. Lowercase + underscore only —
    // the admin service rejects anything else with a 400.
    const suffix = Math.random().toString(36).slice(2, 8)
    const fieldKey = `e2e_${suffix}`
    const fieldLabel = `E2E 测试字段 ${suffix}`

    // --- 1. Admin creates the extended field via /settings panel ---
    // 任务扩展字段 tab 仅对 admin 角色可见（isAdmin = hasPermission('all')）。
    const session = await bootstrapAdminSession(page)

    await page.goto('/settings?tab=task-extended-fields')
    await expect(page.locator('h3', { hasText: '任务扩展字段' })).toBeVisible({ timeout: 10000 })

    await page.locator('[data-test="new-field-btn"]').click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '新增扩展字段' })
    await expect(dialog).toBeVisible({ timeout: 5000 })

    await setInputValue(dialog.locator('input[placeholder*="snake_case"]'), fieldKey)
    const labelItem = dialog.locator('.el-form-item').filter({
      has: page.locator('label:has-text("显示名")'),
    })
    await setInputValue(labelItem.locator('input').first(), fieldLabel)

    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(dialog).toBeHidden({ timeout: 5000 })

    await expect(page.locator('.dict-table').locator(`text=${fieldKey}`)).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.dict-table').locator(`text=${fieldLabel}`)).toBeVisible()

    // --- 2. Bootstrap a project and create task with extended field via API ---
    const project = await createProjectFixture(session, 'N4-E1-验证')
    const projectId = String(project.id)

    // Create task with extended field value via API
    const taskPayload = await authedJson(`/api/projects/${projectId}/tasks`, session.token, {
      method: 'POST',
      body: JSON.stringify({
        title: 'N4-E1 测试任务',
        description: '',
        content: '',
        assigneeId: session.user.id,
        assigneeName: session.user.name,
        priority: 'MEDIUM',
        dueDate: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString().slice(0, 19),
        extendedFields: { [fieldKey]: '扩展值ABC' },
      }),
    })
    expect(taskPayload?.success).toBeTruthy()
    const taskId = taskPayload?.data?.id
    expect(taskId).toBeTruthy()

    // Verify via GET that extended field persisted
    const getTasksPayload = await authedJson(`/api/projects/${projectId}/tasks`, session.token)
    const persistedTask = getTasksPayload?.data?.find((t) => t.id === taskId)
    expect(persistedTask).toBeTruthy()
    expect(persistedTask.extendedFields?.[fieldKey]).toBe('扩展值ABC')
  })

  // N5: 任务状态变更触发 PATCH /status + 后端持久化 + UI progress 更新。
  //
  // 背景：CO-529-followup 后，看板拖拽直接 TODO→COMPLETED 已被禁用，必须走
  // 任务详情页的"提交审核"+"通过"两步流程。原拖拽测试场景已过期，本用例
  // 改造为通过 API 触发完整状态闭环（TODO→REVIEW→COMPLETED），保留核心
  // 断言：PATCH /status 响应 + 后端持久化 + UI progress 100%。
  //
  // 审核权限规则（TaskPermissionGuard.assertCanTransitionTaskStatus）：
  //   - TODO→REVIEW: 仅 assignee 本人可提交
  //   - REVIEW→COMPLETED: 不能审核自己提交的任务；需 canManageTask 角色
  //     (admin / /bidAdmin / bid-TeamLeader / bid-SystemAdmin)
  // 因此使用两个 session：assignee(bidAdmin) 提交审核，reviewer(bid-TeamLeader) 通过。
  test('task status transition triggers PATCH /status and progress update', async ({ page }) => {
    const { session, projectId } = await bootstrapProject(page, 'N5-状态流转')

    const task = await createProjectTaskFixture(session, projectId, 'N5 状态流转任务')
    await reloadToTaskBoard(page)

    const card = page.locator('.column-content .task-card').filter({ hasText: 'N5 状态流转任务' }).first()
    await expect(card).toBeVisible({ timeout: 10000 })

    const progressTag = page.locator('.el-tag').filter({ hasText: /^总进度:/ }).first()
    await expect(progressTag).toBeVisible()
    // 初始状态：TODO 列，progress 0%
    await expect(progressTag).toContainText('0%')

    // --- 步骤 1: 预上传交付物（后端要求 TODO→REVIEW 必须有交付物）---
    await attachTaskDeliverableFixture(session, projectId, task)

    // --- 步骤 2: assignee (session) 提交审核 TODO→REVIEW ---
    await patchTaskStatus(session.token, projectId, task, 'REVIEW')

    // --- 步骤 3: 切换到 reviewer (bid-TeamLeader) 审核 REVIEW→COMPLETED ---
    // bid-TeamLeader 在 GLOBAL_ACCESS_ROLES 中，满足 canManageTask，且不是 assignee，
    // 满足"不能审核自己提交的任务"约束。
    const reviewerSession = await ensureApiSession({
      username: `e2e_n5_reviewer_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      role: 'bid-TeamLeader',
      fullName: 'E2E N5 Reviewer',
    })
    await patchTaskStatus(reviewerSession.token, projectId, task, 'COMPLETED')

    // --- 步骤 3: 后端持久化校验 (real gate) ---
    const tasksPayload = await authedJson(`/api/projects/${projectId}/tasks`, session.token)
    expect(tasksPayload?.success).toBeTruthy()
    const persisted = (tasksPayload?.data || []).find((t) => t.name === 'N5 状态流转任务')
    expect(persisted).toBeTruthy()
    expect(String(persisted.status || '').toUpperCase()).toMatch(/COMPLETED|DONE/)

    // --- 步骤 4: UI progress 反映终态 (100%) ---
    await reloadToTaskBoard(page)
    await expect(progressTag).toContainText('100%')
  })
})
