# 任务状态字典管理页 设计稿

- 日期：2026-05-02
- 范围：N3，紧接 PR #149 / #151，让 admin 真正能"自定义状态"
- 背景：前两期把字典做出来了（V101 表 + GET /api/task-status-dict + projectStore 单一数据源 + terminal 大类解耦），但增/改/停用/排序只能改迁移脚本

---

## 1. 目标

让 ADMIN 在系统设置里可以：
1. 看见所有字典项（含已停用）
2. 新增一个状态（code / name / category / color / sort_order / is_initial / is_terminal）
3. 改名、改颜色、改 category、改 sort_order、切换 is_initial / is_terminal
4. 软删（设 `enabled=false`）— **绝不**物理 DELETE，因为 task 表里可能引用该 code
5. 拖拽排序，写回 sort_order
6. 改完后，看板下拉/列序立即看到（store 重新拉）

---

## 2. 不变的前提

- 数据库结构：保持 V101 schema 不变；本期所有改动通过 service 层
- `is_initial` 唯一性：service 写入时校验（保留 PR #149 的设计 — MySQL 不支持 partial index）
- `category` 取值：枚举固定（OPEN / IN_PROGRESS / REVIEW / CLOSED）— 不开放给用户改枚举池本身
- 所有任务 status 字面量：仍按字典 code（uppercase 标识符）— 不允许 name 出现在判断里

---

## 3. 后端

### 3.1 Endpoints

新增独立 admin controller `/api/admin/task-status-dict`，沿用 `AdminProjectGroupController` 模板：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/admin/task-status-dict` | 列出**全部**字典项（含 enabled=false），按 sort_order 升序 |
| POST | `/api/admin/task-status-dict` | 新增 |
| PUT | `/api/admin/task-status-dict/{code}` | 更新（name / category / color / sort_order / is_initial / is_terminal） |
| PATCH | `/api/admin/task-status-dict/{code}/disable` | 软删（设 enabled=false） |
| PATCH | `/api/admin/task-status-dict/{code}/enable` | 启用 |
| PATCH | `/api/admin/task-status-dict/reorder` | 批量重排（接收 `[{code, sortOrder}]`），事务内一次写完 |

**为何 disable/enable 不用 PUT 而用 PATCH 子路径**：
- 区分"管理员明确切换启用状态"和"修改字段"两种意图（前者频繁，后者罕见）
- 子路径让权限审计更清晰：`PATCH /api/admin/task-status-dict/{code}/disable` 比 `PUT` body 里 `{enabled: false}` 容易在日志里识别

**为何不开 DELETE**：
- 物理删除会让历史 task 的 status FK 悬空（虽然没有硬 FK 约束，但语义破坏）
- 即使 admin 手动删，应该走"先停用 + 后续做数据迁移脚本"两步
- 留出 DELETE 等于诱导误操作

### 3.2 Service 层校验

```
TaskStatusDictAdminService.create(req):
  1. code 不能空，不能含空格，必须全大写 + _ + 数字（regex）
  2. code 在表中不存在
  3. category 必须是枚举值之一
  4. 若 isInitial=true，必须把现有 is_initial=true 的字典项设为 false（事务内）
  5. 若 isTerminal=true，没有额外约束（多个 terminal 状态合理 — TODO：归档/拒绝/已完成都是 terminal）
  6. sortOrder 自动填（取当前 max+10 如果调用方没传）

TaskStatusDictAdminService.update(code, req):
  1. 字典项必须存在
  2. code 不可改（路径参数定，body 里给也忽略 + 警告日志）
  3. category 校验同 create
  4. is_initial 切换为 true：同 create
  5. 如果 enabled 字段出现在 body 里：忽略（用 disable/enable 子路径）

TaskStatusDictAdminService.disable(code):
  1. 字典项存在
  2. **不允许停用 is_initial=true 的字典项**（否则新建任务无默认状态）
     报错信息明确："请先把另一个字典项设为初始状态再停用此项"
  3. 不允许停用所有 terminal=true 的字典项（否则进度计算永远 < 100%）
     检查：当前 terminal=true 的字典项中，剔除本项之后是否还有 enabled=true 的剩余
  4. 设 enabled=false

TaskStatusDictAdminService.enable(code):
  1. 字典项存在
  2. 直接设 enabled=true（无 invariants 需要校验）

TaskStatusDictAdminService.reorder(items):
  1. items 中所有 code 必须在表中存在
  2. 不要求覆盖全部字典项（部分重排合法）
  3. 事务内 batch update sort_order
```

**为什么 disable 校验比 update 严**：update 不能改 enabled，所以 update 不会破坏 invariants；disable 是唯一让字典项"消失"的入口，必须把"不能让系统进入无可用初始/终态字典"这条业务规则放在它身上。

### 3.3 DTO

```
TaskStatusDictAdminDTO  // 用于 admin 接口（含 enabled / createdBy / updatedAt 等审计字段）
TaskStatusDictUpsertRequest  // POST/PUT body
TaskStatusDictReorderRequest  // PATCH /reorder body { items: [{code, sortOrder}] }
```

公开的 `TaskStatusDictDTO`（GET /api/task-status-dict 用的，PR #149）保持不变 — 不暴露审计字段给 STAFF。

### 3.4 ArchitectureTest

新增的 `task/dto`、`task/service`、`task/controller` 中的 admin 类应在 admin-only path 下，不能被 task module 自身使用（admin 只单向调）。

PR #149 的 ProjectAccessGuard baseline 已豁免 `TaskStatusDictController`；**admin controller 也需要同样豁免**（admin endpoints 不属于项目作用域）。需要把 `TaskStatusDictAdminController` 加进 baseline。

---

## 4. 前端

### 4.1 路由 / 入口

加一个面板 `src/views/System/settings/TaskStatusDictPanel.vue`，挂在 `Settings.vue` 主页面里（沿用现有 6+ 个 panel 的模式：每个 panel 是个 tab 项 / 卡片）。

入口文案"任务状态字典"，仅 ADMIN 可见（角色判断走现有 `useUserStore().isAdmin` 模式）。

### 4.2 页面结构

```
┌─ 任务状态字典 ──────────────────────────────────┐
│  [新增状态] 按钮（顶部右）                         │
│                                                  │
│  el-table（含已停用项，停用项灰色）              │
│  ─────────────────────────────────────────────── │
│  排序  Code     名称   类别   颜色   初始 终态 启用 操作  │
│  ━━    ━━━━━━  ━━━━  ━━━━  ━━━━  ━━  ━━  ━━  ━━━━ │
│  ⇅     TODO     待办   OPEN  ⬛灰  ✓   ✗   ✓   编辑/停用 │
│  ⇅     IN_PROG  进行中 IN_   ⬛蓝  ✗   ✗   ✓   ...  │
│  ⇅     REVIEW   待审核 REV.  ⬛黄  ✗   ✗   ✓   ...  │
│  ⇅     COMPLET  已完成 CLOS. ⬛绿  ✗   ✓   ✓   ...  │
│  ⇅     ARCHIV   已归档 CLOS. ⬛灰  ✗   ✓   ✗   启用 │
│                                                  │
│  说明：拖拽排序写回数据库；停用不删除，历史任务保留   │
└─────────────────────────────────────────────────┘
```

### 4.3 拖拽

用 **vuedraggable**（基于 SortableJS，Vue 3 兼容）。Element Plus 的 el-table 没有原生拖拽行，需要把 tbody 包成 vuedraggable。

**新增依赖** `vuedraggable@4`（只新增这一项，约 3KB gzipped）。

如果坚持不想新增依赖，**降级方案**：用上下移动按钮（两个 ⇧⇩ 在每行末尾），交互稍差但 0 依赖。设计稿默认走 vuedraggable；如果你不接受新依赖再切降级。

### 4.4 新增/编辑表单

复用 `DynamicFormRenderer`！PR #149 抽出来的渲染层这次正好派用场：

```js
const fields = [
  { key: 'code', label: 'Code', type: 'text', required: true,
    placeholder: '大写字母+下划线+数字，例如 ARCHIVED', readonly: !isCreate },
  { key: 'name', label: '显示名', type: 'text', required: true },
  { key: 'category', label: '类别', type: 'select', required: true,
    options: [
      { label: '开放（OPEN）', value: 'OPEN' },
      { label: '进行中（IN_PROGRESS）', value: 'IN_PROGRESS' },
      { label: '审核（REVIEW）', value: 'REVIEW' },
      { label: '终态（CLOSED）', value: 'CLOSED' },
    ]},
  { key: 'color', label: '颜色', type: 'text', placeholder: '#67c23a',
    // 临时用 text；后续给 DynamicFormRenderer 加 color 类型
  },
  { key: 'isInitial', label: '初始状态', type: 'select',
    options: [{ label: '是', value: true }, { label: '否', value: false }]},
  { key: 'isTerminal', label: '终态', type: 'select',
    options: [{ label: '是', value: true }, { label: '否', value: false }]},
]
```

弹窗用 `el-dialog` + `<DynamicFormRenderer>` + 自定义 footer 调 admin api 接口。

> **注意**：DynamicFormRenderer 当前不支持 `color` 类型 + `boolean` 选项。**这一期不扩展它**（YAGNI）— 颜色用 text 输入（带 placeholder 和正则校验），boolean 用 select。下一期再给 renderer 加 `color` 和 `boolean` 字段类型。

### 4.5 字典缓存失效

admin 改完字典后，必须让看板/任务表单立即看到新数据。两种方案：

**方案 A**（简单）：admin 操作成功后立即 `projectStore.taskStatuses = []; projectStore.taskStatusesLoaded = false`，下一次 TaskBoard 或 TaskForm onMounted 检查时会重新拉。

**方案 B**（稍精确）：admin 操作成功后直接 `projectStore.loadTaskStatuses(force=true)`，强制刷新，并且全局 emit 一个事件让已挂载的 TaskBoard/TaskForm 用 `watch` 重渲列。

**走方案 A**：admin 改字典是低频操作；MVP 不做精确事件总线。store 加个 `invalidateTaskStatuses()` 方法用于此场景。**用户当前所在页面**会在下次 onMounted（切路由再回来 / 刷新）看到新字典；用户当前看板**不会立即重排**——这个 trade-off 我标记为 trade-off 让你确认。

如果你觉得"改完字典看板必须立即变"，走方案 B 加 50% 工作量（前端事件总线 + watcher）。

### 4.6 测试

- `TaskStatusDictPanel.spec.js`：表格渲染、新增/编辑弹窗交互、停用按钮的 invariant 提示
- 不测拖拽（jsdom 不能仿真 drag；E2E 覆盖）
- E2E 加一个 case：admin 登录 → 进系统设置 → 新增一个 ARCHIVED 状态 → 切到普通用户视角看任务面板能选到"已归档"

### 4.7 路由权限

只有 admin 看到入口；非 admin 直接访问路由也要被守护（前端 `requiresAdmin` meta + 后端 `@PreAuthorize("hasRole('ADMIN')")` 双重）。

---

## 5. 工作量估算

| Phase | 内容 | 工作量 |
|---|---|---|
| A | 后端 admin controller / service / DTO + 单测（含 disable invariants） | 6h |
| B | 前端 admin api client + store invalidate | 1h |
| C | TaskStatusDictPanel 表格 + 编辑弹窗 + 单测 | 4h |
| D | 拖拽排序（含 vuedraggable 依赖审查） | 2h |
| E | E2E 1 个 case + 全量门禁 + PR | 2h |
| **合计** | | **~15h（约 2 工作日）** |

---

## 6. 不在本期范围

- 字典 code rename（要做的话需要级联更新所有 task.status — 大改）
- 字典项级"允许转移规则"（N6，下下期）
- 字典国际化（en/zh）
- 字典审计日志页面（who-changed-what list view）
- DynamicFormRenderer 加 `color` / `boolean` 字段类型

---

## 7. 风险

1. **`is_initial` 切换并发**：两个 admin 同时把不同字典项标 initial，事务隔离能不能保证唯一性？— service 用 `@Transactional` + 显式查"是否已有 initial=true" 然后清掉；如果两个事务并发，一个会先提交、另一个的清空 update 会基于过期数据。**对策**：在 update SQL 里直接 `UPDATE task_status_dict SET is_initial=false WHERE is_initial=true AND code != ?`，让 MySQL row-level lock 保护；接着再 `UPDATE ... SET is_initial=true WHERE code=?`。这种顺序保证最多一个 initial。

2. **拖拽排序的事务**：5 项重排发 5 个 UPDATE，中间断网会留半截状态。**对策**：用 `PATCH /reorder` 一次拿全部 `{code, sortOrder}` 列表，service 在一个事务里 batch update，原子性靠 DB 事务保证。

3. **vuedraggable 引入新依赖**：约 3KB gzipped + Sortable.js 约 13KB；可接受。

4. **方案 A 的失效语义**：用户改完字典回到看板可能要切路由再回才看到新顺序 — 已标记为 trade-off，需要你确认。

---

## 8. 决策点（需你确认）

- [ ] 拖拽：用 `vuedraggable` 还是降级为上下移动按钮？— **推荐 vuedraggable**（用户体验明显更好）
- [ ] 字典缓存失效：方案 A（简单，下次进入页面看到新数据）还是方案 B（精确，立即看到）？— **推荐方案 A**（admin 改字典是低频）
- [ ] 颜色字段：text 输入 + 正则校验 vs DynamicFormRenderer 加 color 类型？— **推荐 text + 正则**（YAGNI；下一期再加 color 类型）
