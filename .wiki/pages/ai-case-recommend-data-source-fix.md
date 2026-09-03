---
title: AI 智能案例推荐数据源修复（score_item 新契约切换 + casework 裸响应陷阱）
space: engineering
category: guide
tags: [案例推荐, score_item, spec041, 裸响应, 二次解包, caseRecommendationApi]
sources:
  - src/api/modules/caseRecommendation.js
  - src/views/Project/stages/components/AiRecommendDrawer.vue
  - backend/src/main/java/com/xiyu/bid/casework/controller/ScoreItemCaseRecommendController.java
backlinks:
  - _index
  - score-parse-service
  - frontend-pitfalls
created: 2026-08-21
updated: 2026-08-21
health_checked: 2026-08-29
---

# AI 智能案例推荐数据源修复（score_item 新契约 + 裸响应陷阱）

> **一句话**：项目详情页「AI 智能案例推荐」抽屉不显示评分项，根因是数据源错配（读旧表）
> + API 层二次解包（裸响应再取 `.data`）。两者都已修复，本页记录契约事实防止回归。

## 根因 1：数据源错配

spec-041 的 AI 评分标准解析链路写入 **`score_item` 新表**，但项目详情抽屉原来读
**`project_score_drafts` 旧表**（spec-041 后不再写入，恒为空）。

修复：`AiRecommendDrawer.vue` 切到 `caseRecommendationApi`（PRD 4.2 契约），后端零改动。

契约字段差异（旧 → 新 ScoreItemDTO）：

| 旧（draft） | 新（score_item） |
| --- | --- |
| `scoreItemTitle` / `category` | `id` / `dim` / `detail` / `code` |
| 推荐响应为裸数组 | `{ total, items }` 分页对象 |
| `caseId` | `id` |
| `score` / `scoreLabel` | `matchScore` / `matchLabel` |
| `matchReason` 字符串 | `matchReasons` 数组 |

组件内 `normalizeRecommendCase` 对新旧字段名都做了 fallback，容忍后端演进。

## 根因 2：casework 后端全部是裸响应（重要陷阱）

`client.js` 响应拦截器已统一返回 `response.data`（blob 除外），**但 casework 模块的
Controller 直接返回 `ResponseEntity`，不走 `{success, data, message}` 统一信封**。
API 模块层若再 `.then(res => res.data)` 会得到 `undefined`（双重解包）。

受影响的 4 个端点（全部裸响应）：

| 端点 | Controller | 返回 |
| --- | --- | --- |
| `GET /api/projects/{id}/score-items` | ScoreItemCaseRecommendController | 裸 `List<ScoreItemDTO>` |
| `POST /api/projects/{id}/cases/recommend` | ScoreItemCaseRecommendController | 裸 `{total, items}` |
| `GET /api/cases/{id}` | KnowledgeCaseController | 裸 `KnowledgeCase` |
| `POST /api/cases/{id}/reuse` | KnowledgeCaseController | 裸 `Map` |

**规则**：`caseRecommendationApi` 的 4 个方法一律裸透传，禁止再加 `.data` 解包。
契约回归锁在 `src/api/modules/caseRecommendation.spec.js`。

## PRD 4.2 复用定位弹窗默认选中（Warning #1 补充）

PRD 1.1/4.2：复用定位弹窗章节树**默认选中当前评分项对应章节（可修改）**。实现链路：

1. 抽屉 `currentDefaultChapter`：优先 `item.targetChapter`（后端章节关联落地后直接生效）
   → 无则按 `dim` 类别关键词回退到内置章节树（商务/报价/资格→第1章，技术/方案/服务→第2章）→ 空（用户手选）
2. `AICaseReuseSectionModal` 打开时 `applyDefaultSelection`：用 `findSectionPathByChapter`
   （`sectionTreeMatch.js`，BFS：完整路径相等 → label 相等 → 最长包含匹配）在章节树里找路径，
   命中则级联预选 `selectedSection`，未命中回退手输框预填
3. 每次打开重置选择，避免上次残留；弹窗已打开时切换评分项会同步刷新默认选中

限制：真实标书章节树 API 属 spec 046 Phase 3，当前弹窗用内置演示树；
后端 `score_item` 无 targetChapter 字段，映射落地前靠 dim 回退。

## PRD 4.2 案例匹配度计算规则与前端浮层可视化（Popover / Tooltip）

### 1. 匹配度计算机制（满分 100 分，对齐 ScoreItemCaseMatchPolicy）
项目详情页编制抽屉推荐接口（`POST /api/projects/{projectId}/cases/recommend`）调用核心策略 `ScoreItemCaseMatchPolicy.java`，综合 8 维度加权评估（封顶 100 分）：
1. **专属客户/项目实体加权（最高 25 分）**：自动提取项目名称与客户主体专有名词（如“三峡”），候选案例命中专属主体直接获断层加权（单实体 15 分，复合 25 分）。
2. **评分条款深度契合（最高 30 分）**：基于滑动 n-gram 分词，过滤招投标领域通用停用词后评估实质特征词重叠度。
3. **评分项标题/正文跨层相似度（最高 20 分）**：支持纯净名称与案例标题、需求原文及应答正文跨层穿透比对。
4. **评分项类别一致性（+15 分）**：技术/商务/服务/资格等大类与业务关键词语义推断一致加 15 分。
5. **项目类型一致性（+10 分）**：工业品/办公/综合等一致加 10 分。
6. **客户类型一致性（+5 分）**：央企/地方国企/政府/民企等一致加 5 分。
7. **搜索关键词命中（+5 分）**：输入额外关键词在候选案例中命中加 5 分。
8. **历史中标加成（+5 分）**：中标案例额外加 5 分。

### 2. 等级划分与标签
- $\ge 80\%$：**优质**（Tag: `danger`）
- $\ge 60\%$：**良好**（Tag: `warning`）
- $< 60\%$：**一般**（Tag: `info`）

### 3. 前端组件与数据透传链路
- **全局规则说明 Tooltip**：`AiRecommendDrawer.vue` 标题栏 `(?) 评分依据说明`，悬浮展开 8 维度规则明细。
- **单卡片命中分析 Popover**：`CaseCard.vue` 左上角匹配度徽标（`el-popover`），悬浮展开综合评级、命中维度列表（`✓ 评分项条款高度契合` 等）与高亮关键词药丸（`kw-pill`）。
- **详情弹窗匹配度 Banner**：`CaseDetailDialog.vue` 弹窗顶部浅蓝色背景条，展示匹配得分与依据明细。
- **纯函数解析层**：`caseLabels.js` 导出 `getMatchLabel`、`getMatchTagType`、`parseStringOrArray`，统一管理阈值与数组/逗号/顿号容错解析。

## 消费方地图（改这个 API 前必读）

- `AiRecommendDrawer.vue`（项目详情抽屉）— 4 个方法全用
- `AICaseReuseSectionModal.vue`（Bidding 侧复用弹窗）— 用 `reuseCase`，返回值经
  `reuse-success` 事件透传，消费方不读 `result` 字段
- `AICaseRecommendDrawer.vue`（Bidding 版抽屉）— 自带双形态兼容写法，
  **当前未被任何页面挂载**（仅 spec 引用），改动不会在生产暴露

## 闲置端点候选池恒空修正（2026-09-01）

- `KnowledgeCaseRecommendAppService`（`GET /api/cases/recommend`、`GET /api/cases/recommend/project`）
  前端无调用方，但存在跨枚举错配：把 `projects.customer_type`（民企/央企…）填进
  `criteria.projectType`，再用于过滤 `knowledge_case.project_type`（综合/工业品/办公…），
  两值域无交集导致候选池恒为空
- 修复：`criteria.projectType` 一律置空（projects 表无项目类型真值字段），
  `fetchCandidates` 移除跨枚举谓词，仅按案例自身字段过滤（与主链路 `findAllActive()` 对齐）
- 回归测试：`KnowledgeCaseRecommendAppServiceTest.recommendForProject_ShouldNotFilterCandidatesByMismatchedProjectType`
- 主链路 `POST /api/projects/{id}/cases/recommend` 不受此缺陷影响，历史切片案例实测可命中（126/160）
- 详见 docs/lessons/lessons-learned.md #195

## 评分项编号前缀剥离与正文穿透匹配升级（2026-09-01）

### 1. 根因
招标文件解析出来的评分项标题通常带有层级编号（如 `2.2.4(3) 商城系统对接能力`、`第一章 (1) 财务审计报告`），
而案例库中的候选案例标题多为通用分类（如 `技术方案`、`商务要求`），其具体的业务应答正文（`responseText`）和要求（`requirementRaw`）
才包含"商城系统对接能力"等具体关键词。由于未剥离编号且仅比对标题 Jaccard 相似度，导致真实相关案例得分低于推荐阈值被过滤。

### 2. 核心改进策略（FP-Java 纯函数）
1. **多轮前缀清洗器（`TextSimilarityPolicy.cleanTitle`）**：
   支持多层级阿拉伯数字（`2.2.4(3)`）、中文章节（`第三章 3.2 (1)`）、带圈数字（`①②③`）、方括号、罗马数字等 11 类前缀递归剥离，提取纯净业务名称。
2. **正文跨层穿透匹配**：
   评分项纯净业务词不仅比对候选案例标题，同时穿透候选案例的 `requirementRaw` 和 `responseText`，命中即赋予满额 20 分标题/正文相似度。
3. **5 大招投标板块推断**：
   构建涵盖技术/服务/商务/资质/人员的领域推断词典，当评分项或案例类别缺失或泛化时，智能推断归属大类赋予 15 分类别分。
4. **条款重叠度（Detail Overlap）弹性计算**：
   引入长文本词频击中底线保护，防止长条款分母过大稀释相似度得分。

## 关联

- [[score-parse-service]] — spec-041 解析链路（score_item 数据的生产方）
- [[frontend-pitfalls]] — 前端陷阱集
- 提交：`agent/gemini/ai-recommend-tooltip-popover`（匹配度浮层与规则说明可视化）
- 提交：`agent/gemini/fix-case-mismatch`（评分项编号前缀清洗与正文穿透匹配）

