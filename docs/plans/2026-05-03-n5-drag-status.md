# N5: 拖拽改状态 + E2E reality check

- 日期：2026-05-03
- 范围：小迭代（~1 天），兑现"消化 vuedraggable 依赖 + TaskBoard UX 升级 + 把前 4 期 E2E 真正跑一遍"
- 前置：PR #149 / #151 / #155 / #157 全部合并；vuedraggable@4 已装但从未 import（来自 PR #155 M-C1）

---

## 1. 目标

**N5**：用户在任务看板上把任务卡片从一列拖到另一列 = 状态变更。保留现有下拉菜单"设为X"作为降级/键盘操作入口。

**E2E reality check**：前 4 期每个 PR 都说"E2E authored, CI 会跑"。现在把本机 stack 起来真跑一遍，修应该飘掉的 selector/时序；commit 修复；未来的 E2E 才有基线。

---

## 2. 决策

### 决策 1：vuedraggable 只包 `column-content`，不动 `board-column` 外壳
- 列标题（column-header）不拖
- 列之间不拖（列顺序由字典 `sort_order` 决定，不让用户手动乱序）
- 每个 `column-content` 是一个 `<draggable>` 组件实例，`group: 'task-cards'` 打通跨列拖拽

### 决策 2：拖拽触发的 API = 复用现有 `PATCH /api/projects/{id}/tasks/{taskId}/status`
- 不新加后端端点（同 N5 设计文档第 1 节）
- 服务端 sortOrder 变动**不持久化**：看板内任务的相对顺序只在 UI 内短时存在；刷新后按字典列分组显示。长期要做任务自己的排序字段是另一个 feature（不在 N5 范围）

### 决策 3：失败处理 = 乐观 UI + 失败 rollback
- 拖到新列立即 emit status change
- store.updateTaskStatus 失败时：`ElMessage.error` + 调 `projectStore` 把任务重新归位到原列
- 成功时：无 toast 吵闹（拖拽本身就是视觉反馈）

### 决策 4：移动端不做特殊优化
- SortableJS 自带 touch 支持
- iPad 长按+拖应该直接工作；不做"移动端不显示拖拽 / 用别的交互"的分支

### 决策 5：保留下拉菜单
- 键盘/屏幕阅读器用户需要；单独靠拖拽不可及
- 不再有"更多操作"的其他内容（当前就是"设为 X"），保留即可

---

## 3. 实现

### 3.1 TaskBoard.vue 改动

**模板**：把 `<div class="column-content">` 替换成 `<draggable>`：

```vue
<draggable
  :model-value="getTasksByStatus(column.key)"
  :group="{ name: 'task-cards' }"
  :item-key="(t) => t.id"
  :disabled="isStatusTransitionInFlight"
  class="column-content"
  ghost-class="task-card-ghost"
  drag-class="task-card-dragging"
  @change="(evt) => onDragChange(evt, column.key)"
>
  <template #item="{ element: task }">
    <div class="task-card" ...>
      <!-- 现有 task-card 内部不变 -->
    </div>
  </template>
</draggable>
```

**关键点**：
- `:model-value="..."` 是 computed-derived 数组（不是 ref），vuedraggable 支持这种模式——只要 `@change` 处理好同步
- 我们不用 vuedraggable 自己维护数组，只监听 `@change` 捕获跨列添加事件

### 3.2 `onDragChange` 逻辑

```js
const isStatusTransitionInFlight = ref(false)

async function onDragChange(evt, targetColumnKey) {
  // vuedraggable 的 change 事件有 added / removed / moved 三种子事件
  if (!evt.added) return  // 只关心 added: 本列收到了新任务
  const task = evt.added.element
  const fromStatus = task.status
  const toStatus = targetColumnKey
  
  if (normalizeStatus(fromStatus) === toStatus) return  // 同列内挪动，忽略
  
  isStatusTransitionInFlight.value = true
  try {
    await projectStore.updateTaskStatus(props.projectId, task.id, toStatus)
    emit('status-change', task, toStatus)  // 保留事件给上层审计/activity log
  } catch (err) {
    ElMessage.error(`状态修改失败：${err?.message || err}`)
    // Vue 响应式会自动把 task 归位——因为 task.status 没变，下个渲染周期会重新按 status 归列
  } finally {
    isStatusTransitionInFlight.value = false
  }
}
```

**关键 trick**：vuedraggable 的 `model-value` 我们传的是 computed `getTasksByStatus(column.key)`。当拖拽结束时 vuedraggable 会更新它的"视觉位置"，但我们没有写任何 setter，所以源数据不变。Vue 下个 tick 会重新 render，如果 status 没变（失败场景）卡片会回到原位。如果 status 变了（成功场景），卡片已经在新列里。

**这种依赖 Vue 响应式归位的做法有一个前提**：`getTasksByStatus` 是 computed 而不是缓存结果。检查一下原实现——它是 `props.tasks.filter(...)`，每次调用重新计算，OK。

### 3.3 下拉菜单保留

不动。作为 accessibility fallback + 键盘用户入口。

### 3.4 样式

加两个 class：
- `.task-card-ghost`：拖拽过程中的占位卡（半透明、虚线边框）
- `.task-card-dragging`：正被拖的卡片本体（阴影加强、tilt 2deg）

### 3.5 单元测试

`TaskBoard.spec.js` 加 2 case：
- `onDragChange added triggers updateTaskStatus with correct target`
- `onDragChange fails → shows error toast + ref flag reset`

拖拽本身在 jsdom 里做不了（SortableJS 依赖 DOM 事件序列）—**E2E 承担真实拖拽验证**。

### 3.6 E2E

现有 spec `e2e/task-board-customization.spec.js` 里那个"已完成"切换的 case，用**拖拽代替**下拉菜单做一次验证：

```js
// 替换/并列：
const card = page.locator('.column-content .task-card').filter({ hasText: '...' }).first()
const targetColumn = page.locator('.board-column').filter({ hasText: '已完成' }).locator('.column-content')
await card.dragTo(targetColumn)
await expect(progressTag).toContainText('100%')
```

Playwright 的 `locator.dragTo()` 对 SortableJS 支持一般——如果搞不定，**fall back 用现有下拉菜单**，记录 note 说"拖拽测试交 CI 或手工"。不要为了这个 case 耗 1 小时。

---

## 4. 工作量

| 块 | 估时 |
|---|---|
| E2E reality check（起 stack + 跑 + 修 selector） | 1.5h |
| N5 TaskBoard.vue + 样式 | 1.5h |
| 单测 2 case | 45m |
| E2E 新增拖拽 case | 45m |
| 门禁 + PR | 30m |
| **合计** | **~5h（半个工作日）** |

---

## 5. 风险

1. **本地 stack 起不来**：maven 首次 compile 可能 flakey；多次 PR 里我们已经踩过。**Pragma**：如果 60s 起不来，不死等——E2E reality check 降级成"CI 验证 + 手工抽查"，继续 N5 主线
2. **vuedraggable + Vue 3 + `model-value` 模式**：理论可行，但 API 有一些 edge case。如果 `@change` 行为和文档不符，第一 fallback 是让 vuedraggable 自己维护一个本地 array（不走 computed）；第二 fallback 是把拖拽降级为"长按后显示 move menu"
3. **跨列拖拽的触发时机**：SortableJS 的 `sort: true` 模式下，同列内移动也会触发 onChange，我们 early return 避免副作用

---

## 6. 不在本期范围

- 卡片在列内的手动排序持久化（需要 task 级 sortOrder 字段，大改）
- 拖拽到 review 列触发审批流集成
- 拖拽操作的 undo/redo
- 移动端的特殊拖拽 UX

---

## 7. 任务切分

1. **pre-check**：本地 stack 起来
2. **reality check**：跑现有 E2E 4 个 test，修 selector/timing
3. **N5-a**：TaskBoard.vue 接入 vuedraggable + 单测
4. **N5-b**：E2E 加拖拽 case
5. **gate + PR**：前后端门禁 + push + gh pr create

每步 TDD；每步一个 commit。
