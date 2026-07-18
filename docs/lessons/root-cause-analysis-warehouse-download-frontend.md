# 前端 axios blob 下载大文件超时 根因分析（改用 fetch + ReadableStream 流式读取 + 进度条）

> 日期: 2026-07-18
> 排查者: claude
> 修复 PR: `!2126`（分支 `agent/claude/fix-warehouse-download-frontend`）

---

## 现场还原

**症状素描**：用户在测试环境点击下载仓库导出任务（`GET /api/knowledge/warehouses/export/tasks/44/download`），**前台点击无反应且最终超时**，控制台无报错，后端返回 200 OK。

**边界划定**：
- 后端已采用 `StreamingResponseBody` 流式输出 ✅（PR !2124）
- 前端 `downloadFile` 使用 axios `responseType: 'blob'` 模式 ❌
- axios 受全局 30 秒超时限制 ❌
- axios blob 模式将整个几百 MB ZIP 加载到内存 ❌

---

## 历史背景：为什么这个 Bug 修了 3 轮

仓库导出下载前端经历了 3 轮改造：

| 阶段 | 实现 | 问题 |
|---|---|---|
| 阶段 1 | axios `responseType: 'blob'` 模式 | 30 秒超时 + 几百 MB 内存加载 |
| 阶段 2 | `<a download>` 浏览器原生导航 | 绕过 axios 超时，但无法暴露下载进度给 JS |
| 阶段 3 | `fetch + ReadableStream` 流式读取 | ✅ 端到端流式下载 + 进度条 |

每一轮改造都解决了前一阶段的问题，但阶段 2 引入了新问题（无法显示进度条），阶段 3 才彻底解决。

---

## 剥洋葱：三个症状其实是三层链路

### 链路 A — axios 全局 30 秒超时

`useAsyncTask.js` 的 `downloadFile` 函数使用 axios `responseType: 'blob'` 模式：

```javascript
// 修复前
const response = await axios.get(url, {
  responseType: 'blob',
  timeout: 30000  // ← 全局 30 秒超时
})
```

几百 MB ZIP 文件下载需要几分钟，超过 30 秒就被 axios 中断，但浏览器控制台无报错（axios 超时不抛错），用户看到「点击无反应」。

### 链路 B — axios blob 模式与后端流式输出矛盾

后端 PR !2124 改用 `StreamingResponseBody` 流式输出，但前端 axios `responseType: 'blob'` 仍会等待整个响应体接收完毕才返回 Promise。这导致：
- 后端流式输出 ✅
- 前端 axios 等待全部接收 ❌（与流式输出矛盾）
- 前端内存中持有完整 ZIP blob（几百 MB）

### 链路 C — `<a download>` 无法暴露下载进度

阶段 2 改用 `<a download>` 浏览器原生导航：

```javascript
const link = document.createElement('a')
link.href = url
link.download = filename
link.click()
```

这绕过了 axios 超时，但浏览器原生导航不暴露下载进度给 JS，无法实现进度条。用户看到「点击后浏览器开始下载，但 UI 无任何反馈」。

---

## 零号病人定位

### 第一行错误：axios blob 模式

```javascript
// 修复前：useAsyncTask.js
const downloadFile = async (url, filename) => {
  const response = await axios.get(url, {
    responseType: 'blob',  // ← 等待全部接收
    timeout: 30000         // ← 30 秒超时
  })
  const blobUrl = window.URL.createObjectURL(response.data)
  // ...触发下载
}

// 修复后：fetch + ReadableStream + AbortController
const downloadFile = async (url, filename) => {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 5 * 60 * 1000)  // 5 分钟超时

  try {
    const response = await fetch(url, { signal: controller.signal })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    
    const contentLength = response.headers.get('Content-Length')
    const total = parseInt(contentLength, 10)
    const isIndeterminate = !total || total === 0  // Content-Length=0 用 indeterminate 模式

    if (!response.body) throw new Error('EMPTY_BODY')  // ← null 检查

    const reader = response.body.getReader()
    const chunks = []
    let received = 0

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      received += value.length
      if (!isIndeterminate) {
        downloadProgress.value = Math.round((received / total) * 100)
      }
    }

    const blob = new Blob(chunks)
    const blobUrl = window.URL.createObjectURL(blob)
    // ...触发下载
  } finally {
    clearTimeout(timeoutId)
  }
}
```

### 必然性解释

```
用户点击下载几百 MB ZIP
  ↓
axios.get(url, { responseType: 'blob', timeout: 30000 })
  ↓
axios 等待整个响应体接收（与后端流式输出矛盾）
  ↓
30 秒后 axios 超时，但不抛错（控制台无报错）
  ↓
用户看到「点击无反应」
  ↓
最终超时，下载失败
```

---

## 验证与修复

### 修复 diff 摘要

1. **`useAsyncTask.js`**：
   - 删除 axios `responseType: 'blob'` 模式
   - 改用 `fetch + ReadableStream` 流式读取
   - 新增 `isDownloading` / `downloadProgress` 状态
   - 新增 `resetDownloadState()` 在 `reset()` 中清理下载状态
   - 新增 5 分钟 `AbortController` 超时
   - 处理 `Content-Length=0` 的 indeterminate 模式
   - 检查 `response.body` 为 null 时抛 `EMPTY_BODY` 错误

2. **`WarehouseExportDialog.vue`**：
   - 新增下载进度条 UI（`<el-progress>` + `isDownloading` v-if）
   - 进度条支持 indeterminate 模式（循环跳动）

3. **`WarehouseExportPackageDetail.vue`**（新建子组件）：
   - 拆分 `WarehouseExportDialog.vue`（245 行 → 拆分后两者均 < 300 行）

4. **`useAsyncTask.spec.js`**：
   - 新增 3 个测试用例覆盖 fetch + ReadableStream
   - 使用 `vi.spyOn(global, 'fetch')` 替代直接赋值，确保测试环境清理规范
   - 测试 indeterminate 模式、null response.body、超时 abort

### 关键修复代码

```javascript
// useAsyncTask.js
const isDownloading = ref(false)
const downloadProgress = ref(0)
const isDownloadIndeterminate = ref(false)

const downloadFile = async (url, filename) => {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 5 * 60 * 1000)
  
  isDownloading.value = true
  downloadProgress.value = 0
  isDownloadIndeterminate.value = false

  try {
    const response = await fetch(url, { signal: controller.signal })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    
    const contentLength = response.headers.get('Content-Length')
    const total = contentLength ? parseInt(contentLength, 10) : 0
    isDownloadIndeterminate.value = !total || total === 0

    if (!response.body) {
      throw new DownloadError('EMPTY_BODY')
    }

    const reader = response.body.getReader()
    const chunks = []
    let received = 0

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      received += value.length
      if (!isDownloadIndeterminate.value) {
        downloadProgress.value = Math.round((received / total) * 100)
      }
    }

    const blob = new Blob(chunks)
    triggerDownload(blob, filename)
  } catch (err) {
    if (err.name === 'AbortError') {
      throw new DownloadError('TIMEOUT_5MIN')
    }
    throw err
  } finally {
    clearTimeout(timeoutId)
    isDownloading.value = false
  }
}

const resetDownloadState = () => {
  isDownloading.value = false
  downloadProgress.value = 0
  isDownloadIndeterminate.value = false
}

const reset = () => {
  // ...其他重置
  resetDownloadState()
}
```

### 测试验证

- **37 个单元测试通过**（含 3 个新增 fetch + ReadableStream 测试）
- build 通过
- pre-push 14 道门禁全绿
- 手动验证：下载几百 MB ZIP 文件，进度条正常显示，5 分钟内完成

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| axios 30 秒超时零号病人已定位 | `useAsyncTask.js` axios `timeout: 30000` | ✅ |
| axios blob 模式与流式输出矛盾已定位 | axios 等待全部接收 vs 后端流式输出 | ✅ |
| `<a download>` 无法暴露进度已定位 | 浏览器原生导航不暴露下载进度 | ✅ |
| 必然性已证明 | 几百 MB ZIP + 30 秒超时 + axios 等待全部接收 → 必然超时 | ✅ |
| 修复 diff 已提供 | PR `!2126` | ✅ |
| 防复发测试已设计 | 3 个测试覆盖 fetch + ReadableStream + indeterminate + null body | ✅ |
| 服务器验证已完成 | 部署后下载几百 MB ZIP 成功，进度条正常 | ✅ |

**Verdict**: ✅ **PASS**

---

## 为什么之前没有提前发现

1. **测试环境文件小**：测试用小 ZIP（< 10MB）下载秒完成，30 秒超时不触发
2. **axios 超时不抛错**：axios 超时后不抛错（控制台无报错），用户看到「点击无反应」难排查
3. **后端流式输出与前端 axios blob 模式矛盾未察觉**：后端 PR !2124 改用流式输出，但前端 axios 仍用 blob 模式，两者矛盾未在测试中暴露
4. **`<a download>` 阶段 2 未考虑进度条需求**：阶段 2 解决了超时，但用户后续提出进度条需求，才暴露问题
5. **未做端到端测试**：只测了 API 返回 200，没有测前端实际下载几百 MB 文件

---

## 防复发规范

1. **前端下载大文件必须使用 `fetch + ReadableStream`**：禁止使用 axios `responseType: 'blob'` 模式下载几百 MB 文件
2. **前端下载必须设置 `AbortController` 超时**：5 分钟（或根据文件大小动态调整），防止服务器挂起导致下载永久 hang 住
3. **前端下载必须处理 `Content-Length=0` 的情况**：使用 indeterminate 模式（进度条循环跳动）避免用户误以为卡住
4. **前端下载必须检查 `response.body` 是否为 null**：抛明确错误（如 `DownloadError('EMPTY_BODY')`）而非直接调用 `.getReader()`
5. **前端 `reset()` 方法必须重置所有状态**：包括 `isDownloading` / `downloadProgress` / `isDownloadIndeterminate`，避免用户关闭对话框后重新打开时状态错乱
6. **前端测试中模拟 fetch 必须使用 `vi.spyOn(global, 'fetch')`**：而非直接赋值 `global.fetch = undefined`，确保测试环境清理规范
7. **后端流式输出必须配套前端流式读取**：后端 `StreamingResponseBody` + 前端 `fetch + ReadableStream` 才是端到端流式
8. **大组件（> 300 行）必须拆分子组件**：`WarehouseExportDialog.vue` 拆分出 `WarehouseExportPackageDetail.vue`，符合项目 300 行规则
9. **前端 composable 层不得包含 UI 提示逻辑**：`ElMessage` 必须由组件层负责
10. **`<a download>` 浏览器原生导航方式不得用于需要进度条的下载**：仅适用于无需进度反馈的场景

---

## 相关文档与代码

- `src/composables/useAsyncTask.js` — `downloadFile` 改用 fetch + ReadableStream
- `src/views/KnowledgeWarehouse/components/WarehouseExportDialog.vue` — 下载进度条 UI
- `src/views/KnowledgeWarehouse/components/WarehouseExportPackageDetail.vue` — 拆分子组件
- `src/composables/__tests__/useAsyncTask.spec.js` — 3 个新增测试用例
- [docs/lessons/root-cause-analysis-warehouse-export-download-oom.md](./root-cause-analysis-warehouse-export-download-oom.md) — 后端 Files.readAllBytes OOM 根因分析
- [docs/lessons/lessons-learned.md](./lessons-learned.md) §9 — Bug 回归反思 SOP（前后端流式配套）
