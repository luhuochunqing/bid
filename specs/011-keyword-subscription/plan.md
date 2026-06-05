# Implementation Plan: 011-标讯关键词订阅

**Branch**: `011-keyword-subscription` | **Date**: 2026-05-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/011-keyword-subscription/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

实现标讯关键词订阅功能。用户可以设置关键词组合（AND/OR 逻辑），系统每天定时匹配新增标讯，结果通过站内通知推送。后端包括：数据库迁移（4张新表）、REST API（CRUD + 查询）、定时匹配任务。前端为关键词订阅管理基础页面和匹配结果展示页面。

## Technical Context

**Language/Version**: Java 17 (后端), TypeScript/JavaScript (前端 Vue 3)

**Primary Dependencies**: 
- 后端：Spring Boot 3, Spring Data JPA, Flyway, Spring @Scheduled, MySQL 8.0
- 前端：Vue 3, Element Plus, Vue Router, Pinia

**Storage**: MySQL 8.0（通过 Flyway 迁移管理）

**Testing**: 
- 后端：JUnit 5, Mockito, Spring Boot Test, ArchUnit（架构测试）
- 前端：Vitest, Vue Test Utils

**Target Platform**: Web (桌面端浏览器)

**Project Type**: Web application (Spring Boot 后端 + Vue 3 前端)

**Performance Goals**: 日匹配任务在 5 分钟内完成；API 响应时间 <200ms（CRUD 操作）

**Constraints**: 
- 关键词上限：每条规则最多 10 个关键词
- 匹配历史保留：90 天
- 每日匹配定时执行（默认凌晨 2:00）

**Scale/Scope**: 初期支持 100 以内用户，每人 10-20 条规则，每天处理数百条新增标讯

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Check

| Principle | Status | Justification |
|-----------|--------|---------------|
| I. FP-Java Architecture | PASS | 新功能将严格遵循分层架构：Controller → Service → Repository。核心匹配逻辑（关键词匹配算法）作为 Pure Core 与 Spring 依赖分离 |
| II. Real-API Only | PASS | 基于已有真实 API 体系，无 Mock 引入 |
| III. TDD | PASS | 将为 Service 层和匹配逻辑编写单元测试 |
| IV. Split-First & Simplicity | PASS | 功能按领域拆分：subscription CRUD、matching engine、notification。无上帝类 |
| V. Boring Proven Patterns | PASS | 使用 Spring Data JPA Repository 模式、@Scheduled、REST API — 均为项目已有模式 |

**No violations found. Complexity Tracking not required.**

## Project Structure

### Documentation (this feature)

```text
specs/011-keyword-subscription/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
# Option 2: Web application (frontend + backend)

backend/src/main/java/com/xiyu/bid/
└── keywordsubscription/
    ├── controller/       # REST API 控制器
    ├── service/          # 业务逻辑和匹配引擎
    ├── repository/       # JPA Repository
    ├── model/            # JPA 实体
    ├── dto/              # 数据传输对象（请求/响应）
    └── scheduler/        # 定时匹配任务

backend/src/main/resources/db/
└── migration-mysql/
    └── V{next}__keyword_subscription.sql  # Flyway 迁移

frontend/src/views/
└── keyword-subscription/  # 关键词订阅管理页面
    ├── SubscriptionList.vue
    ├── SubscriptionForm.vue
    └── MatchResultList.vue
```

**Structure Decision**: Web application structure (Option 2). Backend follows project conventions with new domain package `keywordsubscription`. Frontend adds new view directory matching existing patterns.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations. Complexity Tracking not applicable.
