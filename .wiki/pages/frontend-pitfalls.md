---
title: 前端 Vue3 / Element Plus 陷阱集
space: engineering
category: guide
tags: [前端, Vue3, Element Plus, reactive, ref, v-model, el-upload, el-form, 权限, E2E]
sources:
  - src/
  - .wiki/pages/lessons-learned.md
backlinks:
  - _index
  - lessons-learned
  - design-system
created: 2026-07-10
updated: 2026-07-10
health_checked: 2026-07-10
---
# 前端 Vue3 / Element Plus 陷阱集

> 从 8 个工作区历史对话中提取的前端实战陷阱。
> 涵盖 Vue 3 响应式、Element Plus 组件、表单校验、权限检查、E2E 选择器等高频踩坑点。

---

## 1. Vue 3 reactive context 自动解包 ref 导致 v-model 绑定失效

### 1.1 事故

```vue
<script setup>
const state = reactive({
  user: ref({ name: '' })  // ref 嵌套在 reactive 中
})

// 在 template 中 state.user.name 失效
</script>

<template>
  <el-input v-model="state.user.name" />  <!-- 不响应 -->
</template>
```

### 1.2 根因

Vue 3 的 reactive 会自动解包顶层的 ref，但**嵌套在对象中的 ref 行为不一致**：
- `state.user` 解包后是普通对象，不再是响应式
- `state.user.name` 的变更不会触发视图更新

### 1.3 正确做法

```vue
<script setup>
// 方案 1：不要嵌套 ref
const user = reactive({ name: '' })

// 方案 2：用 ref 包装整个对象
const user = ref({ name: '' })
</script>

<template>
  <el-input v-model="user.name" />
</template>
```

### 1.4 教训

- **ref 和 reactive 不要混合使用**
- **reactive 内部不要嵌套 ref**
- **响应式数据结构越扁平越好**

---

## 2. el-upload customUpload 双重回调反模式

### 2.1 事故

el-upload 组件同时使用 `:http-request` 和 `:on-success`，导致上传回调被触发两次，重复处理。

### 2.2 反模式

```vue
<el-upload
  :http-request="customUpload"
  :on-success="handleSuccess"  <!-- 重复：customUpload 已经处理了 -->
>
```

### 2.3 根因

- `:http-request` 替换了默认上传行为，回调由 `customUpload` 内部决定
- `:on-success` 是默认上传行为的回调，与 `:http-request` 互斥

### 2.4 正确做法

```vue
<el-upload
  :http-request="customUpload"
  <!-- 不要同时使用 :on-success -->
>
  <template #trigger>
    <el-button>上传</el-button>
  </template>
</el-upload>

<script setup>
const customUpload = async (options) => {
  const { file, onProgress, onSuccess, onError } = options
  try {
    const result = await uploadApi(file)
    onSuccess(result)  // 手动调 onSuccess
  } catch (e) {
    onError(e)
  }
}
</script>
```

### 2.5 教训

- **`:http-request` 和 `:on-success` 互斥，不要同时使用**
- **customUpload 内部要手动调用 onSuccess/onError**
- **el-upload 文档要仔细读，回调关系容易混淆**

---

## 3. el-form-item :required 属性注入默认校验消息覆盖自定义 rules

### 3.1 事故

```vue
<el-form-item label="手机号" :required="true" prop="phone" :rules="phoneRules">
  <el-input v-model="form.phone" />
</el-form-item>
```

期望使用 `phoneRules` 中的自定义消息，但实际显示的是 Element Plus 默认的"请输入"消息。

### 3.2 根因

`:required="true"` 会自动注入一条 `required: true` 的校验规则，覆盖 `rules` 中的同名规则。

### 3.3 正确做法

```vue
<!-- 方案 1：在 rules 中定义 required，不要用 :required 属性 -->
<el-form-item label="手机号" prop="phone" :rules="phoneRules">
  <el-input v-model="form.phone" />
</el-form-item>

<script setup>
const phoneRules = [
  { required: true, message: '请输入有效的手机号', trigger: 'blur' },
  { pattern: /^\d{11}$/, message: '手机号格式错误', trigger: 'blur' }
]
</script>

<!-- 方案 2：:required 仅用于显示星号，不定义 rules -->
<el-form-item label="备注" :required="true">
  <el-input v-model="form.remark" />
</el-form-item>
```

### 3.4 教训

- **`:required` 属性会自动注入校验规则**，与 `rules` 冲突
- **校验规则统一在 `rules` 中定义**，不要混用
- **`:required` 只用于显示星号**，不用于校验逻辑

---

## 4. Element Plus el-container 与 CSS Grid 冲突导致冷启动窄屏

### 4.1 事故

页面首次加载时出现短暂的"窄屏"布局（宽度只有期望的 1/3），刷新后恢复正常。

### 4.2 根因

- `el-container` 使用 flex 布局
- 页面同时用了 CSS Grid 布局
- 两者在初始渲染时可能冲突，导致宽度计算错误

### 4.3 解决

```vue
<!-- 方案 1：避免 el-container 和 CSS Grid 混用 -->
<div class="page-grid">  <!-- 用 div 替代 el-container -->
  <aside class="sidebar">...</aside>
  <main class="content">...</main>
</div>

<style>
.page-grid {
  display: grid;
  grid-template-columns: 240px 1fr;
}
</style>

<!-- 方案 2：在 el-container 外层包裹固定宽度 -->
<el-container style="width: 100%">
  ...
</el-container>
```

### 4.4 教训

- **el-container (flex) 和 CSS Grid 不要在同级混用**
- **冷启动布局问题通常是 CSS 加载时序导致**
- **关键布局用固定宽度**，避免依赖动态计算

---

## 5. UserPicker mode=search vs mode=candidates 必须区分

### 5.1 事故

UserPicker 组件有两种模式，混淆使用导致功能异常：
- `mode="search"` — 搜索全公司人员
- `mode="candidates"` — 从候选人列表中选择

### 5.2 区分要点

| 模式 | 数据源 | 适用场景 |
|------|--------|---------|
| `search` | 全公司 users 表 | 任意人员选择（如分配任务） |
| `candidates` | 传入的 candidates 数组 | 限定范围选择（如项目成员） |

### 5.3 教训

- **UserPicker 的 mode 必须与业务场景匹配**
- **不要用 search 模式选择候选人**（会选到不在候选范围的人）
- **不要用 candidates 模式做全公司搜索**（候选列表为空时无法选择）

---

## 6. 前端通知跳转失效：sourceEntityType 大小写不一致

### 6.1 事故

点击通知后跳转失败，根因是 `sourceEntityType` 字段大小写不一致：
- 后端返回：`"TENDER"`（大写）
- 前端判断：`sourceEntityType === 'tender'`（小写）

### 6.2 修复

```javascript
// ❌ 错误：大小写敏感比较
if (notification.sourceEntityType === 'tender') { ... }

// ✅ 正确：统一转大写比较
if (notification.sourceEntityType?.toUpperCase() === 'TENDER') { ... }
```

### 6.3 教训

- **跨层字段比较必须统一大小写**
- **后端枚举通常是大写，前端比较时要转大写**
- **或在前端定义枚举常量，避免硬编码字符串**

---

## 7. 前端权限检查应集中到 composables，用 every（AND）而非 some（OR）

### 7.1 事故

```javascript
// ❌ 错误：用 some，只要有一个权限就通过
const canEdit = user.permissions.some(p => ['tender:edit', 'admin'].includes(p))

// 问题：用户只有 'admin' 字符串但不是管理员，也会通过
```

### 7.2 正确做法

```javascript
// composables/usePermission.js
export function usePermission() {
  const userStore = useUserStore()

  const hasPermission = (requiredPermissions) => {
    if (!Array.isArray(requiredPermissions)) {
      requiredPermissions = [requiredPermissions]
    }
    // every: 必须所有权限都有
    return requiredPermissions.every(p => userStore.permissions.includes(p))
  }

  const hasAnyPermission = (permissions) => {
    // some: 任一权限即可（明确语义）
    return permissions.some(p => userStore.permissions.includes(p))
  }

  return { hasPermission, hasAnyPermission }
}
```

### 7.3 教训

- **权限检查统一封装到 composables**，不要散落在各组件
- **every vs some 要明确语义**：AND 用 every，OR 用 some
- **权限点用常量定义**，不要硬编码字符串

---

## 8. E2E 选择器优先级

### 8.1 事故

E2E 测试用 `getByText('搜索')` 定位按钮，UI 改版后按钮文案变成"筛选"，测试全部失败。

### 8.2 规范

E2E 选择器优先级（从高到低）：

```javascript
// 1. 优先用 role（语义化）
await page.getByRole('button', { name: '搜索' }).click()
await page.getByRole('heading', { name: '项目详情' }).click()

// 2. 其次用 testid（稳定）
await page.getByTestId('search-button').click()

// 3. 再次用 label
await page.getByLabel('关键词').fill('xxx')

// 4. 最后用 text（最不稳定）
await page.getByText('搜索').click()
```

### 8.3 教训

- **E2E 选择器优先用 role > testid > label > text**
- **避免用 text 定位**（文案会变）
- **新增 UI 元素（面包屑等）后跑关联 E2E**，避免选择器冲突
- **详见 [[lessons-learned]] §4.3**

---

## 9. ApiResponse JSON 字段名 `msg` vs `message`

### 9.1 事故

`ApiResponse` 用 `@JsonProperty("msg")` 输出 JSON 字段 `msg`，但测试断言 `$.message`，导致 40 个测试失败。

### 9.2 根因

```java
public class ApiResponse<T> {
    @JsonProperty("msg")  // JSON 输出是 msg
    private String message;  // Java 字段名是 message
}
```

测试断言 `$.message` 找不到字段。

### 9.3 修复

```javascript
// ❌ 错误
expect(response.body).to.have.property('message')

// ✅ 正确
expect(response.body).to.have.property('msg')
```

### 9.4 教训

- **JSON 字段名以 @JsonProperty 为准**，不是 Java 字段名
- **修改 API 响应格式后，同步检查测试断言**
- **用 `grep -rn '\$\.message' src/test/` 找所有断言**
- **详见 [[lessons-learned]] §4.5**

---

## 10. VITE_ 环境变量打包时未注入导致功能静默失效

### 10.1 事故

前端构建时未设置 `VITE_API_BASE_URL`，打包后的静态文件中 API 请求地址为空，功能静默失效（无报错，但请求都失败）。

### 10.2 根因

Vite 只在构建时注入 `import.meta.env.VITE_*` 变量，运行时无法更改。如果构建时未设置，运行时 `import.meta.env.VITE_API_BASE_URL` 是 `undefined`。

### 10.3 正确做法

```bash
# 构建时必须设置所有 VITE_ 变量
VITE_API_MODE=api \
VITE_API_BASE_URL=https://api.example.com \
npm run build

# 或用 .env.production 文件
echo "VITE_API_MODE=api" > .env.production
echo "VITE_API_BASE_URL=https://api.example.com" >> .env.production
```

### 10.4 教训

- **VITE_ 变量是构建时注入**，运行时无法更改
- **构建脚本必须显式设置所有必需的 VITE_ 变量**
- **功能静默失效要检查 import.meta.env 是否为 undefined**

---

## 11. 前端路由组件替换必须保留旧路径

### 11.1 事故

将 `/knowledge/case` 路由从 `Case.vue` 直接替换为 `CaseGrid.vue`，导致：
- E2E 播种的传统 Case 数据在新页面不可见
- E2E 选择器（getByText('搜索')）失效（新页面是"筛选"）

### 11.2 正确做法

用 Tab 包装器兼容新旧视图：

```vue
<template>
  <CaseWrapper>
    <el-tab-pane label="AI 案例网格" name="grid">
      <CaseGrid />
    </el-tab-pane>
    <el-tab-pane label="传统案例库" name="legacy">
      <Case />
    </el-tab-pane>
  </CaseWrapper>
</template>
```

### 11.3 教训

- **路由组件替换不要"一刀切"**
- **用 Tab 包装器兼容旧视图**，保留数据播种通道
- **E2E 选择器要跟随 UI 变更同步修改**
- **详见 [[lessons-learned]] §三**

---

## 12. 业务层 catch 块调用 ElMessage.error 覆盖全局 429 友好提示

### 12.1 事故

全局 axios interceptor 已在收到 429 时展示友好提示「请求过于频繁，请稍后再试」。但业务层 catch 块又直接调用 `ElMessage.error`，把原始 `AxiosError: Request failed with status code 429` 暴露给用户，导致用户以为系统报错。

```vue
<script setup>
import { ElMessage } from 'element-plus'
import { resourcesApi } from '@/api'

async function loadAccounts() {
  try {
    accounts.value = await resourcesApi.accounts.getList()
  } catch (e) {
    console.error('Failed to load accounts:', e)
    // ❌ 错误：覆盖全局 429 提示
    ElMessage.error(e.message || '账户数据加载失败')
  }
}
</script>
```

### 12.2 根因

- 全局 interceptor 负责 429 的友好提示和静默退避
- 业务层 catch 块拿到同样的 error，再次 `ElMessage.error` 会把 interceptor 的提示覆盖或叠加
- 生产环境用户看到的是 `AxiosError: Request failed with status code 429`，体验极差

### 12.3 修复

统一使用 `notifyErrorUnlessRateLimit`，对 429 错误静默，其他错误再弹窗：

```javascript
// src/api/error-utils.js
import { ElMessage } from 'element-plus'

export function isRateLimitError(error) {
  return error?.response?.status === 429
}

export function notifyErrorUnlessRateLimit(error, fallbackMessage) {
  if (isRateLimitError(error)) return
  const serverMsg = error?.response?.data?.msg || error?.response?.data?.message
  ElMessage.error(serverMsg || error?.message || fallbackMessage)
}
```

```vue
<script setup>
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'

async function loadAccounts() {
  try {
    accounts.value = await resourcesApi.accounts.getList()
  } catch (e) {
    console.error('Failed to load accounts:', e)
    // ✅ 正确：429 交给全局 interceptor，其他错误才弹窗
    notifyErrorUnlessRateLimit(e, '账户数据加载失败')
  }
}
</script>
```

### 12.4 教训

- **全局 interceptor 已处理的 429，业务层不要再弹 ElMessage.error**
- **新增 API catch 块时优先使用 `notifyErrorUnlessRateLimit`**
- **pre-push gate 已拦截新增的业务层 ElMessage.error 覆盖 429**：`scripts/check-429-error-override.mjs`
- 扫描脚本：`scripts/scan-429-catch.mjs`、`scripts/scan-load-on-mount-429.mjs`

---

## 13. 相关文档

- [[lessons-learned]] §三 §四 §七 — 前端相关踩坑案例
- [[design-system]] — 设计系统基线
- [[engineering-discipline]] — 反复修复的根因、根治与预防
- FRONTEND.md — 前端规范入口
- `src/components/` — 前端组件源码

---

## 14. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取前端陷阱 |
| 2026-07-12 | 新增 §12：业务层 catch 覆盖全局 429 友好提示 |
