### PR 概述

本 PR 完整落地 **Feature 046（AI 智能推荐案例）**，基于《AI 智能推荐案例 — 抽屉详情（PRD）》构建真实的标书案例库底座与评分项细粒度语义推荐引擎，并打通前端抽屉、高亮对比弹窗与标书章节复用闭环。

---

### 主要变更内容

#### 1. 数据底座 (Phase 1)
- 数据库 Flyway 迁移 `V1193__add_score_item_to_case_reference.sql` 与成对回滚脚本 `U1193`，为 `case_reference_records` 增加 `project_id`、`score_item_id`、`reuse_mode` 字段及联合索引。
- 扩展 `CaseReferenceRecord` 实体、DTO、CreateRequest 及 Repository 查询支持。

#### 2. 纯核心匹配策略 (Phase 2 - FP-Java)
- 纯 Java 核心策略 `ScoreItemCaseMatchPolicy`：以 `ScoreItem.detail` 评分条款为核心进行 n-gram 滑动分词重叠度计算（最高 40 分），标题/维度匹配（最高 20 分），评分类别一致性（最高 15 分），项目/客户类型一致性（最高 15 分），关键词命中（最高 5 分），历史中标加成（最高 5 分），产出 0~100 综合得分与命中高亮词列表。
- 100% 独立单元测试覆盖。

#### 3. 应用服务与 API (Phase 3)
- `ScoreItemCaseRecommendAppService`：租户项目权限守卫、加载评分项、调用纯核心 Policy 计算并按（置顶优先 -> 分数优先 -> 复用数优先）排序分页。
- 暴露 `POST /api/projects/{projectId}/cases/recommend` 与 `GET /api/projects/{projectId}/score-items` 契约接口。
- 增强 `POST /api/cases/{id}/reuse` 案例复用与计数接口。
- Controller 集成测试与权限边界测试全量通过。

#### 4. 前端组件与标书复用闭环 (Phase 4 & 5)
- `src/api/modules/caseRecommendation.js` 统一封装推荐、评分项获取、案例详情及复用 API。
- `AICaseRecommendDrawer.vue`：680px 侧边抽屉，支持评分项联动、关键词即时过滤、卡片列表、置顶/中标/特征标签与动态加载文案。
- `AICaseDetailModal.vue`：元数据表格（项目类型/客户类型/招标主体/复用数等），需求原文（限高 150px 滚动）与应答原文（限高 240px 滚动）高亮对比。
- `AICaseReuseSectionModal.vue`：章节树级联选择、追加到末尾/覆盖当前章节模式切换，提交后端复用并自增计数。
- 前端测试：3 个组件 spec 测试全量通过（Vitest）。

---

### 自动化测试证据

1. **后端 Maven JUnit 5（7/7 PASS）**：
   `mvn test -Dtest=ScoreItemCaseMatchPolicyTest,ScoreItemCaseRecommendAppServiceTest,ScoreItemCaseRecommendControllerTest` -> `BUILD SUCCESS`
2. **架构与权限门禁测试（12/12 PASS）**：
   `mvn test -Dtest=MaintainabilityArchitectureTest,FPJavaArchitectureTest,ProjectAccessGuardCoverageTest` -> `BUILD SUCCESS`
3. **前端 Vitest（6/6 PASS）**：
   `npx vitest run src/views/Bidding/components/AICase*.spec.js` -> `Test Files: 3 passed (3), Tests: 6 passed (6)`
4. **设计 Token 治理检查**：
   `npm run check:token-governance:delta` -> `No new hardcoded colors` (PASS)
5. **行数预算治理检查**：
   `npm run check:line-budgets:staged` -> `line-budget check passed` (PASS)
