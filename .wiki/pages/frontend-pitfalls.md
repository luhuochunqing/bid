---
title: 前端 Vue3 / Element Plus 陷阱集
space: engineering
category: guide
tags: [前端, Vue3, Element Plus, reactive, ref, v-model, el-upload, el-form, 权限, E2E, scoped, teleport]
created: 2026-07-10
updated: 2026-09-16
health_checked: 2026-09-18
sources:
  - src/
  - .wiki/pages/lessons-learned.md
backlinks:
  - _index
  - ai-case-recommend-data-source-fix
  - engineering-discipline
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
- **路由守卫从 some 改 every 后，必须同步 RoleProfileCatalog**（CO-580 教训，2026-07-26）
  - 详见下方 §7.4。

### 7.4 路由守卫 every 改造后的权限矩阵同步（CO-580, 2026-07-26）

#### 事故

commit `f21dce017` 把前端路由守卫从 `some` 改为 `every` 后，`/knowledge/*` 子菜单路由的 `permissionKeys=['knowledge','knowledge-qualification']` 要求用户**同时持有** `knowledge` 父权限和 `knowledge-qualification` 子权限才能通过。

但 `RoleProfileCatalog` 中 `/bidAdmin`、`bid-TeamLeader`、`bid-Team`、`bid-SystemAdmin` 虽持有 `qualification.manage` 操作权限，却未配置 `knowledge-qualification` **菜单权限**，导致这些角色登录后被路由守卫拦截重定向到工作台，无法访问资质证书页面。E2E 测试 `regression-bid-ui-optimization`、`task-board-customization`、`qualification-form-11-fields-flow` 等多模块系统性失败。

#### 根因

1. **菜单权限与操作权限解耦**：`qualification.manage` 是操作权限（控制按钮显隐），`knowledge-qualification` 是菜单权限（控制路由守卫通过）。两者独立配置，缺一不可。
2. **路由守卫 every 语义要求所有 permissionKeys 都满足**：`some` 改 `every` 是正确修复（防止越权），但配套的权限矩阵没有同步补全。
3. **RoleProfileCatalog 是角色权限的单一真相来源**：新增菜单路由后，必须同步在 `RoleProfileCatalog` 给相关角色注入对应的子菜单权限常量。

#### 修复

1. `RoleProfileCatalog.java` 新增 7 个 `KNOWLEDGE_*_PERMISSION` 常量（qualification/personnel/archive/case/template/warehouse/performance）。
2. 给 5 个角色（`/bidAdmin`、`bid-TeamLeader`、`bid-Team`、`bid-SystemAdmin`、`bid-administration`）的 `SeedDefinition` 注入对应子菜单权限。
3. V1178/V1179/V1180 迁移脚本给现有角色补全 `menu_permissions` 字段。
4. U1178/U1179/U1180 回滚脚本使用 `REGEXP_REPLACE` 安全移除追加项。

#### 教训

- **路由守卫改造必须同步审计 RoleProfileCatalog**：把 `some` 改 `every` 是防御性增强，但必须列出所有受影响路由的 `permissionKeys`，逐个确认相关角色已持有全部所需权限。
- **新增子菜单路由时，必须同时更新两处**：(1) `RoleProfileCatalog.SeedDefinition.menuPermissions`；(2) Flyway 迁移脚本给 `roles.menu_permissions` 字段追加。
- **菜单权限 ≠ 操作权限**：`knowledge-qualification`（菜单权限，控制路由守卫）和 `qualification.manage`（操作权限，控制按钮显隐）是两个独立维度，必须分别配置。
- **E2E 全量测试是权限矩阵回归的兜底**：本次 E2E 多模块系统性失败，正是因为权限矩阵与路由守卫不同步。E2E 失败时应优先排查权限矩阵是否完整。

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
- 存量审计（spec 035）：`scripts/audit-existing-429-exposure.mjs`
- N+1 反模式拦截（spec 035）：`scripts/check-list-endpoint-n1.mjs`

---

### 12.5 根治 vs 防御（spec 035 — Account 详情 429 反复修 6 次教训）

**核心论点**：429 防御是治标；N+1 根治才是治本。

| 维度 | 防御（治标） | 根治（治本） |
|---|---|---|
| 改了什么 | 加更友好的文案、加更长的冷却期、降并发 | 让 429 不发生（消除 N+1） |
| 真实案例 | spec 034 / !2032/!2035/!2036 三次补丁 | spec 035：list 端点返回完整 DTO |
| 反复修风险 | 高（每次重新调参数） | 低（根因消除后不需要再调） |
| 用户体感 | 提示友好但仍卡顿 | 不卡顿、不报错 |
| 测试方式 | 文本匹配（无效） | 行为测试（DevTools Network 录制） |

**关键证据链**（spec 035 §0）：

```
!1997 (afc11b64e)  DETAIL_CONCURRENCY = 5     ← 局部防御
!2005 (f3f4ca6f4)  DETAIL_CONCURRENCY = 2     ← 局部防御
!2036 (8a32fe8b3)  DETAIL_CONCURRENCY = 1     ← 局部防御（完全串行）
+ !2032 / !2035    症状层防护
```

每次都"提高防御等级"，但 list 端点不返回完整 DTO 这个根因始终没动。

**根治三件事**：

1. **后端契约**：`GET /api/*/list` 返回完整业务 DTO（password 等敏感字段走单独端点）
2. **前端消费**：删除 `loadDetailsInBatches` 等 N+1 加载函数，复用 list 数据
3. **防复发**：`scripts/check-list-endpoint-n1.mjs` 在 pre-push gate 拦截新出现的 N+1

**教训提炼**：

- **遇到 bug 先问"5 个为什么"**（工程纪律 §3.1）— 反复修的 bug 多半根因没找到
- **不要把限流当 bug 修** — 限流是兜底，根因在调用方（架构问题）
- **测试要测根因行为**（工程纪律 §5.2）— 文本匹配断言是无效的（spec 035 §0 根因 4）
- **存量 207 处业务层 ElMessage.error** 也要治理（用 `audit-existing-429-exposure.mjs` 评级）
- **pre-push gate 同时拦截新增 + 审计存量** — `check-429-error-override.mjs --audit-existing`

参考：specs/035-root-account-429/spec.md §FR-A-01 / §FR-B-01 / §FR-C-01~03
参考：.wiki/pages/engineering-discipline.md §6.3 案例库 "Account 详情 429（N+1 list-detail）"

---

## 13. 相关文档

- [[lessons-learned]] §三 §四 §七 — 前端相关踩坑案例
- [[design-system]] — 设计系统基线
- [[engineering-discipline]] — 反复修复的根因、根治与预防
- FRONTEND.md — 前端规范入口
- `src/components/` — 前端组件源码

---

## 14. el-table 跨页勾选丢失 ids（前端分页 + 后端分页设计错配）

### 14.1 事故

业绩合订本导出：用户勾选 3-6 页约 30 条业绩，导出后 Word 只含第 6 页（最后一页）约 10 条台账数据，部分标题下无附件（错觉）。

### 14.2 根因

`src/views/Knowledge/Performance.vue` 的 el-table 配置缺陷 + 纯前端分页组合：

```vue
<!-- 错误：缺 row-key + reserve-selection -->
<el-table :data="pagedRecords" @selection-change="handleSelectionChange">
  <el-table-column type="selection" width="55" />
```

- `useListPagination.js` 用 `slice` 做纯前端分页，每次翻页 `pagedRecords` 整体重建
- el-table 的 selection 跨页保留机制是为后端分页设计（`:data` 单页不变、靠 `row-key` 追踪）
- 缺 `row-key` 时，`:data` 重建会清空 selection，`@selection-change` 触发后 `selectedIds` 只剩当前页

### 14.3 5 Whys

| 层级 | 回答 |
|---|---|
| 现象 | 勾选 30 条，导出只有 10 条 + 部分无附件 |
| 为什么只有 10 条 | 后端 `totalCount=10`，前端 payload.ids 只含 10 个 |
| 为什么前端只传 10 个 | el-table 缺 `row-key` + `reserve-selection`，翻页清空 selection |
| 为什么翻页会清空 | 前端分页下 `pagedRecords` 整体 slice 重建，无 row-key 追踪选中状态 |
| 工程根因 | 前端 slice 分页 + 后端分页设计错配，未按 el-table 跨页保留契约配置 |

### 14.4 正确做法

```vue
<el-table :data="pagedRecords" row-key="id" @selection-change="handleSelectionChange">
  <el-table-column type="selection" width="55" :reserve-selection="true" />
```

参考项目内已正确实现：`src/views/Bidding/customer-opportunity/CustomerOpportunityPool.vue` 的 `row-key="customerId"`。

### 14.5 诊断证据（生产日志）

- 服务器 `jetty@172.16.10.149`，traceId=8fb96e27d11145b4b9970fb690f5c9f4
- `PerformanceBundleExportNotificationPublisher totalCount=10`（应 30）
- 0 条"附件文件不存在" warn（排除 §97 路径漂移）
- 0 条 ImageIO 异常（排除 §103 ARGB 编码）
- PR !2250 后端 ids 模式 `@Size` 防线已就位

### 14.6 规范

- el-table `type="selection"` 列**必须**配 `row-key`
- 纯前端 slice 分页下**额外**必加 `:reserve-selection="true"`
- 关键业务操作后端必须打 `count` 日志，便于排查"前端少传 ids"

### 14.7 "很多标题下无附件"是复合错觉

实际是"应该出现的 20 个标题根本没出现"+"已出现的 10 个标题中部分无附件"的复合现象。用户描述现象时要追问"实际数量 vs 期望数量"，不能只听"很多"。

---

## 15. echarts 全量引入导致 chunk 膨胀（已改为按需注册）

### 15.1 事故

页面切换内容展示慢排查发现：`import * as echarts from 'echarts'` 全量引入使 echarts chunk 达 821.9K（gzip 270K），叠加服务器 Nginx 未开 gzip，页面切换传输成本极高。

### 15.2 根因

echarts 5 支持按需注册（`echarts/core` + `echarts/charts` + `echarts/components`），但业务代码 9 处均为全量 namespace 引入，tree-shaking 完全失效。

### 15.3 正确做法（2026-08-17 已落地）

- 全站唯一 import 点：`src/utils/echarts.js`，统一注册 Bar/Line/Pie/Radar + Title/Tooltip/Grid/Legend/DataZoom/Radar 组件 + CanvasRenderer
- 业务代码一律 `import echarts from '@/utils/echarts'`，**禁止**直接 `import 'echarts'`
- 新增图表类型（如 gauge/scatter）或组件（如 visualMap/Toolbox）时，在 `src/utils/echarts.js` 追加注册；漏注册的症状是图表空白 + console 报 `Unknown component` / `Series type xxx not exists`
- 实测收益：echarts chunk 821.9K→406.9K（gzip 270K→137K），vendor 同步 -40K

### 15.4 教训

- 大型图表库引入时先查按需注册方案，全量 namespace import 是体积杀手
- vite `manualChunks` 只决定拆分位置，不解决包内体积；瘦身必须靠 tree-shaking

---

## 16. API 模块 getList 默认 size 参数不宜过大

### 16.1 事故

`tendersApi.getList()` 默认 `size: 10000`，远大于后端 `MAX_PAGE_SIZE=100`。工作台热门标讯只展示 6 条却拉全量 100 条，造成不必要的接口耗时（2026-08-17 线上 API 耗时采样确认）。

### 16.2 根因

前端 API 模块写 `params.size || 10000` 时无人质疑"10000 是否合理"——等同后端无分页。后端 TenderController 虽有 clamp 保护（2026-08-02 OOM 根因修复），但 wire 协议仍传 10000，且下次后端改 clamp 值或去 clamp 时风险暴露。

### 16.3 正确做法

- API 模块默认 size 对齐后端 `PaginationConstants.MAX_PAGE_SIZE`（当前 100），不要擅自写 10000
- 调用方按需传显式 size：需要 6 条传 `{ size: 6 }`，需要全量候选传 `{ size: 100 }`
- 列表页类（需要状态计数、全量前端筛选）走 store 统一传筛选参数，不依赖默认大 size

### 16.4 教训

- API 模块默认值和后端服务端约束必须一致，否则默认值等价于"不计后果"
- 工作台每个数据源都是独立场景，应该各自声明数据量需求，而不是依赖一个模块全都拉的默认值

---

## 17. 全局 `text-overflow: ellipsis` 把状态列挤成"黑点"

### 17.1 事故

工作台 AI 商机预测表格：为修复长文本列省略号不生效，给所有 `.el-table .cell` 全局加了 `overflow: hidden; text-overflow: ellipsis`，结果项目状态列的 el-tag 后面出现一个"黑点"。

### 17.2 根因

全局 ellipsis 作用到**所有**单元格。状态列宽度固定（105px）装不下标签时，被截断的内容渲染出省略号「…」，在窄列里视觉上只剩一个点，看起来像黑点——不是渲染 bug，是截断规则的误伤。

项目全局 `table.css` 给所有 el-table 体单元格设了 `white-space: normal + overflow: visible`（长内容换行基线），覆盖了 `show-overflow-tooltip` 依赖的截断样式。修复时若反向全局加 ellipsis，就会伤及不需要截断的列。

### 17.3 正确做法

用 `:has()` 把 ellipsis 收窄到明确标记的列，其余列只保留 `nowrap`：

```css
/* 只对需要截断的列（内层 span 带 op-trunc-text 标记）启用 ellipsis */
.op-table-wrap :deep(.el-table__body .cell) {
  white-space: nowrap;
}
.op-table-wrap :deep(.el-table__body td:has(.op-trunc-text) .cell) {
  overflow: hidden;
  text-overflow: ellipsis;
}
```

参考实现：`src/views/Dashboard/components/OpportunityPredictionCard.vue`（项目名称/招标主体两列标记 `op-trunc-text`）。

### 17.4 规范

- 覆盖全局 table.css 截断基线时，**禁止**全局加 `text-overflow: ellipsis`，必须按列收窄
- 状态/标签类窄列只允许 `white-space: nowrap`，不加 ellipsis
- 修复后验证：Playwright 检查目标列 `textOverflow === 'clip'`（非 ellipsis）

---

## 18. Vue provide/inject 子组件模板直接引用注入字段但未解构（undefined.has 渲染崩溃）

### 18.1 事故

数据分析模块筛选区自定义 TreeSelect：展开任一层级时报 `[ErrorBoundary] Unhandled render error: TypeError: Cannot read properties of undefined (reading 'has')`。子组件模板里直接写了 `selectedSet.has(node.value)`，但 `selectedSet` 在 `<script setup>` 里从未声明。

### 18.2 根因

父组件 `TreeSelectDropdown.vue` 用 `provide('treeSelectContext', { selectedSet, isExpanded, toggleExpand, toggle })` 注入上下文。子组件 `TreeSelectNode.vue` 只写了：

```javascript
const tree = inject('treeSelectContext')
```

然后模板直接用 `selectedSet.has(...)` 与 `tree.isExpanded(...)`。`tree` 拿到了，但 `selectedSet` 是注入对象上的**字段**，未解构到当前作用域 → 模板编译成访问未定义变量 → `.has` 抛错。

> minified 后局部变量被压缩改名，线上 bundle 里 grep 不到 `selectedSet` 字符串，无法靠搜字符串验证是否修复——用 bundle md5 与本地构建产物对比更可靠。

### 18.3 正确做法

从注入上下文解构到当前作用域：

```javascript
const tree = inject('treeSelectContext')
if (!tree) throw new Error('TreeSelectNode 必须在 TreeSelectDropdown 内使用')
const { selectedSet } = tree  // 解构注入字段，模板才可用
```

### 18.4 规范

- 模板中引用的每个注入字段，都必须先从 `inject()` 结果里解构，或统一走 `tree.xxx` 访问，不能混用
- 新增 provide/inject 组件时，子组件先本地 build 一次，确认模板引用的注入字段全部在作用域内
- 验证线上修复用 bundle md5 对比（grep minified 字符串不可靠）

---

## 19. scoped `:deep()` 对 teleport 弹层组件静默失效（el-drawer/el-dialog）

### 19.1 事故

项目中心-标书制作阶段，AI 评分标准解析抽屉点「重新解析」后，加载提示「AI 正在对标已上传标书进行实际打分…」出现在内容中点（约 1600px 深处）而非当前浏览位置，用户看不到提示。

### 19.2 根因（真实浏览器实测取证）

组件内写 `<style scoped>` + `.score-parse-drawer :deep(.el-drawer__body) { display: flex; ... }`，但 el-drawer 默认把弹层 **teleport 到 body**，teleport 后的根元素**不携带 scoped 的 data-v 属性** → `:deep` 选择器无法命中 → 规则静默失效（无任何报错）：

- `.el-drawer__body` computed display 为 `block`（期望 `flex`）
- 内容区 `.drawer-content-wrapper`（`flex: 1` 在 block 父级下无效）被撑到全部内容高度 2992px
- 打分遮罩 `position: absolute; inset: 0` 覆盖全部滚动内容，flex 居中的文字落在内容中点而非可视区（视口仅 720px）

### 19.3 正确做法（2026-08-26 已落地）

这类规则必须放到**非 scoped** 的 style 块，用组件自定义类前缀限定作用域：

```vue
<!-- ❌ 失效：teleport 后根元素无 data-v -->
<style scoped>
.my-drawer :deep(.el-drawer__body) { padding: 0; display: flex; flex-direction: column; }
</style>

<!-- ✅ 生效：类前缀全局规则 -->
<style>
.my-drawer .el-drawer__body { padding: 0; display: flex; flex-direction: column; }
</style>
```

配套防御：遮罩展示期间给内容区加 `.scroll-locked { overflow-y: hidden }`，避免轮询中用户滚动导致提示漂出可视区（遮罩在滚动容器内是内容坐标系定位，会随内容滚走）。项目内先例：`ScoreItemDetailModal.vue` 同样用非 scoped 块。

### 19.4 规范

- 定制 teleport 弹层（el-drawer/el-dialog）内部结构时，**禁止**在 scoped 块用 `:deep`，必须用类前缀全局规则（先查组件版本是否有原生 props，如 el-dialog 的 `body-style`；el-drawer 2.14 无）
- 此类样式失效**无任何报错**，必须用浏览器实测 computed style 验证，不能只看代码
- 回归防护：`e2e/score-parse-drawer-layout.spec.js` 固化三条布局契约（body flex 布局 / 遮罩文字在可视区 / 遮罩期滚动锁定）

---

---

## 20. Vue 3 `<script setup>` 内联 `h()` 函数式组件导致 Scoped CSS 与作用域样式静默失效

### 20.1 现象

在 `<script setup>` 中定义内联函数式组件（如 `const DateCell = (props, { slots }) => h('span', { class: 'op-date' }, ...)`），并在模板中调用 `<DateCell :value="row.date" />`。
在 `<style scoped>` 中编写 `.op-date { ... }` 样式时，**样式完全不生效**；
尝试使用 `.el-table :deep(.op-date)` 或追加无作用域的类名修补，导致样式维护混乱、产生大量 `:deep()` 穿透补丁。

### 20.2 根因

1. Vue 3 SFC 的 Scoped CSS 机制是通过编译期在模板中的元素上注入 `data-v-xxxx` 属性选择器实现的。
2. 在 `<script setup>` 中直接使用 `h()` 动态构造的 VNode **不会自动继承父 SFC 的 `data-v-xxxx` 作用域属性**。
3. 因此 `<style scoped>` 编译出的 `.op-date[data-v-xxxx]` 规则永远无法命中 `h()` 创建的 DOM 节点。
4. 这种在 `<script setup>` 中用 `h()` 封装小组件的做法既破坏了 SFC 的模板表达力，又引入了样式作用域穿透的隐患。

### 20.3 规范对策

- **禁止在 `<script setup>` 中使用 `h()` 构造微型函数式组件替代简单模板**；
- 简单结构直接使用原生模板插槽表达：
  ```vue
  <template>
    <el-table-column label="开标时间" width="115" align="center">
      <template #default="{ row }">
        <span class="op-date">{{ formatDate(row.bidOpenTime) }}</span>
      </template>
    </el-table-column>
  </template>
  ```
- 数据格式化抽离为无副作用的纯 JS 辅助函数（如 `formatDate(value)`）。
- 复杂可复用 UI 应抽离为独立 `.vue` 单文件组件，保留标准 Vue 编译器属性注入与样式作用域。

---

## 21. 选项模板复制导致非标准伪枚举渗透（商机预测与项目列表客户类型筛选区）

### 21.1 现象

工作台-商机预测与投标项目列表页面筛选区的「客户类型」下拉框中，多出了业务上不存在的「其他」选项，且该选项反向诱导后端维护了无业务含义的 `OTHER` 枚举。

### 21.2 根因

1. 开发者在新建 `useProjectSearch.js` 时，定义 `projectTypeOptions`（项目类型合法包含“其他”）后直接复制模板来定义 `customerTypeOptions`，顺手将 `{ value: 'OTHER', label: '其他' }` 带入；
2. 系统蓝图与各业务中心标准客户类型仅有 5 类（政府机关/事业单位/高校、央企、地方国企、民企、港澳台及外企）；
3. 缺乏针对筛选项列表自身内容与长度的断言测试，仅测试了筛选过滤逻辑，导致非标准枚举长期存留。

### 21.3 规范对策

- **严禁凭模板惯性盲目添加“其他”选项**：筛选项枚举必须 100% 对齐蓝图与领域模型。
- **添加筛选项集合锁定测试**：核心业务选项列表需有单元测试断言其精确元素集合与长度（如 `useProjectSearch.spec.js`）。
- **展示层容错隔离**：格式化函数（如 `customerTypeLabel`）保留对历史脏数据的映射兜底，但搜索与表单录入层必须严格收敛。

---

---

## 22. 全局注册 Element Plus 插件与样式导致 Vitest / JSDOM 测试崩溃与假死

### 22.1 现象

为了消除 Vitest 运行中输出的 `[Vue warn]: Failed to resolve component: el-*` 和指令警告，在 `src/test-setup.js` 中全局注册 `ElementPlus` 插件并引入全量样式（`import 'element-plus/dist/index.css'`），导致：
1. `shallowMount` 的组件因 VTU 默认 `renderStubDefaultSlot: false`，Element Plus 内部插槽内容消失（如 `ElUpload` 内文案断言失败）；
2. `ElDrawer` 等包含复杂 DOM 事件绑定的组件在 JSDOM 中触发 `el.addEventListener is not a function` 崩溃；
3. `ElMessageBox`、Loading、动画定时器在 JSDOM 中造成异步 handle 残留，Vitest 进程卡死不退出；
4. 全量 370+ 测试套件执行时间由几分钟膨胀至数十分钟。

### 22.2 根因

1. **违背 test-setup 纯净性定位**：`src/test-setup.js` 开头明确声明「仅补 jsdom 缺失的浏览器 API，禁止放业务 mock / 全局装业务库插件」；
2. **JSDOM 环境与 Element Plus 深度绑定 DOM 的矛盾**：真实 Element Plus 组件依赖真实浏览器布局、样式计算与完整 DOM API，在单测 jsdom 中全量挂载极易产生副作用与未清理 timer。

### 22.3 规范对策

- **严禁在 test-setup.js 中全局加载 ElementPlus 插件与 CSS**：单元测试应保持轻量纯净。
- **通过 console.warn 过滤警告噪音**：在 `src/test-setup.js` 中重写 `console.warn`，精准过滤 `Failed to resolve component: el-` 与 `Failed to resolve directive:`，既保持测试输出清洁，又绝不改变组件运行时生命周期。
- **单个 spec 按需 stub/mount**：确需测试 Element Plus 交互的 spec，在当前测试文件局部引入或按需 stub。

---

## 23. 文件删除接口 404 导致前端状态死锁与错误提示覆盖全局 429

### 23.1 现象

立项阶段招标文件或其他关联文件在后端已被物理删除或悬挂失效时，前端点击删除按钮调用后端 API 返回 404 Not Found：
1. 错误捕获未识别 404，抛出异常阻断了前端本地响应式状态（如 `form.tenderDocumentId = null`、`bidDocFiles = []`）的清理；
2. 用户无法通过再次点击删除清空该文件引用，也无法通过保存表单解除死锁，导致立项流程卡死；
3. catch 块直接使用 `ElMessage.error(error?.message || '删除招标文件失败')`，绕过了全局 429 限流友好提示（`notifyErrorUnlessRateLimit`）。

### 23.2 根因

1. **资源删除幂等性与悬挂引用失步**：当远程资源已不存在（404）时，业务实质上已经达到“资源不存在”的期望目标状态，但调用方将其视为不可恢复的硬错误阻断状态同步；
2. **缺乏 429 提示守卫**：直接硬编码 `ElMessage.error` 违反系统限流防护规范，覆盖了全局防抖与 429 降级处理。

### 23.3 规范对策

- **删除接口 404 容错降级**：捕获 404 状态码时打印 warn 日志并视为删除成功，清空本地状态与引用，解除死锁。
- **统一使用 `notifyErrorUnlessRateLimit`**：业务 catch 中的错误提示必须走统一工具函数，防范 429 穿透。

---

## 24. 静默 catch 块空语句触发 no-empty 与 E2E 临时日志污染

### 24.1 事故

在业务表单渲染或异步辅助数据加载（如附件预加载、日志加载、确认弹窗取消）中，常见类似 `try { ... } catch {}` 的裸空代码块；以及在 E2E 自动化测试排查用例时遗留 `console.log('[DEBUG ...]')`。

```javascript
// 陷阱 1：无注释裸空 catch 触发 no-empty 告警
try {
  const { data } = await http.get(`/api/attachments`)
} catch {}

// 陷阱 2：测试文件遗留未清理的 console.log 触发 no-console 告警
console.log('[DEBUG edit-self] status:', createRes.status)
```

### 24.2 根因

1. ESLint 的 `no-empty` 规则禁止除包含注释外的空语句块。开发者常认为静默降级不需要写任何处理，但缺失注释会导致后续维护者无法区分是“有意静默容错”还是“异常处理遗漏代码”。
2. E2E 测试调试过程中打入的日志未在 PR 提交前彻底自检清除，长期堆积污染门禁 baseline。

### 24.3 根治规则

- **补全意图注释**：对于合理静默容错的 catch 块，必须在 block 内部显式补充业务意图注释（如 `// 附件加载失败静默降级，不阻塞表单渲染`），既满足 ESLint 规则又清晰传递工程意图。
- **E2E 用例零日志交付**：调试完成后通过 `npm run lint` 统一检查，彻底移除临时打印。
- **必要运行日志显式声明**：如 Sentry 或监控 SDK 初始化等确实需要控制台输出的底座工具，使用 `// eslint-disable-next-line no-console` 显式豁免。

---

## 25. 全局 accessibility.css 的 button min-height: 44px 导致组件内 small 按钮底部溢出错位

### 25.1 事故现象

在工作台卡片或表单筛选区中，使用 `<el-button size="small">` 与 `<el-date-picker size="small">`、`<el-input size="small">` 并排布局时，按钮的高度膨胀为 44px，导致按钮底边超出同排其他控件（24px）达 20px 之多，破坏了网格对齐和视觉底边基线。

### 25.2 根因

项目全局 `src/styles/accessibility.css` 中配置了针对可访问性无障碍点击区域的全局增强规则：
```css
a,
button,
[role="button"] {
  min-height: 44px;
  min-width: 44px;
}
```
该规则匹配到了所有的 `<button>` 原生标签。由于 `<el-button size="small">` 渲染为 `<button class="el-button el-button--small">`，若未显式在局部设置 `min-height` 进行覆盖，就会继承全局的 `min-height: 44px;`，从而导致高度被硬性撑大到 44px，引发底部严重溢出。

### 25.3 根治规则

- **紧凑筛选栏显式覆盖 min-height**：在表单筛选行、工具栏等使用 `size="small"` 的紧凑操作场景中，必须在局部样式中显式重置并强约束 `min-height`、`height` 与 `box-sizing`：
  ```css
  .compact-btn {
    height: 24px !important;
    min-height: 24px !important;
    max-height: 24px !important;
    box-sizing: border-box !important;
  }
  ```
- **布局容器 align-items 对齐**：与输入框/选择器并排的按钮容器，统一配置 `align-items: center;`，确保在同一基准线上绝对水平对齐。

---

## 26. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取前端陷阱 |
| 2026-07-12 | 新增 §12：业务层 catch 覆盖全局 429 友好提示 |
| 2026-08-11 | 新增 §14：el-table 跨页勾选丢失 ids（业绩合订本导出 bug） |
| 2026-08-17 | 新增 §15：echarts 全量引入 chunk 膨胀，改按需注册 |
| 2026-08-17 | 新增 §16：API getList 默认 size 10000 过剩，对齐后端 MAX_PAGE_SIZE=100 |
| 2026-08-18 | 新增 §17：全局 ellipsis 把状态列挤成"黑点"（工作台商机预测表格） |
| 2026-08-19 | 新增 §18：provide/inject 子组件未解构注入字段 → undefined.has 渲染崩溃（数据分析 TreeSelect） |
| 2026-08-26 | 新增 §19：scoped :deep 对 teleport 弹层静默失效（评分解析抽屉重新解析遮罩漂出可视区） |
| 2026-08-28 | 新增 §20：`<script setup>` 内联 `h()` 函数式组件导致 Scoped CSS 静默失效与过度抽象（工作台商机预测时间单元格） |
| 2026-08-28 | 新增 §21：选项模板复制导致非标准伪枚举渗透（商机预测与项目列表客户类型筛选区） |
| 2026-09-05 | 新增 §22：全局注册 Element Plus 插件与样式导致 Vitest / JSDOM 测试崩溃与假死 |
| 2026-09-09 | 新增 §23：文件删除接口 404 导致前端状态死锁与错误提示覆盖全局 429 |
| 2026-09-13 | 新增 §24：静默 catch 块空语句触发 no-empty 与 E2E 临时日志污染（ESLint 基线警告治理） |
| 2026-09-16 | 新增 §25：全局 accessibility.css 的 button min-height: 44px 导致组件内 small 按钮底部溢出错位 |





