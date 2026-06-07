# 任务编辑持久化 + E2E 跑通 + 字典 fetch 收敛 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans / subagent-driven-development to execute this plan task-by-task.

**Goal:** 让任务抽屉的"编辑保存"调通 `PUT /api/tasks/{id}` 真持久化；端到端跑通 E2E 闭环（含刷新验证）；TaskBoard 字典获取统一到 projectStore 单一数据源。

**Architecture:**
后端 `PUT /api/tasks/{id}` 与 `TaskService.updateTask` 已存在（PR #149 之前就有）。本期只在前端：新增 `projectsApi.updateTask` 客户端 + `projectStore.updateTask` action + composable 编辑分支接 store；前端字段（name/deadline）通过 mapper 适配后端字段（title/dueDate）；TaskBoard 删除本地 `taskStatusDictApi` 调用，改读 `projectStore.taskStatuses`；E2E 追加刷新和控制字符两个 case。

**Tech Stack:**
- 前端：Vue 3 / Pinia / Element Plus / Vitest
- E2E：Playwright (`e2e/task-board-customization.spec.js`)
- 后端：无改动（接口已就绪）
- 设计稿：`docs/plans/2026-05-02-task-edit-persist-design.md`

**Pre-check:**
1. `pwd` = `/Users/user/xiyu/worktrees/claude`，分支 `agent/claude-init`
2. `git fetch origin && git rebase origin/main` —— 已 rebase 到 main（含 PR #149 已合并的 24 commits）
3. `./scripts/who-touches.sh "src/api/modules/projects.js src/stores/project.js src/composables/projectDetail/useProjectDetailTaskActions.js src/components/common/TaskBoard.vue src/views/Project/project-utils.js e2e/task-board-customization.spec.js"`，无冲突
4. 基线绿：`npm run test:unit && cd backend && mvn -q test -Dtest=ArchitectureTest`

---

## Phase A — 字段 mapper（前端 SSOT）

### Task A1：新增 task DTO ↔ 看板卡片 mapper

**Files:**
- Modify: `src/views/Project/project-utils.js`
- Test: `src/views/Project/project-utils.spec.js`（已存在，追加 case）

**Step 1：先写失败测试**

在 `project-utils.spec.js` 末尾追加：

```js
import { taskFormDtoToBackend, taskBackendToCard } from './project-utils.js'

describe('task DTO mapper', () => {
  it('taskFormDtoToBackend maps form fields to backend names', () => {
    const result = taskFormDtoToBackend({
      name: 'T1', content: '# md', status: 'TODO', priority: 'high',
      deadline: '2026-06-01', owner: '张三', // owner 暂丢弃
    })
    expect(result).toEqual({
      title: 'T1', content: '# md', status: 'TODO',
      priority: 'high', dueDate: '2026-06-01',
    })
    expect(result).not.toHaveProperty('owner')
    expect(result).not.toHaveProperty('name')
    expect(result).not.toHaveProperty('deadline')
  })

  it('taskFormDtoToBackend skips undefined fields (PATCH semantics)', () => {
    const result = taskFormDtoToBackend({ name: 'T', status: 'TODO' })
    expect(result).toEqual({ title: 'T', status: 'TODO' })
  })

  it('taskBackendToCard maps backend dto to board card shape', () => {
    const result = taskBackendToCard({
      id: 7, title: 'T2', content: 'c', status: 'COMPLETED',
      priority: 'medium', dueDate: '2026-05-15',
      assigneeName: '李宗', deliverables: [{ id: 1 }],
    })
    expect(result).toEqual({
      id: 7, name: 'T2', content: 'c', status: 'COMPLETED',
      priority: 'medium', deadline: '2026-05-15',
      owner: '李宗', deliverables: [{ id: 1 }], hasDeliverable: true,
    })
  })

  it('taskBackendToCard handles missing optional fields', () => {
    const result = taskBackendToCard({ id: 1, title: 'X', status: 'TODO' })
    expect(result.deliverables).toEqual([])
    expect(result.hasDeliverable).toBe(false)
    expect(result.owner).toBe('')
  })
})
```

**Step 2：运行 → FAIL**

`npm run test:unit -- src/views/Project/project-utils.spec.js`
Expected: 4 cases FAIL (functions undefined)

**Step 3：实现两个 mapper**

在 `project-utils.js` 适当位置（其他 mapper 之间）追加：

```js
/**
 * 把 TaskForm.vue 表单字段（前端命名）转成后端 TaskDTO 字段。
 *  - name → title
 *  - deadline → dueDate
 *  - owner 暂时丢弃（assignee_id 接通后续）
 *  - undefined 字段不写入，保留 PATCH 语义
 */
export function taskFormDtoToBackend(form = {}) {
  const dto = {}
  if (form.name !== undefined) dto.title = form.name
  if (form.content !== undefined) dto.content = form.content
  if (form.status !== undefined) dto.status = form.status
  if (form.priority !== undefined) dto.priority = form.priority
  if (form.deadline !== undefined) dto.dueDate = form.deadline
  return dto
}

/**
 * 把后端 TaskDTO 转回看板卡片字段（前端命名 + 计算字段）。
 */
export function taskBackendToCard(dto = {}) {
  const deliverables = Array.isArray(dto.deliverables) ? dto.deliverables : []
  return {
    id: dto.id,
    name: dto.title ?? '',
    content: dto.content ?? '',
    status: dto.status,
    priority: dto.priority,
    deadline: dto.dueDate ?? '',
    owner: dto.assigneeName ?? '',
    deliverables,
    hasDeliverable: deliverables.length > 0,
  }
}
```

**Step 4：测试通过**

`npm run test:unit -- src/views/Project/project-utils.spec.js` PASS

**Step 5：commit**

```bash
git add src/views/Project/project-utils.js src/views/Project/project-utils.spec.js
git commit -m "$(cat <<'EOF'
feat(task): add task DTO mapper between form fields and backend

taskFormDtoToBackend / taskBackendToCard centralize the alias between
前端 (name/deadline/owner) and 后端 (title/dueDate/assigneeName) so
api / store / composable callers don't each rewrite the mapping.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase B — API client + store action

### Task B1：projectsApi.updateTask

**Files:**
- Modify: `src/api/modules/projects.js`
- Test: `src/api/modules/projects.spec.js`（如已存在，追加；否则跳过测试这步）

**Step 1：写失败测试**（如果有 spec 文件）

```js
it('updateTask PUT /api/tasks/{id} with backend dto', async () => {
  httpClient.put.mockResolvedValue({ success: true, data: { id: 1, title: 'X' } })
  const result = await projectsApi.updateTask(1, { title: 'X', status: 'TODO' })
  expect(httpClient.put).toHaveBeenCalledWith('/api/tasks/1', { title: 'X', status: 'TODO' })
  expect(result.data.id).toBe(1)
})
```

**Step 2**：FAIL（方法未定义）

**Step 3：在 projects.js 追加方法**

```js
async updateTask(taskId, dto) {
  return httpClient.put(`/api/tasks/${taskId}`, dto)
}
```

放在 `updateTaskStatus` 旁边。同时更新文件头注释（如果约定要更新）。

**Step 4**：测试通过；governance 通过

`npm run check:front-data-boundaries`

**Step 5：commit**

```bash
git add src/api/modules/projects.js src/api/modules/projects.spec.js
git commit -m "$(cat <<'EOF'
feat(api): add projectsApi.updateTask client

PUT /api/tasks/{id} — full PATCH-style update for the TaskForm drawer
edit save path. Backend endpoint already exists (TaskController.updateTask).

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task B2：projectStore.updateTask action

**Files:**
- Modify: `src/stores/project.js`
- Test: 复用 existing project store specs（grep 看现有 update* action 的测试模式）

**Step 1：写失败测试**

在 store spec 文件追加：

```js
it('updateTask calls projectsApi.updateTask and syncs local cache', async () => {
  const store = useProjectStore()
  store.currentProject = { id: 1, tasks: [{ id: 7, title: 'old' }] }
  vi.mocked(projectsApi.updateTask).mockResolvedValue({
    success: true, data: { id: 7, title: 'new', status: 'TODO' }
  })
  await store.updateTask(1, 7, { title: 'new' })
  expect(projectsApi.updateTask).toHaveBeenCalledWith(7, { title: 'new' })
  expect(store.currentProject.tasks[0].title).toBe('new')
})
```

读现有 `src/stores/project.js` 拿 currentProject / tasks 字段的真实形状。如果 store 测试不存在，把这个 case 放到 composable 测试里间接验证。

**Step 2**：FAIL

**Step 3：实现 action**

在 `src/stores/project.js`（Pinia Options API style，per G1）追加：

```js
async updateTask(projectId, taskId, dto) {
  const result = await projectsApi.updateTask(taskId, dto)
  if (!result?.success) {
    throw new Error(result?.message || '更新任务失败')
  }
  // 同步本地缓存（保留前端形状字段）
  const project = this.currentProject
  if (project?.id === projectId && Array.isArray(project.tasks)) {
    const idx = project.tasks.findIndex(t => t.id === taskId)
    if (idx >= 0) {
      project.tasks[idx] = { ...project.tasks[idx], ...result.data }
    }
  }
  return result.data
},
```

**注意**：这里返回的是后端 DTO 形状（title/dueDate/assigneeName）。caller 决定是否调 `taskBackendToCard` 转换。本任务的测试只断言 `title === 'new'` 即可（不引入 mapper）。

**Step 4**：PASS

**Step 5：commit**

```bash
git commit -m "$(cat <<'EOF'
feat(store): projectStore.updateTask wraps PUT /api/tasks/{id}

Delegates to projectsApi.updateTask, syncs the returned DTO into
currentProject.tasks[] so the board view reflects the change without
a separate refetch.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase C — 接通抽屉编辑链路

### Task C1：handleSaveTask 编辑分支调真实 API

**Files:**
- Modify: `src/composables/projectDetail/useProjectDetailTaskActions.js`
- Test: `src/composables/projectDetail/useProjectDetailTaskActions.spec.js`（追加 case）

**Step 1：写失败测试**

```js
it('handleSaveTask edit branch calls store.updateTask with mapped dto', async () => {
  // setup mocked context with projectStore.updateTask = vi.fn().mockResolvedValue({...})
  // and a project containing task id=7 in state
  const ctx = makeCtx({
    projectStore: { updateTask: vi.fn().mockResolvedValue({ id: 7, title: 'New', status: 'TODO' }) },
    project: { id: 1, tasks: [{ id: 7, name: 'Old', status: 'TODO' }] },
  })
  const { handleSaveTask } = useProjectDetailTaskActions(ctx)
  await handleSaveTask({ id: 7, name: 'New', content: 'md', status: 'TODO' })
  expect(ctx.projectStore.updateTask).toHaveBeenCalledWith(
    1, 7,
    { title: 'New', content: 'md', status: 'TODO' }
  )
  expect(ctx.message.success).toHaveBeenCalled()
  expect(ctx.state.project.value.tasks[0].name).toBe('New')
})

it('handleSaveTask edit branch shows error toast on api failure', async () => {
  const ctx = makeCtx({
    projectStore: { updateTask: vi.fn().mockRejectedValue(new Error('boom')) },
    project: { id: 1, tasks: [{ id: 7, name: 'Old' }] },
  })
  const { handleSaveTask } = useProjectDetailTaskActions(ctx)
  await handleSaveTask({ id: 7, name: 'X' })
  expect(ctx.message.error).toHaveBeenCalled()
})
```

读现有 spec 的 `makeCtx` / mock pattern；按本仓约定调整。

**Step 2**：FAIL

**Step 3：改写 handleSaveTask 编辑分支**

定位现有 `handleSaveTask` 实现。当前编辑分支：
```js
} else {
  // edit
  Object.assign(found, data)
  pushActivity(`编辑了任务「${data.name}」`)
  message.success('任务已更新')
}
```

改成：
```js
} else {
  try {
    const dto = taskFormDtoToBackend(data)
    const updated = await projectStore.updateTask(state.project.value.id, found.id, dto)
    // 反向同步前端形状
    Object.assign(found, taskBackendToCard(updated))
    pushActivity(`编辑了任务「${updated.title}」`)
    message.success('任务已更新')
  } catch (error) {
    message.error(resolveErrorMessage(error, '任务更新失败'))
  }
}
```

import 两个 mapper：
```js
import { taskFormDtoToBackend, taskBackendToCard } from '@/views/Project/project-utils'
```

**Step 4**：PASS（含失败 path）

**Step 5：commit**

```bash
git commit -m "$(cat <<'EOF'
feat(task): drawer edit save now persists via PUT /api/tasks/{id}

Was previously optimistic local-only — refresh would discard changes.
Now the save calls projectStore.updateTask, which round-trips through
the backend; the returned DTO is mapped back to board card shape so
the ui stays in sync without a refetch.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase D — 字典 fetch 收敛

### Task D1：TaskBoard 改读 projectStore.taskStatuses

**Files:**
- Modify: `src/components/common/TaskBoard.vue`
- Modify: `src/components/common/TaskBoard.spec.js`

**Step 1：调整测试**

把现有 `vi.mock('@/api/modules/taskStatusDict.js', ...)` 改为 mock projectStore：

```js
vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({
    taskStatuses: [
      { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
      // ...同 PR #149 的 5 条字典 fixture
    ],
    taskStatusesLoaded: true,
    loadTaskStatuses: vi.fn(),
  }),
}))
```

保留所有原有 case（动态列数 / 进度 / dropdown 数）；新增一个 case：

```js
it('calls projectStore.loadTaskStatuses if not yet loaded', async () => {
  const loadFn = vi.fn()
  vi.doMock('@/stores/project', () => ({
    useProjectStore: () => ({
      taskStatuses: [], taskStatusesLoaded: false, loadTaskStatuses: loadFn,
    }),
  }))
  // ... mount, await flushPromises
  expect(loadFn).toHaveBeenCalled()
})
```

**Step 2**：FAIL（组件还在调 `taskStatusDictApi.list`）

**Step 3：改写 TaskBoard.vue**

- 删 `import { taskStatusDictApi } from '@/api/modules/taskStatusDict'`
- 加 `import { useProjectStore } from '@/stores/project'`
- script setup 内：
  ```js
  const projectStore = useProjectStore()
  const statuses = computed(() => projectStore.taskStatuses)
  const loadingStatuses = computed(() => !projectStore.taskStatusesLoaded)
  
  onMounted(() => {
    if (!projectStore.taskStatusesLoaded) {
      projectStore.loadTaskStatuses()
    }
  })
  ```
- 删除原本的 local `statuses` ref + `loadingStatuses` ref + 旧 onMounted fetch 块

**Step 4**：测试 PASS

`npm run test:unit -- src/components/common/TaskBoard.spec.js`

**Step 5**：跑全量单测确认无回归

`npm run test:unit`

**Step 6：commit**

```bash
git commit -m "$(cat <<'EOF'
refactor(task-board): read status dict from projectStore (single source)

TaskBoard had its own taskStatusDictApi.list() call alongside the
projectStore.loadTaskStatuses already firing on project detail mount.
Two fetches per page, two caches. Consolidate: TaskBoard reads
projectStore.taskStatuses (computed), and ensures bootstrap on its
own onMounted as a fallback for non-project-detail usages.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase E — E2E 真跑通

### Task E1：本地起栈 + spec 跑通 + 加刷新断言

**Files:**
- Modify: `e2e/task-board-customization.spec.js`

**Step 1：起本地栈（worktree 独占端口 1315/18081）**

```bash
./scripts/start-backend.sh   # background
./scripts/start-frontend.sh  # background
```

等两边健康检查通过：
```bash
curl -s http://127.0.0.1:18081/actuator/health
curl -s http://127.0.0.1:1315
```

**Step 2：跑现有 spec**

```bash
npx playwright test e2e/task-board-customization.spec.js --reporter=list
```

按报错调整选择器/时序。

**Step 3：追加刷新闭环 case**

在 spec 末尾追加：

```js
test('content survives page reload (real persistence)', async ({ page }) => {
  // setup: 登录 + 进项目（复用 fixture）
  // ...
  
  // 创建任务
  await page.click('button:has-text("添加任务")')
  const drawer = page.locator('.el-drawer')
  await expect(drawer).toBeVisible()
  await drawer.locator('input[placeholder*="任务名称"]').fill('刷新持久化测试')
  const md = '## 步骤\n- 一\n- 二\n```code\nx=1\n```'
  await drawer.locator('textarea').first().fill(md)
  await drawer.locator('button:has-text("保存")').click()
  await expect(drawer).toBeHidden()
  
  // 编辑修改 content
  await page.click(':text("刷新持久化测试")')
  await expect(drawer).toBeVisible()
  await drawer.locator('textarea').first().fill(md + '\n- 三')
  await drawer.locator('button:has-text("保存")').click()
  await expect(drawer).toBeHidden()
  
  // 关键：刷新页面
  await page.reload()
  await expect(page.locator(':text("刷新持久化测试")')).toBeVisible()
  
  // 重新打开抽屉，断言 content 仍在且包含三个步骤
  await page.click(':text("刷新持久化测试")')
  await expect(drawer).toBeVisible()
  const value = await drawer.locator('textarea').first().inputValue()
  expect(value).toContain('## 步骤')
  expect(value).toContain('- 三')
  expect(value).toContain('\n')
})

test('control chars stripped, line breaks preserved through round-trip', async ({ page }) => {
  // 类似设置...
  await page.click('button:has-text("添加任务")')
  const drawer = page.locator('.el-drawer')
  await drawer.locator('input[placeholder*="任务名称"]').fill('控制字符测试')
  // 在 textarea 直接 evaluate 设值（含 \x07）
  await drawer.locator('textarea').first().evaluate((el, v) => {
    el.value = v
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }, 'ab\nc')
  await drawer.locator('button:has-text("保存")').click()
  await expect(drawer).toBeHidden()
  
  await page.reload()
  await page.click(':text("控制字符测试")')
  await expect(drawer).toBeVisible()
  const v = await drawer.locator('textarea').first().inputValue()
  expect(v).toBe('ab\nc')  // BEL stripped; \n kept
})
```

**Step 4：E2E 全跑通过**

```bash
npx playwright test e2e/task-board-customization.spec.js
```

**Step 5：commit**

```bash
git commit -m "$(cat <<'EOF'
test(e2e): verify task edit persists across page reload

Adds two scenarios that close the loop on N1 (drawer edit persistence)
and validate the V102 + Markdown sanitizer end-to-end:
  - Edit a task, save, reload, reopen → content (incl. \\n) still there
  - Submit content with control char + newline → BEL stripped, \\n kept

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase F — 最终门禁

### Task F1：全量 gate + push + PR

**Step 1：前端**
```
npm run check:task-status-literal
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run test:unit
npm run build
```

**Step 2：后端**（无后端改动，跑架构 + 任务相关验证）
```
cd backend && mvn -q test -Dtest=ArchitectureTest,TaskServiceProjectAccessTest,TaskContentPersistenceTest,TaskContentMarkdownSanitizeTest
```

**Step 3：E2E（在 step 起栈基础上）**
```
npx playwright test e2e/task-board-customization.spec.js
```

**Step 4：手工 smoke**（用户参与）
1. `npm run dev:all`
2. 登录 lizong/123456
3. 进任一项目详情
4. 添加任务 → 填名称 + Markdown 内容 → 保存
5. **刷新页面**
6. 重新打开任务 → 内容仍在

**Step 5：push + PR**

```bash
git push origin HEAD:agent/claude-init
gh pr create --base main --head agent/claude-init \
  --title "feat(task): 任务编辑持久化 + E2E 闭环 + 字典 fetch 收敛" \
  --body "..."
```

PR body 模板：

```
## Summary
- 抽屉"编辑保存"调通 PUT /api/tasks/{id}（之前是装饰品，刷新即丢）
- 字段 mapper（前端 name/deadline ↔ 后端 title/dueDate）集中在 project-utils
- TaskBoard 字典获取统一到 projectStore，删除组件内本地 fetch
- E2E 追加刷新闭环 + 控制字符 round-trip 两个 case

## Test plan
- [x] 前端单测全绿（含新增 mapper / store / composable cases）
- [x] 前端 build / 4 个 check 脚本全 PASS
- [x] 后端 ArchitectureTest + TaskService 受影响测试 PASS
- [x] E2E `e2e/task-board-customization.spec.js` 全跑通含刷新断言
- [ ] 手工：添加任务 → 编辑 → 保存 → 刷新 → 内容仍在

## 设计文档
docs/plans/2026-05-02-task-edit-persist-design.md

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

---

## 工作量预估

| Task | 预估 |
|---|---|
| A1 mapper | 30 分钟 |
| B1 API client | 20 分钟 |
| B2 store action | 40 分钟 |
| C1 composable 编辑分支 | 1 小时 |
| D1 TaskBoard 字典收敛 | 1 小时 |
| E1 E2E 跑通 + 加 case | 2-3 小时（取决于本地栈起得多快） |
| F1 门禁 + PR | 1 小时 |
| **合计** | **~6-8 小时** |

7 个任务，可串行；A1 / B1 / D1 之间无依赖，能并行。

---

## 风险

1. **本地栈起不来**：worktree 端口 1315/18081 可能被占；用 `lsof -i :1315` 排查
2. **现有 store / composable spec 测试结构差异**：可能需要先读懂 makeCtx pattern；不要硬塞通用 fixture
3. **现有 handleSaveTask 编辑分支也许已经 await 了什么**：仔细 grep 当前实现，别盖掉 H1 之后的改动
4. **E2E 跑通可能暴露之前没发现的 bug**：那是好事，按 issue 修，不要为了让 spec 过关而弱化断言
