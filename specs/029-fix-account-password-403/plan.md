# Implementation Plan: 修复平台账号密码查看权限异常类型误用

**Branch**: `agent/gemini/fix-account-password-403` | **Date**: 2026-07-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/029-fix-account-password-403/spec.md`

## Summary

`PlatformAccountService.getPassword` 方法在 3 处权限校验失败时抛出 `IllegalStateException`，被 `GlobalExceptionHandler.handleIllegalStateException` 吞成 409 "系统状态冲突，请刷新后重试"，并触发 5xx 诊断路径（ERROR 级日志 + payload dump + Sentry 上报）。Sentry issue XIYU-N（14 天 6 次）显示这是真实噪声源。

修复方案：将 3 处 `IllegalStateException` 替换为 `AccessDeniedException`，走 4xx 路径（403 + WARN 级日志 + 不上报 Sentry），与项目已有 `PlatformAccountViewerPolicy.checkCanManageAccount` 等同类权限校验范式对齐。改动最小，不修改权限判定逻辑、`GlobalExceptionHandler`、`@Auditable` 切面。

## Technical Context

**Language/Version**: Java 21 (Oracle Corporation 21.0.11)

**Primary Dependencies**: Spring Boot 3.2, Spring Security（`org.springframework.security.access.AccessDeniedException`）

**Storage**: N/A（本次不涉及数据库改动）

**Testing**: JUnit 5 + Mockito + Spring Boot Test（已有项目测试基础设施）

**Target Platform**: Linux server（生产 `winbid-01`）

**Project Type**: web-service（Spring Boot REST API）

**Performance Goals**: 不变（本次仅替换异常类型，不影响性能）

**Constraints**: 不变

**Scale/Scope**: 1 个生产代码文件 + 1 个测试文件，共约 5-10 行代码改动 + 3-5 个测试用例

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Core Principle VII. Defensive Collection & Graceful Degradation (NON-NEGOTIABLE) — PASS

§3 要求 5xx 异常 handler MUST 满足诊断标准（log.error + payload + Sentry）。本次修复恰恰是把业务可恢复错误从 5xx 路径剥离到 4xx 路径：
- **Before**: `IllegalStateException` → `handleIllegalStateException` (409 + ERROR + Sentry)
- **After**: `AccessDeniedException` → `handleAccessDeniedException` (403 + WARN + 不上报 Sentry)

`AccessDeniedException` 是 4xx 业务异常，不属于 5xx 诊断标准适用范围。`handleAccessDeniedException` 已存在且符合 4xx 异常处理规范（WARN 级日志，不上报 Sentry）。

### Core Principle VI. Authorization Unification (NON-NEGOTIABLE) — PASS

§1 要求"是否对该具体资源有操作权限" MUST 下沉到 Service 层 Policy。本次修改的 `getPassword` 已在 Service 层（`PlatformAccountService`），未违反"Controller 不得用 hasAnyRole 一刀切"的约束。

注：spec 已明确"本次不做权限校验下沉到 Policy 的对称化重构"（避免范围蔓延）。当前 `getPassword` 在 Service 层内联权限判定是设计偏差但不违反 Constitution（Constitution 要求下沉到 Service 层，已满足；进一步下沉到 Policy 是 DRY 优化，作为技术债单独处理）。

### Core Principle III. Test-Driven Development (NON-NEGOTIABLE) — PASS

本次修改 MUST 遵循 Red → Green → Refactor：
1. 先写测试覆盖 3 处权限校验失败路径，断言抛出 `AccessDeniedException`（Red）
2. 替换异常类型使测试通过（Green）
3. 不引入重构（Refactor 阶段保留给后续技术债任务）

### Core Principle IV. Split-First & Simplicity — PASS

本次改动仅替换异常类型，不新增类、不拆分文件。`PlatformAccountService` 已存在，行数预算不受影响。

### 其他原则（I/II/V/VIII）— N/A 或 PASS

- I（FP-Java）：不涉及核心业务规则改动，仅替换异常载体类型。
- II（Real-API Only）：本次不涉及 Mock。
- V（OSS Integration）：不涉及 OSS。
- VIII（Boring Proven Patterns）：使用项目已有的 `AccessDeniedException` 范式（`PlatformAccountViewerPolicy` CO-416 已建立），是最平淡的修复方式。

**Gate Result**: PASS，无 Constitution 违规，无需 Complexity Tracking 表记录。

## Project Structure

### Documentation (this feature)

```text
specs/029-fix-account-password-403/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (N/A — 无数据模型变更)
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (API 契约变更)
│   └── password-view-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/
└── src/
    ├── main/java/com/xiyu/bid/
    │   ├── platform/service/
    │   │   └── PlatformAccountService.java          # 修改 3 处异常类型
    │   └── exception/GlobalExceptionHandler.java     # 不修改（已有 AccessDeniedException handler）
    └── test/java/com/xiyu/bid/platform/service/
        └── PlatformAccountServiceTest.java           # 新增或补充测试（如已存在）
```

**Structure Decision**: 沿用现有后端项目结构，仅修改 1 个 Service 类 + 1 个测试类。不新增包、不新增类。

## Complexity Tracking

> 无 Constitution 违规，本表为空。
