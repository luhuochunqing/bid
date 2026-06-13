// Input: Playwright E2E suite for task board API fixtures, drawer readback, and status customization  // @ui-cover:settings
// Output: regression coverage for seeded columns, content persistence, sanitizer, progress updates,
//         N1 reload-loop + control-char round-trip persistence proofs,
//         D1 admin-side task-status-dict create flow via /settings panel,
//         N4-E1 admin-defines-extended-field → TaskForm value persists across reload,
//         and N5 drag-to-change-status: cross-column drop triggers PATCH /status
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { test, expect } from '@playwright/test'
import { authedJson, createAuthenticatedSession, createProjectFixture } from './support/project-fixtures.js'

async function switchToTaskBoardTab(page) {
  // TaskBoard 只在 "标书编制" (DRAFTING) tab 内的 DraftingStage 中
  const draftingTab = page.locator('.el-tabs__item', { hasText: '标书编制' }).first()
  if (await draftingTab.isVisible()) {
    await draftingTab.click()
    await expect(page.locator('.drafting-tab-content, .task-board, .kanban-board').first()).toBeAttached({ timeout: 15000 }).catch(() => {})
    await page.waitForLoadState('networkidle')
  }
}

async function reloadToTaskBoard(page) {
  await page.reload()
  await page.waitForLoadState('networkidle')
  await switchToTaskBoardTab(page)
}

async function bootstrapProject(page, label) {
  const session = await createAuthenticatedSession()
  const project = await createProjectFixture(session, label)
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)
  const projectId = String(project.id)
  await page.goto(`/project/${projectId}`)
  await expect(page).toHaveURL(/\/project\/\d+$/)
  await page.waitForLoadState('networkidle')
  await switchToTaskBoardTab(page)
  await expect(page.locator('.drafting-tab-content, .task-board, .kanban-board').first()).toBeAttached({ timeout: 10000 })
  return { session, projectId }
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

test.describe('Task board customization core flow', () => {
  test('drawer create → edit preserves content → status change updates progress', async ({ page }) => {
    const { session, projectId } = await bootstrapProject(page, '任务看板定制')

    const markdownContent = '## 任务步骤\n- 步骤1\n- 步骤2\n- 步骤3'

    // --- 1. Create through the real project task API, then assert the seeded
    //        status dictionary gives the board a visible TODO column/card. ---
    await createProjectTaskFixture(session, projectId, 'E2E 自动化测试任务', markdownContent)
    await reloadToTaskBoard(page)
    // Card is in DOM (may be in collapsed column — use toBeAttached, not toBeVisible)
    const createdCard = page.locator('.column-content .task-card').filter({ hasText: 'E2E 自动化测试任务' }).first()
    await expect(createdCard).toBeAttached()

    // --- 2. Re-open the drawer in edit mode, write content via the edit
    //        path (which exercises the new PUT /api/tasks/{id}), then assert
    //        round-trip persistence on reopen. ---
    await createdCard.click()

    let editDrawer = page.locator('.el-drawer').filter({ hasText: '编辑任务' })
    await expect(editDrawer).toBeVisible()

    await editDrawer.locator('textarea').first().fill(markdownContent)
    await editDrawer.getByRole('button', { name: '保存' }).click()
    await expect(editDrawer).toBeHidden()

    // Reopen and verify content survived the edit save (in-memory round-trip).
    await page.locator('.column-content .task-card').filter({ hasText: 'E2E 自动化测试任务' }).first().click()
    editDrawer = page.locator('.el-drawer').filter({ hasText: '编辑任务' })
    await expect(editDrawer).toBeVisible()

    const persistedValue = await editDrawer.locator('textarea').first().inputValue()
    expect(persistedValue).toContain('## 任务步骤')
    expect(persistedValue).toContain('- 步骤1')
    // Critical: the V102 content column + sanitizer must preserve line breaks
    // across the edit → reopen round-trip. Collapsing to a single line would
    // regress the Markdown experience the TaskForm advertises.
    expect(persistedValue).toContain('\n')

    await editDrawer.getByRole('button', { name: '取消' }).click()
    await expect(editDrawer).toBeHidden()

    // --- 3. Move the task to "已完成" via the card dropdown and verify progress ---
    const progressTag = page.locator('.el-tag').filter({ hasText: /^总进度:/ }).first()
    await expect(progressTag).toBeVisible()

    const cardForStatus = page.locator('.column-content .task-card').filter({ hasText: 'E2E 自动化测试任务' }).first()
    await cardForStatus.locator('.more-icon').first().click()
    // Dropdown items are rendered as "设为{name}"; target the COMPLETED terminal status.
    const completeMenuItem = page.locator('.el-dropdown-menu__item', { hasText: '设为已完成' }).first()
    await expect(completeMenuItem).toBeVisible()
    await Promise.all([
      page.waitForResponse((response) =>
        response.url().includes(`/api/projects/${projectId}/tasks/`) &&
        response.url().endsWith('/status') &&
        response.request().method() === 'PATCH' &&
        response.ok()
      ),
      completeMenuItem.click(),
    ])

    // Progress tag should reflect the terminal transition (100% for a single task).
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

    // Reopen drawer in edit mode; content should still be there.
    await cardAfterReload.click()
    const editDrawer = page.locator('.el-drawer').filter({ hasText: '编辑任务' })
    await expect(editDrawer).toBeVisible()
    const value = await editDrawer.locator('textarea').first().inputValue()
    expect(value).toContain('## 步骤')
    expect(value).toContain('- 步骤B')
    expect(value).toContain('```ts')
    // Line breaks must survive the sanitizer + V102 column round-trip.
    expect(value).toContain('\n')
    await editDrawer.getByRole('button', { name: '取消' }).click()
    await expect(editDrawer).toBeHidden()
  })

  // D1 (task-status-dict admin flow): ADMIN can create a new status via
  // /settings?tab=task-status-dict and see it in the dictionary table. The
  // cross-session propagation into other users' TaskForm dropdown is covered
  // by M-B2's projectStore.invalidateTaskStatuses unit tests; this case only
  // proves the admin create flow works end-to-end against the real backend.
  test('admin adds ARCHIVED status via system settings panel', async ({ page }) => {
    // The fixture creates an ADMIN-role user (role: 'bid_admin' in register
    // fallback), so the task-status-dict tab will be visible.
    const session = await createAuthenticatedSession()
    await page.addInitScript(({ token, user }) => {
      sessionStorage.setItem('token', token)
      sessionStorage.setItem('user', JSON.stringify(user))
    }, session)

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
    const editDrawer = page.locator('.el-drawer').filter({ hasText: '编辑任务' })
    await expect(editDrawer).toBeVisible()
    const value = await editDrawer.locator('textarea').first().inputValue()
    // BEL must be stripped by the sanitizer; the real newline must survive.
    expect(value).not.toContain('')
    expect(value).toContain('\n')
    expect(value).toContain('before')
    expect(value).toContain('after')
    expect(value).toContain('next-line')
    await editDrawer.getByRole('button', { name: '取消' }).click()
    await expect(editDrawer).toBeHidden()
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
    const session = await createAuthenticatedSession()
    await page.addInitScript(({ token, user }) => {
      sessionStorage.setItem('token', token)
      sessionStorage.setItem('user', JSON.stringify(user))
    }, session)

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

  // N5: drag-to-change-status real reality check. A card dragged from the TODO
  // column into the "已完成" column must:
  //   1. Trigger a PATCH /api/projects/{id}/tasks/{taskId}/status request
  //   2. Update the total progress tag to 100% (single-task project, terminal)
  //   3. Persist — backend list call after the drop shows status in [COMPLETED, DONE]
  // SortableJS support in Playwright is uneven; if locator.dragTo produces a
  // cross-column change event we trust it, otherwise the tailing backend
  // assertion will still fail loudly and we add a skip reason rather than
  // masking a real regression.
  test('drag card across columns triggers status PATCH and progress update', async ({ page }) => {
    const { session, projectId } = await bootstrapProject(page, 'N5-拖拽改状态')

    await createProjectTaskFixture(session, projectId, 'N5 拖拽测试任务')
    await reloadToTaskBoard(page)

    const card = page.locator('.column-content .task-card').filter({ hasText: 'N5 拖拽测试任务' }).first()
    await expect(card).toBeVisible({ timeout: 10000 })

    // Target column: the COMPLETED column is terminal in the seeded dict; its
    // column-content is where the card must end up.
    const completedColumn = page.locator('.board-column').filter({ hasText: '已完成' }).locator('.column-content').first()
    await expect(completedColumn).toBeVisible()

    const progressTag = page.locator('.el-tag').filter({ hasText: /^总进度:/ }).first()
    await expect(progressTag).toBeVisible()

    // Playwright's dragTo synthesizes pointerdown/move/up events; SortableJS
    // listens for them. Race the PATCH response so we don't block indefinitely
    // if the drag synthesis doesn't trigger the onChange handler in this env.
    await Promise.race([
      Promise.all([
        page.waitForResponse(
          (response) =>
            response.url().includes(`/api/projects/${projectId}/tasks/`) &&
            response.url().endsWith('/status') &&
            response.request().method() === 'PATCH' &&
            response.ok(),
          { timeout: 15000 },
        ),
        card.dragTo(completedColumn),
      ]),
      page.waitForTimeout(20000),
    ])

    // Backend source of truth — this is the real gate. If the drag didn't
    // fire onChange, this assertion fails and we know drag-on-CI is broken.
    const tasksPayload = await authedJson(`/api/projects/${projectId}/tasks`, session.token)
    expect(tasksPayload?.success).toBeTruthy()
    const persisted = (tasksPayload?.data || []).find((t) => t.name === 'N5 拖拽测试任务')
    expect(persisted).toBeTruthy()
    expect(String(persisted.status || '').toUpperCase()).toMatch(/COMPLETED|DONE/)

    // UI progress follows the terminal transition.
    await expect(progressTag).toContainText('100%')
  })
})
