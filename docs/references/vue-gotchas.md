# Vue 陷阱与调试经验

记录开发过程中遇到的 Vue 3 陷阱、调试方法论和设计教训。

## 1. el-upload on-change 绑定陷阱

### 问题

```vue
<!-- ❌ 错误：内联表达式会在渲染时立即执行 -->
:on-change="$emit('file-change', $event)"

<!-- ✅ 正确：使用函数引用 -->
:on-change="onFileChange"
```

`:on-change="$emit('file-change', $event)"` 是内联表达式，Vue 会在组件渲染时**立即执行**它，而不是作为事件处理函数绑定。结果是 `on-change` 绑定到 `undefined`，el-upload 的文件选择事件永远不会触发。

### 正确写法

```vue
<script setup>
const emit = defineEmits(['file-change'])
const onFileChange = (file, fileList) => emit('file-change', file, fileList)
</script>

<template>
  <el-upload :on-change="onFileChange" />
</template>
```

### 调试方法

如果怀疑事件处理函数没有被调用，在函数入口加一行日志：

```javascript
async function handleFileChange(file, fileList) {
  console.log('[DEBUG] handleFileChange called', file?.name)
  // ...
}
```

如果没有看到日志，说明函数根本没被调用，问题在模板绑定而非函数逻辑。

## 2. 调试方法论：先验证调用，再改逻辑

### 原则

**不要在不确定的情况下修改函数内部逻辑。先用最小侵入方式定位问题。**

### 反面案例

调试"附件上传后详情页不显示文件名"问题时：

1. ❌ 假设问题在 `useTenderAiParse.js` 的逻辑，添加 store-then-parse 流程
2. ❌ 创建新文件 `doc-insight-utils.js` 做 URL 转换
3. ❌ 部署 3 次才找到根因

### 正确路径

1. ✅ 在 `handleFileChange` 入口加 `console.log`
2. ✅ 发现函数没被调用
3. ✅ 检查模板中的事件绑定
4. ✅ 发现 `:on-change="$emit(...)"` 是内联表达式
5. ✅ 修复为 `:on-change="onFileChange"`
6. ✅ 一次部署搞定

## 3. 避免过度设计

### 单函数文件

不要为一个 5 行函数创建单独文件：

```javascript
// ❌ 过度设计：创建 doc-insight-utils.js 只放一个函数
export function toDownloadUrl(fileUrl) {
  if (!fileUrl) return ''
  if (fileUrl.startsWith('doc-insight://')) {
    return `/api/doc-insight/download?fileUrl=${encodeURIComponent(fileUrl)}`
  }
  return fileUrl
}

// ✅ 直接内联到使用处
const sourceDocumentDownloadUrl = computed(() => {
  const url = props.tender?.sourceDocumentFileUrl
  if (!url) return ''
  if (url.startsWith('doc-insight://')) {
    return `/api/doc-insight/download?fileUrl=${encodeURIComponent(url)}`
  }
  return url
})
```

### 不必要的复杂流程

不要在不需要的地方添加复杂流程：

```javascript
// ❌ 过度设计：store-then-parse 两步流程
let storedDoc = null
try {
  const storeResponse = await tendersApi.storeTenderDocument(...)
  if (storeResponse?.success && storeResponse.data) {
    storedDoc = storeResponse.data
    applySourceDocumentMetadata(uploadFile, storedDoc)
  }
} catch (storeErr) { ... }

const parseResponse = storedDoc?.storagePath
  ? await tendersApi.parseExistingTenderDocument(...)
  : await tendersApi.parseTenderIntakeDocument(...)

// ✅ 简单直接：单次 parse 调用已经足够
const response = await tendersApi.parseTenderIntakeDocument(uploadFile, { entityId: 'create-tender' })
if (!response?.success) throw new Error(response?.msg || '文档自动识别失败')
applyParsedFields(response.data)
applySourceDocumentMetadata(uploadFile, response.data)
```

## 4. 一次部署原则

### 原则

调试时用 `console.log`，确认根因后再改代码部署。

### 流程

1. 在可疑函数入口加 `console.log`
2. 部署到服务器
3. 在浏览器控制台观察日志
4. 确认根因后，移除日志，修复代码
5. 最终部署

这比"改代码 → 部署 → 发现不对 → 再改 → 再部署"高效得多。

## 5. Vue 内联表达式 vs 函数引用

### 规则

| 写法 | 行为 |
|---|---|
| `:prop="fn"` | 绑定函数引用，事件触发时调用 |
| `:prop="fn()"` | 立即执行，绑定返回值 |
| `:prop="$emit('event')"` | 立即执行 emit，绑定 `undefined` |
| `@event="fn"` | 绑定函数引用（语法糖） |
| `@event="fn($event)"` | 内联处理器，$event 是第一个参数 |

### el-upload 的 on-change

el-upload 的 `on-change` 回调接收 `(uploadFile, uploadFiles)` 两个参数。使用内联处理器时 `$event` 只是第一个参数：

```vue
<!-- ❌ fileList 丢失 -->
:on-change="$emit('file-change', $event)"

<!-- ✅ 两个参数都传递 -->
:on-change="(file, fileList) => emit('file-change', file, fileList)"
```

## 6. el-select-v2 remote 模式 initialOptions 淹没搜索结果（CO-355）

### 现象
`mode="search"` 的 UserPicker 搜 `zrr` 能命中"郑蓉蓉"，但她和 37 个预加载的"固定人员"一起出现在下拉里。**其他搜索框都没问题，只有传 `:initial-options` 的入口有问题。**

### 根因链
1. 调用方在 `mode="search"` 下传 `:initial-options`（预加载候选人，如 TaskForm 的 37 个 assignable-candidates）
2. `UserPicker` 的 `mergedOptions` 把 `initialOptions` 与搜索结果按 id 合并 → 预加载项**始终在场**
3. el-select-v2 在 `remote` 模式下，`useSelect.mjs` 的 `isRemoteMethodValid = filterable && remote && isFunction(remoteMethod)` 成立时，`isValidOption` 对**所有**选项返回 `true` → 预加载项永不被前端过滤
4. 结果：搜索命中 + 预加载项一起展示

### 修复
`selectOptions` computed：搜索态（`mode==='search'` 且 `options.value.length>0`）只用搜索结果；无搜索时回落到 `mergedOptions`（含 initialOptions）保证已选值标签渲染。

```js
const searching = props.mode === 'search' && options.value.length > 0
const source = searching ? options.value : mergedOptions.value
```

### 判别信号
**"其他入口正常、只有传 `initialOptions` 的入口异常"** → 几乎一定是 `mergedOptions` 合并 + el-select-v2 remote 不过滤的叠加。遇到搜索结果"混在固定人员里"，先 grep `:initial-options` 看哪个入口传了预加载列表。

### 防复发
新增 `UserPicker.spec.js` 两条用例锁定：搜索态不混入 initialOptions / 无搜索时回落到 initialOptions。用 `git checkout -- UserPicker.vue` 还原组件验证测试 RED（固定人员与搜索命中同时出现）。

## 7. 调试方法论：后台能搜到 ≠ 前台能展现（CO-355 教训）

### 教训
这次 bug 修了 3 轮（PR !1130、#1139、#1148）都没解决用户问题，每轮都是正确的后端增强，但**全在错的方向**。用户一针见血："后台数据库能搜到，并不代表前台就能展现呀。"

### 错误路径回顾
| 轮次 | 假设 | 修复 | 结果 |
|---|---|---|---|
| 1 | `enabled=TRUE` 过滤掉 99% 员工 | 改 WHERE | 部分对，未解决 |
| 2 | `ORDER BY enabled DESC` 把结果压下去 | 改相关度排序 | 对的增强，未解决 |
| 3 | 拼音首字母 `zrr` 匹配不到 `zhengrongrong` | 加首字母支持 | 对的增强，未解决 |
| 4 | **前端 initialOptions 与搜索结果合并** | selectOptions 搜索态只用搜索结果 | **解决** |

前 3 轮全在"后端能否搜到"打转，没人在"前端能否展现"验证。直到用户点破才转向前端。

### 方法论：症状侧定位三问
遇到"搜索结果不对"类 bug，先问三个问题，**别急着改后端**：
1. **其他类似入口正常吗？** 正常 → 差异在异常入口特有的配置/数据流，不在公共后端
2. **浏览器实际渲染的 options 是什么？** 查 Vue devtools / 加 `console.log(selectOptions)`，而非假设
3. **数据流在哪一层"被合错了"？** 后端正确但前端展示错，问题在**合并/过滤/映射层**，不在查询层

### 红线
- **后端单测/接口验证通过 ≠ 前端展示正确**。后端验证只能证明"数据源对"，不能证明"渲染链路对"
- 改后端前，先用浏览器 devtools 确认**前端拿到的数据和渲染出的数据是否一致**。如果前端拿对了但渲染错，改后端永远修不好
- "反反复复"通常是**在错误的层修复正确的事**的信号——停下来重新定位症状发生的层

### 真实证据链
定位前端根因时，逐层取证据而非假设：
1. 对比 19 个 UserPicker 用法 → 只有 TaskForm 传 `initialOptions`
2. 读 `UserPicker.vue` mergedOptions → 确认合并逻辑
3. 读 el-select-v2 `useSelect.mjs` → 确认 remote 模式不过滤
4. 服务器验证 `/assignable-candidates?context=task` 返回 37 人 → 确认预加载量
5. 写测试用未修复组件复现"固定人员混入" → 闭环

每一步都有**可观测证据**，不是"我觉得是这里"。

## 8. el-form :disabled 传播导致 textarea 滚动条锁死（PR !2162）

### 问题

任务详情（`el-drawer` title="任务详情"）里所有 textarea 在 view 模式下被 `el-form` 的 `:disabled="readonly"` 传播导致 `disabled=true`，HTML 原生 `<textarea disabled>` **锁死滚动条**，无法查看超出可视高度的内容。

### 根因

- `<el-form :disabled="readonly">` 让所有子字段继承 disabled
- HTML 标准：`<textarea disabled>` 锁死滚动条且不可选择；`<textarea readonly>` 允许滚动+选择+复制，禁止编辑
- Element Plus `useFormDisabled` 用 `??`（nullish coalescing）合并 disabled：`disabled.value ?? unref(fallback) ?? form?.disabled ?? false`
- 显式 `:disabled="false"` 会通过 `??` 覆盖 form 级传播

### 修复模式

对 textarea **显式覆盖 `:disabled="false"`**，并把控制逻辑迁移到 `:readonly`：

```vue
<!-- ❌ 错误：disabled 锁死滚动条 -->
<el-input
  type="textarea"
  :disabled="readonly && !canDeliver"
/>

<!-- ✅ 正确：readonly 允许滚动+选择+复制，disabled=false 覆盖 form 级传播 -->
<el-input
  type="textarea"
  :readonly="readonly && !canDeliver"
  :disabled="false"
/>
```

### 关键正确性点

1. **条件必须等价迁移**：原 `:disabled="expr"` → `:readonly="expr"`，不能丢条件
2. **disabled=false 必须显式**：不显式覆盖会继承 form 级 disabled=true
3. **业务行为一致**：readonly 仍不可编辑，差异仅在于允许滚动+选择+复制（正向变化）

### 不适用场景

- 单行 `<el-input>` 不需要此修复（无滚动条锁死问题）
- `<el-select>` / `<el-upload>` / `<el-date-picker>` 保持 form 级 disabled 即可

### 验证方法

单元测试中 ElInput stub 需透传 `disabled` / `readonly` prop：

```js
ElInput: {
  name: 'ElInput',
  props: ['modelValue', 'type', 'rows', 'placeholder', 'disabled', 'readonly'],
  template: '<input :data-disabled="disabled" :data-readonly="readonly" />',
}

// 测试断言
expect(textarea.props('readonly')).toBe(true)
expect(textarea.props('disabled')).toBe(false)
```

## 5. el-dialog 打开时无脑 reset() 导致异步任务状态丢失（CO-602 / 2026-08-04）

### 问题

```vue
<!-- ❌ 错误：对话框打开时无论任务状态如何都 reset() -->
watch(() => props.visible, (val) => {
  if (val) {
    checkedTypes.value = []
    reset()  // ← 任务运行中/已完成/已失败时也会重置，导致状态丢失
  }
})
```

用户启动导出任务后关闭对话框，再次打开时：
- 任务仍在运行，但对话框回到初始状态（看不到进度）
- 任务已完成，但看不到"下载"按钮（无法下载文件）
- 任务已失败，但看不到"重试"按钮

### 修复

```vue
<!-- ✅ 正确：仅在无任务时重置 -->
watch(() => props.visible, (val) => {
  if (val) {
    const hasActiveTask = isRunning.value || isCompleted.value || isFailed.value
    if (!hasActiveTask) {
      checkedTypes.value = []
      reset()
    }
  }
})
```

### 教训

1. **对话框 watch visible 时必须区分"首次打开"和"重新打开"** — 有活跃任务时应保留状态
2. **reset() 是全量重置** — 调用前必须确认不会丢失用户需要的状态

## 6. formatBytes 重复实现导致精度不一致（CO-602 / 2026-08-04）

### 问题

项目中 3 处独立实现了字节格式化函数，精度规则不一致：

| 位置 | KB 精度 | MB 精度 |
|------|---------|---------|
| PerformanceBundleExportDialog | toFixed(1) | toFixed(2) |
| WarehouseExportPackageDetail | toFixed(2) | toFixed(2) |
| 其他组件 | 各不相同 | 各不相同 |

### 修复

提取公共工具 `src/utils/formatBytes.js`，统一规则：
- B：整数
- KB/MB/GB：保留 2 位小数
- 自动选择最合适的单位（1024 进制）
- 0 或负数返回 fallback 字符

### 教训

1. **工具函数必须集中管理** — 不能在多个组件中重复实现，否则精度规则不一致
2. **新增工具函数必须配套 spec 测试** — `src/utils/formatBytes.spec.js` 覆盖边界条件

## 7. 测试 mock 不完整导致 0-test 失败 + 断言过期（PR #2266 / 2026-08-04）

### 问题

`workbench-characterization.spec.js` 在 pre-commit hook 跑前端测试时失败，报 `No "createRouter" export is defined on the "vue-router" mock`（0 test），整个测试套件无法加载。

### 根因（三层问题，不止 vue-router mock）

1. **vue-router mock 缺失**：fixture 的 `vi.mock('vue-router', ...)` 只导出 `useRouter`，缺 `createRouter`/`createWebHistory`/`beforeEach`/`onError`。原设计依赖 stub UserPicker + mock usersApi 切断 `client.js → router/index.js` 导入链，但导入链随业务演进重新接通后崩溃。
2. **Pinia store 未 mock**：`WorkbenchNotifications.vue` 在 setup 顶层调用 `useNotificationStore`（Pinia），fixture 未 mock，组件挂载时崩溃。
3. **断言严重过期**：Workbench UI 改造（`193c96325` / `8b2943fb8`）移除了 QuickStart、Metrics、TenderList 等模块，原 case 2-7 断言全部失效。

### 修复

1. **vue-router mock 补全**：补 `createRouter`（返回含 `push`/`currentRoute`/`beforeEach`/`onError` 的 stub）、`createWebHistory`
2. **补 mock `@/stores/notifications`**：含 `fetchNotifications`/`markAsRead`/`loading`/`notifications`/`unreadCount`/`totalElements`
3. **重写 spec**：删除依赖已移除模块的 case，更新断言匹配当前渲染，锁定 TodoCategoryCards/DeadlinePanels/WorkbenchCalendarRebuild/WorkbenchNotifications 渲染结构

### 教训

1. **vi.mock 必须覆盖目标模块的所有顶层调用** — `router/index.js` 顶层调用 `createRouter`/`createWebHistory`/`router.beforeEach`/`router.onError`，mock 缺任一导出都会导致模块加载崩溃
2. **不能依赖"切断导入链"作为 mock 策略** — 业务代码演进会重新接通导入链，必须直接 mock 目标模块的所有使用点
3. **Pinia store 在 setup 顶层调用时必须在 fixture mock** — 否则组件挂载时 `getActivePinia()` 报错；项目惯例是 `vi.mock('@/stores/xxx', ...)` 而非初始化 Pinia
4. **characterization test 必须随 UI 重构同步更新** — 重构移除/替换模块时，依赖这些模块的测试 case 必须删除或更新，否则断言过期

