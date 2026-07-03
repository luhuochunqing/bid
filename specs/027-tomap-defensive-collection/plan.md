# Implementation Plan: 防御性 Collection 与优雅降级治理

**Branch**: `agent/qoder/tomap-robustness-defensive-collection` | **Date**: 2026-07-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/027-tomap-defensive-collection/spec.md`

## Summary

修复全仓 31 处 `Collectors.toMap` 2 参数版本（无 merge function）隐患，为装饰性 enrichment 方法加 try-catch 降级，对齐 5xx 异常 handler 诊断标准（堆栈+Payload+Sentry），新增 ArchUnit 守卫规则 + pre-push gate 拦截新增无 merge function 的 toMap 调用。根因：2026-07-03 标讯列表崩溃事件（PR #1640）暴露三层失效——toMap fail-fast、enrichment 无降级、handler 无诊断。治理基础：Constitution v2.0.0 Principle VII。

## Technical Context

**Language/Version**: Java 21 (backend), Node.js 20 (scripts/gate)

**Primary Dependencies**: Spring Boot 3.2, JPA, ArchUnit (架构测试), Sentry (异常上报), Mockito/JUnit 5 (测试)

**Storage**: MySQL 8.0（无 schema 变更，纯代码治理）

**Testing**: JUnit 5 + Mockito（单元测试）, ArchUnit（架构守卫）, `mvn test -Dtest=ArchitectureTest`

**Target Platform**: Linux server (生产), macOS (开发)

**Project Type**: web-service (Spring Boot 后端)

**Performance Goals**: pre-push gate 扫描 <2s；ArchUnit 守卫增量 <5s

**Constraints**: 不修改 DB schema；不破坏现有 API 契约（409 状态码保留）；31 处修复必须逐处验证不影响业务逻辑

**Scale/Scope**: 31 处 toMap 修复（28 处高风险 + 3 处中低风险）+ ~5 个 enrichment 降级 + ~3 个 handler 诊断 + 1 个 ArchUnit 规则 + 1 个 pre-push gate 脚本

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. FP-Java Architecture | ✅ PASS | 修复均在 Service 层，不涉及 Pure Core / Imperative Shell 边界 |
| II. Real-API Only | ✅ PASS | 纯后端代码治理，无 Mock 引入 |
| III. Test-Driven Development | ✅ PASS | ArchUnit 守卫 + enrichment 降级单元测试 + handler 诊断测试 |
| IV. Split-First & Simplicity | ✅ PASS | 不新增文件，仅修改现有 toMap 调用；pre-push gate 脚本 <100 行 |
| V. OSS Integration | ✅ N/A | 不涉及 OSS 集成 |
| VI. Authorization Unification | ✅ N/A | 不涉及权限 |
| **VII. Defensive Collection & Graceful Degradation** | ✅ PASS | **本 feature 的治理目标**——修复存量 + 防新增 + 加诊断 |
| VIII. Boring Proven Patterns | ✅ PASS | 使用标准 `toMap(keyMapper, valueMapper, (a,b)->a)` 模式，无魔法 |

**Gate Result**: PASS（无违规，无需 Complexity Tracking）

## Project Structure

### Documentation (this feature)

```text
specs/027-tomap-defensive-collection/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出（技术调研）
├── data-model.md        # Phase 1 输出（无新实体，简化）
├── quickstart.md        # Phase 1 输出（验证步骤）
├── contracts/           # Phase 1 输出（无新 API 契约，简化）
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成）
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/
│   ├── tender/service/TenderQueryService.java          # 2 处 toMap 已在 PR #1640 修复
│   ├── project/service/ProjectQueryService.java         # 4 处 toMap 待修复（高风险）
│   ├── project/service/ProjectExportService.java        # 1 处 toMap 待修复（已部分修复）
│   ├── casework/application/ProjectArchiveExportService.java  # 2 处 toMap 待修复
│   ├── tenderfavorite/service/TenderFavoriteService.java  # 1 处 toMap 待修复
│   ├── task/service/TaskBoardService.java               # 2 处 toMap 待修复
│   ├── task/service/TaskStatusDictAdminService.java     # 1 处 toMap 待修复
│   ├── task/service/TaskExtendedFieldAdminService.java  # 1 处 toMap 待修复
│   ├── documenteditor/service/DocumentSectionTreeService.java  # 2 处 toMap 待修复（高风险）
│   ├── resources/expenseledger/application/ExpenseLedgerApplicationService.java  # 2 处
│   ├── resources/service/CustodianEmployeeNumberResolver.java  # 1 处
│   ├── resources/service/CaBorrowApplicationNameEnricher.java  # 2 处
│   ├── platform/service/AccountBorrowApplicationMapper.java  # 2 处
│   ├── alerts/service/AlertHistoryQueryService.java     # 1 处
│   ├── warehouse/application/WarehouseExportAppService.java  # 1 处
│   ├── warehouse/application/WarehouseLedgerExportAppService.java  # 1 处
│   ├── workflowform/infrastructure/persistence/JpaWorkflowFormAdminStore.java  # 2 处（高风险）
│   ├── service/AdminUserService.java                    # 1 处（中低风险）
│   ├── admin/settings/core/DepartmentGraphPolicy.java   # 1 处（中低风险）
│   ├── warehouse/domain/WarehouseAttachmentExportPolicy.java  # 1 处（中低风险）
│   └── exception/GlobalExceptionHandler.java            # handler 诊断（PR #1640 已修 1 处，待补其他 5xx）
├── src/test/java/com/xiyu/bid/
│   ├── tender/service/TenderQueryServiceTest.java       # 已有 2 个回归测试（PR #1640）
│   └── architecture/ArchitectureTest.java               # 新增 toMap 守卫规则
└── pom.xml

scripts/
├── check-tomap-no-merge-function.mjs                    # 新增 pre-push gate 脚本
└── pre-push-gate.sh                                     # 接入新检查
```

**Structure Decision**: 使用现有 web-service 结构（Option 2），不新增模块。31 处 toMap 修复分布在 20 个文件中，均在现有 `backend/src/main/java/com/xiyu/bid/` 下。ArchUnit 守卫规则加入现有 `ArchitectureTest.java`。pre-push gate 脚本加入现有 `scripts/` 目录。

## Complexity Tracking

> 无 Constitution Check 违规，无需填写。
