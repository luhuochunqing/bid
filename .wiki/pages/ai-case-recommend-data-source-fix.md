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
health_checked: 2026-08-21
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

## 消费方地图（改这个 API 前必读）

- `AiRecommendDrawer.vue`（项目详情抽屉）— 4 个方法全用
- `AICaseReuseSectionModal.vue`（Bidding 侧复用弹窗）— 用 `reuseCase`，返回值经
  `reuse-success` 事件透传，消费方不读 `result` 字段
- `AICaseRecommendDrawer.vue`（Bidding 版抽屉）— 自带双形态兼容写法，
  **当前未被任何页面挂载**（仅 spec 引用），改动不会在生产暴露

## 关联

- [[score-parse-service]] — spec-041 解析链路（score_item 数据的生产方）
- [[frontend-pitfalls]] — 前端陷阱集
- 提交：`agent/qoder/ai-case-recommend-fix`（切源 + 二次解包修复 + E2E 时序加固）
