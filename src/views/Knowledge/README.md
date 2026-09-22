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
| `CaseDetailDialog.vue` | Component | 案例详情弹窗，被 `CaseGrid.vue` 使用 |
| `caseDetailFormatter.js` | Util | 案例详情排版纯函数（打平表格/列表行切分、句子型伪标题识别、来源标题与文档名兜底提取），被 `CaseDetailDialog.vue` 使用 |
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

- 2026-09-21: 案例详情「应答片段摘要」排版与来源标题修复：
  - `caseDetailFormatter.js` 新增 `splitFlattenedNumberedRows`：PDF 转换层打平的表格/编号列表按递增行号序列切分换行（日期、量词数字、「第」引导的章节引用不构成递增序列，不误切）；≥2 行即认定行结构，再由「每个行号（含末行）后面至少 4 字行内容」的结构判据拦住 `第1章第2节`、`1号楼2单元` 一类误切，**不对整行长度设先验门槛**（短的两行表格同样是真实表格；49 字反例曾因长度门槛被整类漏切）。注意该结构判据兜不住 `第1章…第2章…` 式引用（引用之间天然隔十几个字），故「第」引导的数字在候选阶段就排除
  - 新增 `isSentenceLikeTitle`：与后端 `MarkdownTitleRules.isSentenceLikeTitle` 同规则，拒绝把正文句子当标题展示
  - 新增 `isVerifiedChapterTitle`：只承认「章节号 + 标题文字」形式的**已验证章节**（与后端 `MarkdownTitleRules.isChapterTitle` 同规则，由共享夹具 `backend/src/test/resources/casework/chapter-title-contract-cases.json` 双向锚定）——编号之后必须还有标题文字，`1.`、`第一章`、`（1）`、`①`、`A.` 这类只有编号的串不算；**字母列表标记同样不算章节号**（`A. 附录清单`、`B、服务方案`——字母枚举与正文里的字母列表无从区分）；两侧空白/分隔符字符类逐字一致（显式含全角空格 U+3000 与不换行空格 U+00A0）。`extractSourceLocationText` 只返回已验证来源，且对 `sourceSectionPath` **逐段**校验：整条路径都是已验证章节才展示完整路径，含无编号祖先时退回命中章节自身的标题；无编号小标题（PDF 转换器的加粗排版层）、正文句子、评分项标题、responseText 启发式一律不再冒充「原文所在标题」，此时 UI 显示「暂无来源标题」，评分项标题仍在自己的字段展示
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
