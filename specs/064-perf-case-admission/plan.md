# Implementation Plan: 业绩评分项案例推荐精准准入（品类 + 金额门槛 + 资格项回退）

**Branch**: `agent/gemini/fix-perf-case-admission` | **Date**: 2026-09-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/064-perf-case-admission/spec.md`

## Summary

在 casework 领域策略层新增两个纯核心策略：`PerformanceThresholdExtractor`（从资格项/评分项文本解析“品类-金额门槛”并统一为万元）与 `PerformanceCaseAdmissionPolicy`（业绩证据准入 + 分层降级）。同时扩展 `QualificationPerformanceRetriever` 为“全部实质条款合并 + 结构化门槛输出”，修正 `CaseCategoryMatchPolicy` 中混合类别“业绩优先”的归类顺序。`ScoreItemCaseRecommendAppService` 仅做编排：按业绩评分项（或未选评分项 + 资格项含业绩要求）执行准入过滤与分层回退。前端与接口契约零改动。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.2（编排层已有）、JUnit 5 + AssertJ（测试）

**Storage**: MySQL 8.0（只读；本次无 Flyway 迁移）

**Testing**: `mvn test`（定向单测 + casework 模块回归 + FPJava/Architecture/ProjectAccess 门禁）+ `node scripts/check-line-budgets.mjs`

**Target Platform**: Linux server（部署）/ macOS（开发）

**Project Type**: web-service（后端模块内领域策略改造）

**Performance Goals**: 准入为 O(n) 纯函数；不新增外部调用；推荐接口保持现有内存打分量级（现有实测 80–170ms）

**Constraints**: 单 Java 文件硬上限 300 行；`domain/policy` 纯核心（FP-Java 门禁）；前端零改动；接口响应结构不变

**Scale/Scope**: 5 个后端源文件（2 新增 / 3 修改）+ 5 个测试文件；案例库单次全量候选（当前 12 条 / 生产千级切片）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结论 | 证据/说明 |
|---|---|---|
| I. FP-Java 架构 | PASS | 新增规则全部落 `domain/policy` 纯类（record/final、无 I/O、无框架依赖）；AppService 只做编排 |
| II. Real-API Only | PASS | 不新增 Mock/双模式；测试用构造数据 |
| III. TDD | PASS | RED-first：先提交失败用例（人员案例放行、多条款丢门槛、资质业绩误判），再实现 |
| IV. Split-First & Simplicity | PASS | 新增 2 个小文件（预估 180/90 行）；`QualificationPerformanceRetriever` 改动后 ≤300 行 |
| V. OSS Integration | N/A | 不涉及 OSS |
| VI. Authorization Unification | PASS | 推荐入口已由 `assertCurrentUserCanAccessProject` 守卫，无新端点 |
| VII. Defensive Collection | PASS | 无新增 `Collectors.toMap`；准入降级遵循“装饰性逻辑不得拖垮主链路” |
| VIII. Boring Proven Patterns | PASS | 复用现有策略/端口模式；不引入新框架 |
| DB Migrations | PASS | 本次无迁移（金额/品类字段登记技术债） |
| Blueprint E2E Mandate | PASS（豁免） | 纯后端改动，适用 `[skip e2e-scope]` 豁免，在收尾 PR 说明 |

## Project Structure

### Documentation (this feature)

```text
specs/064-perf-case-admission/
├── spec.md              # 需求规格
├── plan.md              # 本文件
├── research.md          # Phase 0：决策与备选
├── data-model.md        # Phase 1：领域对象与规则
├── quickstart.md        # Phase 1：验证指南
├── checklists/
│   └── requirements.md  # 规格质量清单
└── tasks.md             # Phase 2：任务清单
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/casework/
├── domain/policy/
│   ├── PerformanceThresholdExtractor.java      # NEW：品类-金额门槛解析（万元归一）
│   ├── PerformanceCaseAdmissionPolicy.java     # NEW：业绩证据准入 + 分层降级 + 跨候选择优
│   ├── QualificationClauseCollector.java       # NEW：资格项实质条款收集（合并/限量/截断）
│   ├── QualificationPerformanceRetriever.java  # MOD：全部条款合并 + 结构化门槛
│   └── CaseCategoryMatchPolicy.java            # MOD：classifyText 业绩优先于资质
└── application/service/
    └── ScoreItemCaseRecommendAppService.java   # MOD：准入编排 + 资格项回退

backend/src/test/java/com/xiyu/bid/casework/
├── domain/policy/
│   ├── PerformanceThresholdExtractorTest.java       # NEW
│   ├── PerformanceCaseAdmissionPolicyTest.java      # NEW
│   ├── QualificationPerformanceRetrieverTest.java   # MOD：多条款/门槛断言
│   └── ScoreItemCaseMatchPolicyTest.java            # MOD：混合类别断言
└── application/service/
    └── ScoreItemCaseRecommendAppServiceTest.java    # MOD：准入/回退集成断言
```

**Structure Decision**: 沿用六边形架构的 casework 模块结构；规则在 `domain/policy`（纯核心），取数与过滤编排在 `application/service`。不新增 Controller/DTO/迁移。

## Complexity Tracking

无 Constitution 违规项，无需豁免（`[skip e2e-scope]` 属蓝图 E2E 规则的官方后端豁免路径，记录在 quickstart）。
