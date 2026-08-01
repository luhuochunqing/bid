# Knowledge 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
知识模块负责案例、资质、模板等知识资产页面。
该目录主要提供可复用资产的查询、展示、使用和维护入口。
页面内容以真实知识数据和业务操作为主，不承载通用组件实现。

## 边界清单

### 顶层视图（路由入口）

| 文件 | 地位 | 功能 |
|------|------|------|
| `KbLayout.vue` | Layout | 知识模块布局容器（侧边栏 + 路由出口） |
| `views/CaseWrapper.vue` | View | 案例库路由入口，渲染 `CaseGrid.vue` |
| `views/CaseGrid.vue` | View | AI 案例网格列表页（案例库唯一前端入口，操作 `knowledge_case` 表） |
| `Qualification.vue` | View | 资质文件页编排层，复用 `components/qualification/` 子组件 |
| `Template.vue` | View | 模板库页编排层，复用 `components/template/` 子组件 |
| `Performance.vue` | View | 业绩管理页编排层，复用 `components/Performance*.vue` 子组件 |
| `Personnel.vue` | View | 人员管理页编排层，复用 `components/personnel/` 子组件 |
| `BrandAuth.vue` | View | 品牌授权页编排层，复用 `components/BrandAuth*.vue` 子组件 |
| `Warehouse.vue` | View | 仓库管理页 |
| `views/ProjectArchive.vue` | View | 项目档案页 |
| `views/DepositBoard.vue` | View | 存证看板页 |

### 案例库子组件（`views/components/`）

| 文件 | 地位 | 功能 |
|------|------|------|
| `CaseCard.vue` | Component | 案例卡片，被 `CaseGrid.vue` 使用 |
| `CaseDetailDrawer.vue` | Component | 案例详情抽屉，被 `CaseGrid.vue` 使用 |
| `views/caseLabels.js` | Constants | 案例库枚举常量（项目类型/客户类型/评分类别/状态） |

### 其他子目录

- `components/qualification/` — 资质子组件 + composable（详见该目录 README）
- `components/personnel/` — 人员子组件 + composable
- `components/template/` — 模板子组件 + composable
- `components/Performance*.vue` — 业绩管理子组件
- `components/BrandAuth*.vue` — 品牌授权子组件
- `views/components/Archive*.vue` — 项目档案子组件

> 注：`Case.vue`、`CaseDetail.vue` 及 `components/case/` 子目录已于 2026-07-31 清理（V999 双 Tab 时期的遗留孤儿组件，路由实际指向 `views/CaseWrapper.vue`）。

## 最近更新

- 2026-07-31: P0 清理 + P1 架构修复：
  - 清理 V999 双 Tab 时期遗留的 11 个孤儿组件 + 6 个死 API 方法 + buildCasePayload
  - 恢复 `createReferenceRecord`（误删修复，被 `useDocumentKnowledge.js` 调用）
  - 删除残留的 `filterCaseByQuery`/`applyCasePagination`/`buildCaseListResponse`，`getList` 改为服务端分页
  - 修复 `CaseGrid.vue loadRelated` 调错 API（从 `getList` 改为 `getGridList`）
  - 修复 `caseIndustryMap` 双向映射数据失真，改为单向 `caseIndustryDisplayMap`
  - 提取 `isNumericId`/`invalidIdMessage` 到 `resources/shared.js`
  - casesApi 测试从 2 个补全到 12 个（含 `createReferenceRecord` 回归测试）
- 2026-04-19: 资质页拆分为页面编排层 + `components/qualification/` 子组件，并移除页面内硬编码借阅记录。
- 2026-04-19: 案例页拆分为列表/搜索/表单/详情头部组件与 composable，案例列表改为参数驱动查询并移除本地 mock 主路径。
