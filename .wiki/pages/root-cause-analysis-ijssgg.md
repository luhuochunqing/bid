---
title: 立项招标文件上传 Bug 根因分析 (IJSSGG)
space: engineering
category: analysis
tags: [bug, 根因分析, 立项, 招标文件上传, 权限]
sources:
  - src/views/Project/stages/InitiationStage.vue
  - src/views/Project/stages/useInitiationStageActions.js
backlinks:
  - _index
created: 2026-06-13
updated: 2026-06-06
health_checked: 2026-06-27
---
# 立项招标文件上传 Bug 根因分析

Issue: https://gitee.com/allinai888/bid/issues/IJSSGG

---

## 现场还原

**症状素描**：立项流程中招标文件上传存在 3 层故障——A. 审核人看不到已上传文件；B. 提交后上传入口未关闭；C. 文件列表无上传人信息。

**边界划定**：Bug 仅在立项阶段（InitiationStage）的招标文件上传区域出现。项目详情页的通用文档列表（ProjectDetailDocumentsCard）上传人字段正常。非"提交立项"状态的页面不会触发这几个问题。

**思维沙箱**：核心拼图是 `bidDocFiles` 的填充时机和 `el-upload` 的 `disabled` 绑定。`bidDocFiles` 只在 `handleDocBeforeUpload` 中赋值，在 `load()` 中没有任何填充逻辑。

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口/参数层

#### Bug A: 审核人看不到文件

```javascript
// InitiationStage.vue:89-99
<el-upload v-model:file-list="bidDocFiles" ... />
```

- `bidDocFiles` 初始值为 `[]`（第 167 行）
- `load()` 调用 API → 填充 `form.tenderDocumentId` → **但不填充 `bidDocFiles`**
- 审核人打开页面时 `bidDocFiles = []` → `el-upload` 显示空文件列表
- 文件实际上传成功并存储在数据库，但前端没有去查

#### Bug B: 提交后上传入口未关闭

```javascript
// InitiationStage.vue:89-99
<el-upload ...>  ← 没有 :disabled 绑定
```

- `el-form` 有 `:disabled="locked"`（第 17 行）
- `el-upload` **没有** `:disabled` 绑定
- 提交后 `reviewStatus = 'PENDING_REVIEW'` → `locked = true`
- `el-form` 的表单字段被禁用，但 `el-upload` 仍然可交互

#### Bug C: 文件列表无上传人信息

```javascript
// useInitiationStageActions.js:88
bidDocFiles.value = [{ name, url, status: 'success' }]
```

- 上传成功后只存了 `{ name, url, status }`，没有 `uploader`
- `el-upload` 默认文件列表只显示文件名，没有自定义模板展示上传人
- `load()` 未从服务端获取文件元数据

---

### Layer 2 — 核心逻辑层

#### 状态机分叉点

提交前后状态变迁：

```
提交前: reviewStatus='DRAFT' | '' → locked=false → el-upload 可操作
提交后: reviewStatus='PENDING_REVIEW' → locked=true → el-form 禁用，但 el-upload 不禁用 ← Bug B
审核人打开: reviewStatus='PENDING_REVIEW' → bidDocFiles=[] → 看不到文件 ← Bug A
```

#### Bug A 分叉点

```
load() 调用 getInitiation(projectId)
  ↓
响应体包含 tenderDocumentId=123, name="招标文件.pdf", ...
  ↓
Object.assign(form, data) → form.tenderDocumentId = 123
  ↓
⚠️ 没有任何代码把文件信息映射到 bidDocFiles
  ↓
bidDocFiles = [] → el-upload 显示为空
```

#### Bug B 分叉点

```
submit() → API 调用成功 → reviewStatus='PENDING_REVIEW'
  ↓
locked = computed(() => reviewStatus === 'PENDING_REVIEW') → true
  ↓
el-form :disabled="locked" → 表单字段禁用 ✅
el-upload (无 :disabled) → 上传按钮仍可点击 ❌
```

#### Bug C 分叉点

```
handleDocBeforeUpload() → API 上传成功
  ↓
result.data = { id, name, url, uploader: "张三", ... }
  ↓
bidDocFiles.value = [{ name: result.data.name, url: result.data.fileUrl, status: 'success' }]
  ↓
⚠️ uploader 字段被丢弃
  ↓
el-upload 文件列表只显示文件名，没有上传人
```

---

### Layer 3 — 数据层

**后端存储是正确的**：
- `ProjectDocument` entity 有 `uploaderName` 字段 → 入库 ✅
- `ProjectDocumentViewAssembler.toDto()` maps `uploaderName → uploader` ✅
- 通用文档列表 API `GET /api/projects/{id}/documents` 返回 `uploader` 字段 ✅
- `InitiationViewDto` 有 `tenderDocumentId` → 前端拿到 ✅

**前端断层**：
- `load()` 拿到 `tenderDocumentId` 但不去查文档详情
- `bidDocFiles` 存储格式丢失 uploader
- `el-upload` 默认文件列表无上传人展示位
- 没有 `:disabled` 绑定

---

## 零号病人定位

**第一行错误（Bug A+B 的根源）：**

```initiation-stage.vue:89
<el-upload
  v-model:file-list="bidDocFiles"
  :before-upload="handleDocBeforeUpload"
  :limit="1"
  accept=".pdf,.doc,.docx"
  drag
>
```

**第一行错误（Bug C 的根源）：**

```useInitiationStageActions.js:88
bidDocFiles.value = [{ name: result.data.name || file.name, url: result.data.fileUrl || '', status: 'success' }]
```

**必然性解释（Bug A）：**
因为 `load()` 调用 `getInitiation()` 后只执行了 `Object.assign(form, data)` 将 `tenderDocumentId` 写入表单，但没有调用 `getDocuments()` 或任何其他 API 来获取文件的 `name` 和 `url`，所以 `bidDocFiles` 始终为 `[]`。审核人打开页面时，`el-upload` 的 `v-model:file-list="bidDocFiles"` 绑定空数组，必然显示空文件列表。

**必然性解释（Bug B）：**
因为 `el-upload` 组件缺少 `:disabled="locked"` 属性。提交后 `reviewStatus` 变为 `'PENDING_REVIEW'`，`locked` computed 变为 `true`，但这个状态只传递给了 `el-form :disabled`，没有传递给 `el-upload`。项目负责人在提交后仍然能看到并操作上传按钮，这是组件属性的缺失导致的必然结果。

**必然性解释（Bug C）：**
因为 `handleDocBeforeUpload` 在构造 `bidDocFiles` 条目时只取了 `name` 和 `url`，丢弃了 API 响应中的 `uploader` 字段。同时 `el-upload` 的默认文件列表模板不展示上传人信息。即使后端正确存储了 `uploaderName`，前端也没有在 UI 中展示它。

**状态变迁图：**
```
用户上传文件 → handleDocBeforeUpload → API 返回 {id,name,url,uploader:"张三"}
                                         ↓
                                     bidDocFiles = [{name, url, status}] ← uploader 丢失 (Bug C)
                                         ↓
提交立项 → submit() → reviewStatus = 'PENDING_REVIEW'
                        ↓             ↓
                    locked=true    el-upload无:disabled (Bug B)
                        ↓
审核人打开页面 → load() → Object.assign(form, data)
                            ↓
                        form.tenderDocumentId = 123
                            ↓
                        bidDocFiles 仍为 [] (Bug A)
```

---

## 验证与修复

### Bug A 修复：`load()` 中填充 bidDocFiles

```diff
// useInitiationStageActions.js
  async function load() {
    try {
      const response = await projectLifecycleApi.getInitiation(props.projectId)
      const data = response?.data || response
      if (!data) return
      Object.assign(form, data)
+     // 如果有招标文件 ID，获取文件信息并填充 bidDocFiles
+     if (data.tenderDocumentId) {
+       try {
+         const docResp = await projectsApi.getDocuments(props.projectId, {
+           params: {
+             documentCategory: 'TENDER_DOCUMENT',
+             linkedEntityType: 'PROJECT_INITIATION',
+             linkedEntityId: props.projectId,
+           }
+         })
+         const docs = docResp?.data || []
+         if (docs.length > 0) {
+           const doc = docs[0]
+           bidDocFiles.value = [{ name: doc.name, url: doc.fileUrl || '', status: 'success' }]
+         }
+       } catch (e) {
+         console.warn('[InitiationStage] failed to load tender document info', e)
+       }
+     }
      if (data.customerInfoRows) {
```

**最小验证**：在 `load()` 方法中加 `console.log('bidDocFiles after load:', bidDocFiles.value)`。审核人身份打开立项页面 → 控制台应看到文件列表非空。

### Bug B 修复：`el-upload` 加 disabled 绑定

```diff
// InitiationStage.vue
  <el-upload
    v-model:file-list="bidDocFiles"
    :before-upload="handleDocBeforeUpload"
    :limit="1"
    accept=".pdf,.doc,.docx"
    drag
+   :disabled="locked && !isApprovalMode"
  >
```

**逻辑解释**：
- `locked && !isApprovalMode`：当且仅当表单锁定且当前用户不是审批人时禁用上传
- 项目负责人提交后 → `locked=true, isApprovalMode=false` → 上传禁用 ✅
- 审核人审批时 → `locked=true, isApprovalMode=true` → 保留补充上传权限 ✅（符合业务需求："审核人支持补充上传文件"）
- 审核通过后 → `reviewStatus=APPROVED, isApprovalMode=false` → 上传禁用 ✅

### Bug C 修复：给 el-upload 加自定义文件列表模板展示上传人

```diff
// InitiationStage.vue
  <el-upload
    v-model:file-list="bidDocFiles"
    :before-upload="handleDocBeforeUpload"
    :limit="1"
    accept=".pdf,.doc,.docx"
    drag
+   :disabled="locked && !isApprovalMode"
-  >
+  >
+   <template #file="{ file }">
+     <div class="bid-doc-file-item">
+       <span class="bid-doc-file-name">{{ file.name }}</span>
+       <span class="bid-doc-file-uploader" v-if="file.uploader">上传人：{{ file.uploader }}</span>
+       <el-button link type="danger" size="small" @click="handleRemoveDoc(file)">删除</el-button>
+     </div>
+   </template>
```

```diff
// useInitiationStageActions.js:88
- bidDocFiles.value = [{ name: result.data.name || file.name, url: result.data.fileUrl || '', status: 'success' }]
+ bidDocFiles.value = [{ name: result.data.name || file.name, url: result.data.fileUrl || '', uploader: result.data.uploader || userStore.userName, status: 'success' }]
```

```diff
// InitiationStage.vue script
+ function handleRemoveDoc(file) {
+   bidDocFiles.value = bidDocFiles.value.filter(f => f.uid !== file.uid)
+ }
```

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `InitiationStage.vue:89` + `useInitiationStageActions.js:88` | ✅ |
| 必然性已证明 | 逻辑闭环：`load()` 不填充 bidDocFiles → 审核人看不到；el-upload 无 `:disabled` → 提交后仍可操作；bidDocFiles 丢弃 uploader → 列表无上传人 | ✅ |
| 最小验证已设计 | 步骤见上 | ✅ |
| 修复 diff 已提供 | 三个独立 diff | ✅ |
| 防复发测试已设计 | 见下 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. **Bug A 防复发**：加一个检查点——`load()` 执行后，如果 `form.tenderDocumentId` 非空，`bidDocFiles` 数组必须非空。
2. **Bug B 防复发**：检查 `el-upload` 的 `:disabled` 绑定——当 `locked=true && isApprovalMode=false` 时，上传按钮必须为禁用状态。
3. **Bug C 防复发**：检查上传后的 `bidDocFiles` 条目是否包含 `uploader` 字段。
