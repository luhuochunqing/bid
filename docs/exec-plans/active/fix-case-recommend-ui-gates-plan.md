# 案例推荐前端门禁修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复案例推荐资质项误判与低分有效结果被前端隐藏的问题。

**Architecture:** 保持后端推荐接口为有效结果唯一来源；前端只负责展示接口结果，并以窄化的纯函数判定是否显示资质证书引导。所有变化限制在现有抽屉组件和组件测试内。

**Tech Stack:** Vue 3 Composition API、Vitest、Vue Test Utils。

## Global Constraints

- 真实 API 唯一源，禁止新增 Mock 业务路径。
- 不修改后端推荐算法、API 契约、数据库或存量数据。
- 必须先观察回归测试按预期失败，再写生产代码。
- 不触碰原持久 worktree 的未提交改动。

---

### Task 1: 建立误判与隐藏结果的 RED 回归

**Files:**
- Modify: `src/views/Project/stages/components/AiRecommendDrawer.spec.js`

**Interfaces:**
- Consumes: `caseRecommendationApi.getScoreItems(projectId)` 和 `recommendCases(projectId, request)`。
- Produces: 对低分结果展示、服务端总数和资质窄判定的组件行为契约。

- [x] **Step 1: 添加低分有效结果测试**

构造 `recommendCases` 返回 `{ total: 165, items: [{matchScore: 32}, {matchScore: 37}] }`，断言两张 `.case-card` 均存在且文案含 `165 个结果`。

- [x] **Step 2: 添加普通认证语境不误判测试**

构造评分项 `{ code: 'TECH_QUAL', dim: '质量管控', detail: '要求提供产品质量管控方案，涉及强制认证产品质量管理' }`，断言 `.qualification-notice` 不存在。

- [x] **Step 3: 运行测试并确认 RED**

Run: `npm run test:unit -- src/views/Project/stages/components/AiRecommendDrawer.spec.js`

Expected: 低分案例被过滤、数量显示 0，以及普通认证语境错误出现提示的断言失败。

### Task 2: 最小实现并验证 GREEN

**Files:**
- Modify: `src/views/Project/stages/components/AiRecommendDrawer.vue`
- Test: `src/views/Project/stages/components/AiRecommendDrawer.spec.js`

**Interfaces:**
- Consumes: 推荐响应 `{ total, items }` 和评分项 `{ id, code, dim, detail }`。
- Produces: `filteredCases` 展示后端有效结果，`recommendTotal` 展示服务端总数，`isQualificationScoreItem` 只识别明确证书要求。

- [x] **Step 1: 删除 50 分客户端门槛**

选择评分项时让 `filteredCases` 直接返回 `cases.value`；未选择评分项时保留现有关键词过滤行为。

- [x] **Step 2: 保存并展示服务端总数**

新增 `recommendTotal` 响应状态；成功加载评分项推荐时赋值为合法的 `res.total`，否则回退当前结果数；默认推荐、清空选择和请求失败时同步重置，标签在选择评分项时显示该值。

- [x] **Step 3: 收窄资质证书判定**

名称命中明确资质/证书关键词时判定；正文仅在同时出现证书对象与提交/持有证据时判定，不再将单独“认证”作为充分条件。

- [x] **Step 4: 运行组件测试确认 GREEN**

Run: `npm run test:unit -- src/views/Project/stages/components/AiRecommendDrawer.spec.js`

Expected: 组件测试全部通过。

- [x] **Step 5: 运行前端构建与差异审查**

Run: `npm run build`

Expected: exit 0；`git diff --check` 无输出；差异仅包含批准范围内文件。
