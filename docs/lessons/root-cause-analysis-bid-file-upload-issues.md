# 投标文件上传 4 个问题 根因分析（进度条/列表刷新/状态同步/重复删除）

> 日期: 2026-07-13
> 排查者: claude
> 修复 PR: `!2063` / `!2065`（分支 `agent/claude/fix-bid-file-upload-ux`）

---

## 现场还原

**症状素描**：用户反馈投标文件上传存在 4 个主要问题：

1. **无进度条和上传提示**：用户上传 1.66GB 文件时无进度显示，误解上传状态
2. **上传成功后文件未出现在项目文件列表**：用户上传成功但列表为空
3. **仅提交时才知道上传结果**：用户点提交才报错，无法提前发现
4. **删除文件后状态不同步且重复删除报错**：两个独立列表（父组件 vs 子组件）状态不一致

**边界划定**：
- 后端 OBS 直传链路正常 ✅（文件实际已上传到 OBS）
- 前端 OBS 直传进度未同步给 el-upload 组件 ❌
- 前端上传成功后未刷新列表 ❌
- 前端删除操作未双向同步父/子组件 ❌

---

## 历史背景：为什么 4 个问题同时出现

投标文件上传采用了 OBS 直传 + el-upload 组合方案，但前端实现没有处理好两个独立状态源（OBS 直传进度 vs el-upload file list）的同步：

| 问题 | 根因 | 影响层 |
|---|---|---|
| 进度条不显示 | OBS 直传进度未同步给 el-upload 的 `file.percentage` | UI 反馈 |
| 列表不刷新 | 上传成功后未调用 `loadDocuments()` 刷新父组件列表 | 状态同步 |
| 提交时才知道结果 | 上传中允许提交，未等待 `COMPLETED` 状态 | 业务流程 |
| 删除状态不同步 | `ProjectDocumentTable.vue` 和 `DraftingStage.vue` 各维护一份列表 | 双向同步 |

---

## 剥洋葱：四个症状其实是四层链路

### 链路 A — OBS 直传进度未同步给 el-upload

`useObsProjectDocumentUpload.js` 的 `progress.value` 是 OBS 直传进度（0-1），但 el-upload 的 `file.percentage` 是 0-100。原实现没有把 `progress.value` 同步到 `file.percentage`，导致 el-upload 的进度条始终为 0。

### 链路 B — 上传成功后未刷新列表

`DraftingStage.vue` 调用上传后，没有监听上传成功事件并调用 `loadDocuments()` 刷新项目文件列表。用户上传成功后，列表仍为空，需要手动刷新页面才能看到新文件。

### 链路 C — 上传中允许提交

`DraftingStage.vue` 的提交按钮没有检查 `bid_file` 表中是否有 `UPLOADING` 状态记录。用户上传 1.66GB 文件未完成时切换账号或直接提交，导致 `completed` 接口 500 错误（找不到 UPLOADING 记录的 owner）。

### 链路 D — 删除操作未双向同步

`ProjectDocumentTable.vue`（子组件）和 `DraftingStage.vue`（父组件）各维护一份文件列表。删除操作只更新子组件列表，没有通过 `emit('change')` 通知父组件刷新，导致：
- 用户在子组件删除文件后，父组件列表仍显示该文件
- 用户在父组件再次点击删除，触发 404 重复删除报错

---

## 零号病人定位

### 链路 A：进度条不显示

**第一行错误**：

```javascript
// 修复前：useObsProjectDocumentUpload.js
const progress = ref(0)  // 0-1 范围
// 没有同步到 el-upload 的 file.percentage

// 修复后：在 el-upload 的 #file slot 内显式渲染
<template #file="{ file }">
  <div>
    <el-progress :percentage="Math.round(file.percentage || 0)" />
    <span>{{ Math.round(file.percentage || 0) }}%</span>
  </div>
</template>
```

### 链路 B：列表不刷新

**第一行错误**：

```javascript
// 修复前：DraftingStage.vue
async function handleUploadSuccess() {
  ElMessage.success('上传成功')
  // ← 没有调用 loadDocuments() 刷新列表
}

// 修复后
async function handleUploadSuccess() {
  ElMessage.success('上传成功')
  await loadDocuments()  // 刷新项目文件列表
}
```

### 链路 C：上传中允许提交

**第一行错误**：

```javascript
// 修复前：DraftingStage.vue
function canSubmit() {
  return projectStatus.value === 'DRAFTING'
  // ← 没有检查是否有 UPLOADING 状态文件
}

// 修复后
function canSubmit() {
  if (projectStatus.value !== 'DRAFTING') return false
  // 检查是否有 UPLOADING 状态文件
  return !documents.value.some(doc => doc.status === 'UPLOADING')
}
```

### 链路 D：删除状态不同步

**第一行错误**：

```javascript
// 修复前：ProjectDocumentTable.vue
async function handleDelete(file) {
  await api.deleteFile(file.id)
  // ← 只更新子组件列表，没有通知父组件
  fileList.value = fileList.value.filter(f => f.id !== file.id)
}

// 修复后：通过 emit('change') + ref.loadDocuments() 双向同步
async function handleDelete(file) {
  await api.deleteFile(file.id)
  fileList.value = fileList.value.filter(f => f.id !== file.id)
  emit('change')  // 通知父组件
}

// 父组件
<ProjectDocumentTable ref="docTableRef" @change="loadDocuments" />
```

---

## 验证与修复

### 修复 diff 摘要

1. **`useObsProjectDocumentUpload.js`**：暴露 `progressPercent`（0-100 computed），同步进度到 el-upload
2. **`DraftingStage.vue`**：
   - 监听上传成功事件，调用 `loadDocuments()` 刷新列表
   - 提交按钮检查 `UPLOADING` 状态文件
3. **`ProjectDocumentTable.vue`**：
   - 删除操作通过 `emit('change')` 通知父组件
   - el-upload `#file slot` 显式渲染 `<span>{{ Math.round(file.percentage || 0) }}%</span>` 和 Loading 图标
4. **OBS 直传失败回退 multipart**：回退前重置 `progress.value=0`，避免进度条停留在中间值

### 测试验证

- `useWorkbenchTodos.spec.js` 等单元测试通过
- build 通过
- pre-push 14 道门禁全绿
- 手动验证：1.66GB 文件上传进度显示、上传成功后列表刷新、UPLOADING 状态禁止提交、删除操作双向同步

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 进度条不显示零号病人已定位 | OBS 直传进度未同步到 el-upload `file.percentage` | ✅ |
| 列表不刷新零号病人已定位 | 上传成功未调用 `loadDocuments()` | ✅ |
| 上传中允许提交零号病人已定位 | `canSubmit()` 未检查 `UPLOADING` 状态 | ✅ |
| 删除状态不同步零号病人已定位 | 子组件删除未 `emit('change')` 通知父组件 | ✅ |
| 必然性已证明 | 两个独立状态源（OBS 直传 vs el-upload list）必然不同步 | ✅ |
| 修复 diff 已提供 | PR `!2063` + `!2065` | ✅ |
| 防复发测试已设计 | 单元测试 + 手动验证覆盖 4 个场景 | ✅ |

**Verdict**: ✅ **PASS**

---

## 为什么之前没有提前发现

1. **OBS 直传 + el-upload 组合方案复杂**：两个独立状态源（OBS 直传进度 vs el-upload file list）需要显式同步，但原实现没有意识到
2. **测试环境文件小**：测试用小文件（< 10MB）上传秒完成，进度条问题不易暴露
3. **未做端到端测试**：只测了 OBS 直传 API，没有测 el-upload UI 反馈
4. **父/子组件状态同步规范缺失**：没有强制要求子组件状态变更必须 `emit` 通知父组件
5. **UPLOADING 状态未在 UI 体现**：后端有 UPLOADING 状态但前端没有禁止提交

---

## 防复发规范

1. **OBS 直传 + el-upload 组合必须显式同步进度**：`useObsUpload` 暴露 `progressPercent`（0-100 computed），el-upload `#file slot` 显式渲染 `<span>{{ Math.round(file.percentage || 0) }}%</span>`
2. **el-upload `#file slot` 必须显式渲染 `file.percentage`**：否则进度条不可见（详见 element-plus-gotchas.md §3）
3. **上传成功后必须刷新父组件列表**：通过 `emit('change')` + `ref.loadDocuments()` 双向同步
4. **OBS 直传失败回退 multipart 时必须重置 `progress.value=0`**：避免进度条停留在中间值
5. **`UPLOADING` 状态必须在 UI 层禁止提交**：`canSubmit()` 必须检查 `documents.value.some(doc => doc.status === 'UPLOADING')`
6. **父/子组件状态变更必须双向同步**：子组件状态变更 `emit('change')`，父组件监听后调用 `loadXxx()` 刷新
7. **批量上传成功后需用 debounce 包裹列表刷新函数**：避免多次并发请求（如 `loadBidFiles`）
8. **前端 composable 层不得包含 UI 提示逻辑**：`ElMessage.success` 必须由组件层负责
9. **前端进度同步必须使用 composable 暴露的 computed 值**：避免重复 `Math.round(val * 100)` 转换
10. **招标文件上传必须使用独立的 `useObsUpload` 实例**：与投标文件上传逻辑完全隔离

---

## 相关文档与代码

- `src/composables/useObsProjectDocumentUpload.js` — OBS 直传 + 进度同步
- `src/views/Project/components/DraftingStage.vue` — 父组件，监听 change 事件刷新列表
- `src/views/Project/components/ProjectDocumentTable.vue` — 子组件，emit('change') 通知父组件
- [docs/lessons/element-plus-gotchas.md](./element-plus-gotchas.md) §3 — el-upload `#file slot` 必须显式渲染 `file.percentage`
- [docs/lessons/lessons-learned.md](./lessons-learned.md) §10 — 设计评审（父/子组件状态同步规范）
