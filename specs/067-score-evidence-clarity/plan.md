# Implementation Plan: 评分依据可信度与中文可读性

**Branch**: `agent/codex/fix-score-evidence-clarity` | **Date**: 2026-09-20 | **Spec**: [spec.md](spec.md)

## Summary

修复业绩预计分把“候选记录数量”误当“全部强制条件已满足”的问题。新增纯核心策略识别现有业绩库无法证明的必备材料；应用服务仍查询候选，但证据不完整时强制返回待确认并生成清晰中文依据。前端把提取来源等内部枚举翻译为业务中文，并将“知识库命中”改为准确的数据来源标签。

## Technical Context

**Language/Version**: Java 21；Vue 3 / JavaScript

**Primary Dependencies**: Spring Boot 3.2、JPA、Element Plus

**Storage**: 复用现有 MySQL `performance_record` 与 `score_item`，无迁移

**Testing**: JUnit 5 + AssertJ + Mockito；Vitest + Vue Test Utils

**Target Platform**: 西域数智化投标管理平台 Web 应用

**Project Type**: 前后端 Web 应用缺陷修复

**Performance Goals**: 不新增数据库查询；证据规则为评分项文本的线性扫描

**Constraints**: 不触发生产重算；不伪造附件证据；不改变阶段 2 实际评分

**Scale/Scope**: 五个源码/测试关注点，零数据库变更

## Constitution Check

- **I FP-Java**: 新证据规则位于无框架依赖的纯核心；应用服务只负责编排和实体回填。
- **II Real-API Only**: 不引入 Mock 业务路径；单测仅隔离 Repository/Matcher 协作者。
- **III TDD**: 金额提取、证据不完整、中文零泄漏测试先失败后实现。
- **IV Split-First**: 新规则单独建 Policy，避免继续扩张 `EstimatedScoreService`。
- **VII Defensive Collection**: 不新增 `Collectors.toMap`；空文本/空候选安全降级。
- **VIII Boring Patterns**: 使用显式正则与不可变 record，不引入新依赖。

Phase 1 复核：设计不违反 Constitution，无 Complexity Tracking 例外。

## Project Structure

### Documentation

```text
specs/067-score-evidence-clarity/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/score-evidence-ui.md
└── tasks.md
```

### Source Code

```text
backend/src/main/java/com/xiyu/bid/scoreparse/
├── domain/KnowledgeCategoryPolicy.java
├── domain/ProjectEvidenceRequirementPolicy.java
├── application/EstimatedScoreBasisFormatter.java
└── application/EstimatedScoreService.java

backend/src/test/java/com/xiyu/bid/scoreparse/
├── domain/KnowledgeCategoryPolicyTest.java
├── domain/ProjectEvidenceRequirementPolicyTest.java
└── application/EstimatedScoreServiceTest.java

src/views/Project/stages/components/
├── ScoreItemDetailModal.vue
└── ScoreItemDetailModal.spec.js
```

**Structure Decision**: 业务判断放纯核心 Policy，现有应用服务仅组合匹配结果与证据判定，Vue 组件只翻译和呈现 API 已有字段。
