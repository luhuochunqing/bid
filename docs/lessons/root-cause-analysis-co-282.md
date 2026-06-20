# CO-282 客户信息 14 行残留与游客兜底 根因分析

> Issue: CO-282
> 日期: 2026-06-20
> 排查者: zcode
> 修复 PR: `!895` (commit `c54c25872`, merge `0870bd085`)

---

## 现场还原

**症状素描**：接口创建的标讯 `http://172.16.38.78:8080/bidding/324` 中，评估表「客户信息矩阵」仍显示第一列预设角色和 14 行表格；同一页面顶部当前用户显示为「游客」。用户明确指出：客户信息 14 行不是浏览器缓存，而是前端固定矩阵合并策略。

**边界划定**：
- 部署后直接调用 `GET /api/tenders/324/evaluation`，后端返回 `customer_rows=0` ✅
- 部署后的前端产物中 `游客|guest` 字面量检查为 `none` ✅
- 登录与 `/api/auth/me` 返回 `系统管理员` ✅
- 问题只在旧前端展示策略与旧 bundle/cache 风险下出现 ❌

---

## 历史背景：为什么这个 Bug 修了很多轮

客户信息矩阵最初来自「固定 15 列 × 14 行」的产品形态。后来 CRM/外部接口接入后，客户信息变成 EAV/flat 双格式、固定角色与外部角色并存的回显数据。历史修复链条里，每个 PR 都解决了一层真实问题，但直到最后一轮才去掉展示层的固定 14 行生成。

| PR/提交 | 修复内容 | 解决层级 | 未覆盖 |
|---|---|---|---|
| `4093b78bc` | CRM 关联后 EAV → flat、字段补齐、保存 NAME | 前端转换 | 外部 API 保存、固定 14 行 |
| `e9fa7c31c` | 缺 `roleKey` 时生成 `EXTERNAL_ROLE_N` | 后端保存 | 字段方言、展示策略 |
| `07a5f7931` | CRM 创建标讯时补齐 evaluation 客户信息 | 创建入口 | 二次更新、字段 key |
| `f218eca27` | 前端保存路径二次更新避免唯一约束冲突 | JPA flush 顺序 | 展示策略 |
| `348d66b74` | `CONTACT/EVALUATION_BASIS` 标准化为前端 key | 字段契约 | EAV 格式、固定 14 行 |
| `d581c21ae` / `be20d1988` | 外部接口兼容 EAV 格式 | payload 形态 | 前端固定行 |
| `18e65cb95` / `7f3708277` | 外部角色行在前端可见 | 前端展示兼容 | 固定行仍作为基底 |
| `e5352a67e` | CO-282 字段值/布尔枚举对齐 | 值转换 | 仍补固定 14 行 |
| `d90e9779a` | 过滤空固定行 | 症状缓解 | 仍先生成固定 14 行 |
| `c54c25872` | 不再生成固定 14 行；移除游客兜底；index no-store | 根治展示与缓存 | 已验证 |

---

## 剥洋葱：两个症状其实是两条链路

### 链路 A — 客户信息 14 行残留

修复前 `CustomerInfoMatrix.vue` 的核心逻辑仍以 `CUSTOMER_INFO_ROWS` 为展示基底：

```js
const fixedRows = CUSTOMER_INFO_ROWS.map((r) => {
  const existing = map.get(r.roleKey)
  return existing ? { ...makeEmptyRow(r), ...existing, roleKey: r.roleKey, roleLabel: r.roleLabel } : makeEmptyRow(r)
})
return [...fixedRows, ...extraRows].filter(hasCustomerInfoValue)
```

这意味着即使接口返回空数组，前端也会先生成固定 14 行，然后再过滤。`d90e9779a` 的「过滤空模板行」只是缓解症状，不是根治：只要模板行存在默认值、转换层残留值或被认为有值，14 行仍可能显示。

最终修复：`src/views/Bidding/detail/components/CustomerInfoMatrix.vue:55`

```js
function mergeData(incoming) {
  if (!Array.isArray(incoming)) return []

  return incoming
    .filter(item => item?.roleKey)
    .map(item => {
      const roleLabel = getCustomerInfoRoleLabel(item.roleKey, item.roleLabel)
      return { ...makeEmptyRow({ roleKey: item.roleKey, roleLabel }), ...item, roleKey: item.roleKey, roleLabel }
    })
    .filter(hasCustomerInfoValue)
}
```

关键变化：
- 不再 import `CUSTOMER_INFO_ROWS`
- 不再生成 fixed 14 rows
- 只展示真实传入 `modelValue`
- 空数据即空表，不再补模板

### 链路 B — 当前用户显示「游客」

「游客」不是后端真实用户，而是前端展示层的 fallback 和旧 bundle/cache 风险。修复前 `Header.vue` 有重复兜底：

```js
guest: '游客'
userStore.currentUser?.name || '游客'
roleName || roleTextMap[userRole] || '游客'
```

最终修复：`src/components/layout/Header.vue:185`

```js
const userName = computed(() => {
  if (userStore.isRestoringSession) return '加载中'
  return userStore.userName || '用户'
})

const userRoleText = computed(() => {
  if (userStore.isRestoringSession) return '加载中'
  if (!userStore.currentUser) return '用户'
  return userStore.currentUser?.roleName || roleTextMap[userStore.userRole] || '用户'
})
```

同时 `src/api/session.js:22` 增加旧 `user` hint 结构校验，无身份字段的 storage hint 会被清理，避免旧缓存污染首屏。

---

## 零号病人定位

### 1. 客户信息 14 行

**第一行错误**：展示组件把模板角色当作数据源。

```
src/views/Bidding/detail/components/CustomerInfoMatrix.vue
mergeData() -> CUSTOMER_INFO_ROWS.map(...)
```

**必然性解释**：

```
接口返回 customerInfo=[]
  ↓
前端 mergeData 仍遍历 CUSTOMER_INFO_ROWS
  ↓
生成 14 个固定角色行
  ↓
过滤逻辑只能过滤“看起来完全空”的行
  ↓
一旦存在默认值/残留值/转换值，14 行仍显示
```

### 2. 游客

**第一行错误**：身份 UI 使用业务角色文案作为 fallback。

```
src/components/layout/Header.vue
userName / userRoleText -> || '游客'
```

**必然性解释**：

```
页面刚加载 / session 恢复中 / user hint 异常
  ↓
Header 没有 currentUser
  ↓
fallback 显示 “游客”
  ↓
用户以为系统存在游客用户或登录状态错误
```

---

## 验证与修复

### 修复 diff 摘要

1. `CustomerInfoMatrix.vue`：只根据真实 `modelValue` 渲染客户信息行。
2. `CustomerInfoMatrix.spec.js`：增加空输入不显示预设行、一个/两个 fixed role 不补剩余行、false 布尔值仍算真实值、外部 role 正常显示等测试。
3. `Header.vue`：删除所有 `guest/游客` fallback，恢复中显示 `加载中`，无用户显示通用 `用户`。
4. `session.js`：旧 storage user hint 必须包含身份字段，否则清理。
5. `docker/nginx.conf` / `scripts/docker-up.sh`：`index.html` 与 SPA fallback 使用 `no-cache, no-store, must-revalidate`，避免旧入口继续加载旧 bundle。

### 本地验证

```bash
pnpm vitest run src/components/layout/Header.spec.js src/views/Bidding/detail/components/CustomerInfoMatrix.spec.js
# 2 files, 10 tests passed

rg "游客|guest" src
# No matches found
```

### 部署后验证（172.16.38.78）

```text
backend_status=active
index_status=200
HTTP/1.1 200 OK
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Expires: 0
asset_check=none
login=True:200:系统管理员
me=True:200:系统管理员
evaluation_324=True:200:customer_rows=0
customer_labels=
```

这组验证同时证明：
- 后端没有给 tender 324 返回客户信息行；
- 前端产物不含 `游客|guest`；
- `index.html` 不会长期缓存旧入口；
- 当前用户来自真实登录态，不是游客。

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 14 行零号病人已定位 | `CustomerInfoMatrix.mergeData()` 生成 `CUSTOMER_INFO_ROWS` | ✅ |
| 游客零号病人已定位 | `Header.vue` 中 `guest/游客` fallback | ✅ |
| 必然性已证明 | 后端 324 返回 0 行，但旧前端仍可生成 14 行 | ✅ |
| 修复 diff 已提供 | `c54c25872` / PR `!895` | ✅ |
| 防复发测试已设计 | Header + CustomerInfoMatrix 单测 | ✅ |
| 服务器验证已完成 | `customer_rows=0`、`asset_check=none`、no-store headers | ✅ |

**Verdict**: ✅ **PASS**

---

## 为什么之前没有提前发现

1. **旧产品形态掩盖根因**：固定 14 行曾是需求，不是天然错误；外部接口回显场景出现后，它才变成错误展示。
2. **多轮修复都在数据链路**：保存、字段映射、EAV/flat、二次更新都是真问题，但不是最终 14 行残留的展示层根因。
3. **“生成后过滤”不是根治**：`d90e9779a` 已经过滤空行，但没有删除模板行生成。只要有默认值或残留值，模板仍会出现。
4. **两个症状混在一起**：客户信息 14 行和游客显示同时出现，容易把全部问题归因给浏览器缓存；实际一个是展示策略，一个是身份 fallback + 旧 bundle 风险。
5. **缺少端到端四层断言**：如果早期同时验证 API 返回行数、前端 dist 字面量、index cache header 和真实页面，就能更早切开数据问题与展示问题。

---

## 防复发规范

1. **模板数据不能无条件进入回显视图**：人工编辑模板和外部接口真实回显必须区分模式。回显场景只能展示真实数据。
2. **不要用业务身份作为 fallback**：身份恢复中显示 `加载中`；无用户显示通用 `用户` 或跳登录页，不能伪造「游客」这类业务角色。
3. **修 UI 缓存类问题要验证 dist 字面量**：不仅要改源码，还要确认部署产物中不含旧字面量。
4. **SPA 发布必须验证入口缓存头**：hashed assets 可以 immutable，但 `index.html` 必须 no-store/no-cache，否则用户可能继续加载旧 bundle。
5. **客户信息链路测试要覆盖“空数据不补模板”**：这是防止固定矩阵策略回归的关键断言。

---

## 相关文档与代码

- `src/views/Bidding/detail/components/CustomerInfoMatrix.vue` — 最终不再生成固定 14 行
- `src/views/Bidding/detail/components/CustomerInfoMatrix.spec.js` — 防回归测试
- `src/components/layout/Header.vue` — 删除游客兜底
- `src/components/layout/Header.spec.js` — Header 身份展示测试
- `src/api/session.js` — storage user hint 结构校验
- `docker/nginx.conf` — index no-store 策略
- `docs/lessons/root-cause-analysis-co-266-co-267.md` — 字段名不一致导致客户信息不显示的前序根因
