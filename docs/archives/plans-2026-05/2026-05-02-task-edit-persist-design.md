# 任务编辑持久化 + E2E 跑通 + 字典 fetch 收敛 设计稿

- 日期：2026-05-02
- 范围：紧接 PR #149 的下一期 P0 闭环
- 设计稿来源：用户反馈"抽屉编辑刷新就丢"+ J1 留下的 E2E 未跑/双 fetch 治理债

---

## 1. 三件事的关系

PR #149 抽屉是"装饰品"：用户改名字、写 Markdown、按保存，前端 toast 弹"已更新"，但**任何刷新都让改动消失**。这块不补完，任务看板的"内容可编辑"诉求形同虚设。

同时 J1 留下两个治理债：
- E2E 在 session 里没真跑过——上一期所有验证靠单测和 spec compliance；UI 抽屉链路没有任何端到端证据
- TaskBoard 自己 fetch 字典 + projectStore 也 fetch 字典——同一个项目页两条数据源；早治理早避免分裂

三件事单独做都很轻，**绑成一个 PR 一次推进效率最高**：N1 改完抽屉编辑，N2 立刻验证它真的跑通，N8 顺手收敛字典获取路径不留尾巴。

---

## 2. N1 — 任务编辑持久化

### 现状

- 后端 `PUT /api/tasks/{id}` 已存在（`TaskController.java:70`），`TaskService.updateTask` PATCH 风格 null-guarded，覆盖 title/description/content/assignment/status/priority/dueDate
- 前端 `src/api/modules/projects.js` 只有 `updateTaskStatus`，**没有完整 update 方法**
- `useProjectDetailTaskActions.handleSaveTask` 编辑分支当前是 `Object.assign(task, data)` 本地内存改

### 改动

**前端 API 客户端**（`src/api/modules/projects.js`）

新增 `updateTask(taskId, taskDTO)` → `PUT /api/tasks/{id}`。**注意**：用 task id 而非 project id 路由——后端校验已通过 `assertCanAccessProject` 守。

**前端 store action**（`src/stores/project.js`）

新增 `async updateTask(projectId, taskId, dto)`：
1. 调 `projectsApi.updateTask(taskId, dto)`
2. 拿返回的 `TaskDTO` 反向同步到本地 `state.project.value.tasks[i]`，保证看板列、进度同步刷新
3. 失败抛错让 composable 处理 toast

**前端 composable**（`src/composables/projectDetail/useProjectDetailTaskActions.js`）

`handleSaveTask` 编辑分支：
- 之前：`Object.assign(found, data); pushActivity(...); message.success(...)`
- 之后：`await projectStore.updateTask(projectId, found.id, dto); pushActivity(...); message.success(...)`，catch 里 `message.error`

DTO 字段映射：
- `name` → `title`（后端字段叫 title）
- `content` → `content`
- `owner`（自由文本）→ 暂时塞 `description` 或丢弃。**当前 schema 里 `owner` 是个自由文本字段**（不是 user id），后端没有专门字段；先丢一份到 description 末尾或者新增一个 `owner_text` 字段。**MVP 决策：丢弃**，等 N3 状态字典管理页之后再做"成员选择"的真正 ownership UI。`description` 留给系统生成的"AI 拆解描述"
- `deadline` → `dueDate`
- `priority` → `priority`
- `status` → `status`

### 字段映射 trade-off

`name` vs `title`：现有数据库列叫 `title`，TaskDTO 也用 `title`。**前端层做一次 alias** `taskFormDtoToBackend()`：

```js
function taskFormDtoToBackend(form) {
  return {
    title: form.name,
    content: form.content,
    status: form.status,
    priority: form.priority,
    dueDate: form.deadline,
  }
}
```

反向同步用 `taskBackendToCard()`：

```js
function taskBackendToCard(dto) {
  return {
    id: dto.id,
    name: dto.title,
    content: dto.content,
    status: dto.status,
    priority: dto.priority,
    deadline: dto.dueDate,
    owner: dto.assigneeName || '',
    deliverables: dto.deliverables ?? [],
    hasDeliverable: !!dto.deliverables?.length,
  }
}
```

这两个 mapper 放 `src/views/Project/project-utils.js`（已有任务字段适配函数，集中维护）。

### 测试

- store unit：mock `projectsApi.updateTask`，验证返回值同步到 `state.project.tasks[i]`
- composable unit：扩展 `useProjectDetailTaskActions.spec.js`，断言编辑分支调 store + `pushActivity` + 成功 toast；失败路径调 error toast

---

## 3. N2 — E2E 真跑通

### 现状

- `e2e/task-board-customization.spec.js` 已提交（PR #149 I1）
- session 内未跑过——选择器对未对、网络空闲时机对不对、抽屉 teleport 是否被截图触达，都没验证
- spec 在 N1 之前写的，**编辑分支断言只看了内存中的 textarea 值**（reopen 同一卡片读到的是 `state.project.tasks` 的数据）。N1 接通后，应该追加：保存后**刷新页面，再次打开抽屉，content 仍然存在**——这一刷新闭环才是"真的持久化"的证据

### 改动

1. 本地启动栈：`npm run dev:all` 跑起来（worktree 独占端口 1315 / 18081，CLAUDE.md 已规定）
2. `npx playwright test e2e/task-board-customization.spec.js` 跑通
3. 失败的选择器/时序按 Playwright 报告调
4. **追加一个 case** `刷新后内容仍在`：保存任务 → `await page.reload()` → 重新打开抽屉 → 断言 content 含 `## 任务步骤` 和 `\n`
5. 在 Markdown sanitizer 上加一个负面 case：填 `内容带控制字符` → 保存 → 刷新 → 断言 BEL 已被清除但 `\n` 保留（验证 V102 + sanitizer 真的端到端工作）

### 风险

- 如果 N1 之前 spec 写得过于乐观（只验证内存）现在要重写编辑断言——**重写**，不要只 patch 选择器
- E2E 会暴露之前没发现的 H1 / N1 bug；那是好事，按发现修

---

## 4. N8 — 字典 fetch 收敛

### 现状

- `TaskBoard.vue` `onMounted` 调 `taskStatusDictApi.list()` 直接装本地 `statuses` ref
- `projectStore.loadTaskStatuses()` 在项目详情 init 时也调一次，存 `state.taskStatuses`
- 同一项目详情页两条 fetch；状态字典又是低频变更主数据，重复浪费一次请求

### 改动

`TaskBoard.vue`：
- 删本地 `statuses` ref 和 `taskStatusDictApi.list()` 调用
- 改成 `const projectStore = useProjectStore(); const statuses = computed(() => projectStore.taskStatuses)`
- `loadingStatuses` 变 `computed(() => projectStore.taskStatusesLoaded === false)`，仅在还没加载时显示
- `onMounted` 兜底：`if (!projectStore.taskStatusesLoaded) projectStore.loadTaskStatuses()`——这样在 TaskBoard 被复用到非项目详情上下文（如未来的"我的任务"页）时仍能自启

`TaskBoard.spec.js`：
- 改用 vi.mock(`@/stores/project`) 模拟 store，断言列数 / 进度计算逻辑——和 store 一个数据源后，测试也更精确

### 收敛后的边界

- **唯一 fetcher**：`projectStore.loadTaskStatuses`
- **唯一 SSOT**：`projectStore.taskStatuses`
- 任何组件读字典：`useProjectStore().taskStatuses`
- 任何组件需要 terminal 判断：`useProjectStore().isTerminalStatus(code)`

---

## 5. 不在本期范围

- N3 状态字典管理页（admin 增删改）→ 下下个 PR
- N4 任务扩展字段 schema → 再下一期
- N5 拖拽改状态 / N6 状态转移规则 / N7 评论历史 → 看反馈
- 任务 owner 真正接通用户系统（user picker + assignee_id）→ 与"成员协作"统一规划

---

## 6. 工作量预估

| 任务 | 工作量 | 风险 |
|---|---|---|
| N1 | 4-6 小时（含测试） | 字段 alias 容易漏，要 grep 现有 `task.name` 调用确认 |
| N2 | 2-3 小时 | 本地栈如果跑不起来要先排查端口占用 |
| N8 | 1-2 小时 | 改 TaskBoard.spec.js 测试要重新设计 mock |

**合计 1-2 天，绑成一个 PR**

---

## 7. 验证清单

收 PR 前必须 PASS：
- 后端：`mvn -q test -Dtest=TaskServiceProjectAccessTest,TaskContentPersistenceTest,TaskContentMarkdownSanitizeTest,ArchitectureTest`
- 前端单测：全绿（含新增 store / composable / TaskBoard 测试）
- 前端 build：`npm run build`
- 治理脚本：`check:task-status-literal` / `check:front-data-boundaries` / `check:doc-governance` / `check:line-budgets`
- E2E：`npx playwright test e2e/task-board-customization.spec.js` 含刷新 + 控制字符两个新 case
- 手工：登录 lizong → 进项目 → 添加任务 → 编辑 → 改 Markdown 内容 → 保存 → **刷新页面** → 重新打开 → 内容仍在
