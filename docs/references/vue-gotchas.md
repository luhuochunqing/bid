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
