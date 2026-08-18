# Implementation Plan: AI 智能推荐案例（Feature 046）

**Branch**: `046-ai-case-recommendation` | **Date**: 2026-08-18 | **Spec**: [specs/046-ai-case-recommendation/spec.md](file:///Users/user/xiyu/worktrees/gemini/specs/046-ai-case-recommendation/spec.md)

**Input**: 基于《AI 智能推荐案例 — 抽屉详情（PRD）》，构建真实可生产的案例库数据模型与评分项级语义推荐引擎。

## 1. 架构总览与分层设计

```text
┌─────────────────────────────────────────────────────────────┐
│                    Controller / API 层                       │
│  - GET  /api/projects/{projectId}/score-parse/items         │
│  - POST /api/projects/{projectId}/cases/recommend          │
│  - GET  /api/cases/{id}                                     │
│  - POST /api/cases/{id}/reuse                               │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Application 编排层 (副作用隔离)               │
│  - ScoreItemCaseRecommendAppService                         │
│    ├── ProjectAccessScopeService (权限校验)                  │
│    ├── ScoreItemRepository (加载当前评分项条款 detail)          │
│    ├── KnowledgeCaseRepository (召回候选案例)                │
│    └── CaseReferenceAppService (原子自增 + 复用记录)          │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Domain 纯核心策略层 (FP-Java)                │
│  - ScoreItemCaseMatchPolicy (纯函数式，零 Spring 依赖)        │
│    ├── 评分项 Detail 条款语义与关键词 Jaccard/Overlap 匹配      │
│    ├── 类别/项目类型/客户类型一致性加权计算                    │
│    ├── 中标案例 WON 加成与高亮匹配词生成                     │
│    └── 综合评分 (0~100) 与评级 (优质/良好/一般) 排序截断       │
└─────────────────────────────────────────────────────────────┘
```

## 2. Technical Context

- **Language/Version**: Java 21, Spring Boot 3.2, Vue 3 + Vite 5
- **Primary Dependencies**: Spring Data JPA, Flyway, Jackson, Lombok
- **Storage**: MySQL 8.0 (`knowledge_case`, `score_item`, `case_reference_record`)
- **Testing**: JUnit 5, Mockito, AssertJ, ArchUnit
- **Target Platform**: Linux / Containerized Spring Boot
- **Constraints**:
  - 严格遵守 FP-Java 规范：纯核心策略类（`ScoreItemCaseMatchPolicy`）纯逻辑、零外部依赖、100% 可单测。
  - 接口权限守卫严格对齐（`ProjectAccessScopeService`）。
  - Flyway 迁移脚本版本号严格按 `scripts/next-migration-version.sh` 取号（`V1193`）。

## 3. 门禁检查清单 (Constitution Gates)

- [x] **Gate 1 (No Mock)**: 推荐计算不依赖前端伪造数组，全部基于后端真实数据库案例与策略算法。
- [x] **Gate 2 (FP-Java Pure Core)**: `ScoreItemCaseMatchPolicy` 位于 `domain/policy/`，无框架依赖。
- [x] **Gate 3 (Permissions Guard)**: Controller 方法严格使用 `@PreAuthorize` 并调用 `ProjectAccessScopeService`。
- [x] **Gate 4 (Flyway Consistency)**: 迁移文件 `V1193__*.sql` 与回滚文件 `U1193__*.sql` 成对创建。

## 4. 实施阶段计划

### Phase 1: 数据模型与迁移 (Data Layer)
- [ ] 创建 Flyway 迁移 `V1193__add_score_item_to_case_reference.sql` 与回滚脚本 `U1193__*.sql`。
- [ ] 扩展 `CaseReferenceRecord` 实体与 DTO，支持 `scoreItemId` 与 `reuseMode`。

### Phase 2: 评分项级推荐核心策略与服务 (Recommendation Engine)
- [ ] 编写纯核心 `ScoreItemCaseMatchPolicy`，实现基于 `ScoreItem.detail` 的语义条款匹配算法及单元测试。
- [ ] 编写应用服务 `ScoreItemCaseRecommendAppService`，组装评分项、候选案例与 Policy 排序。
- [ ] 暴露 `POST /api/projects/{projectId}/cases/recommend` 接口与 `POST /api/cases/{id}/reuse` 接口。
- [ ] 编写 Controller 集成测试与边界条件测试。

### Phase 3: 前端抽屉与标书复用闭环 (UI Integration)
- [ ] 对接推荐抽屉组件，支持项目评分项下拉联动与关键词搜索。
- [ ] 对接案例详情弹窗，实现匹配高亮展示。
- [ ] 对接标书编辑器章节选择与追加/覆盖/撤销。
