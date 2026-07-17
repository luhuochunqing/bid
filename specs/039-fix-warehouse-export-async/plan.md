# Implementation Plan: 修复仓库全量合订本导出任务创建失败

**Branch**: `agent/claude/fix-warehouse-export-async` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/039-fix-warehouse-export-async/spec.md`

## Summary

CO-582 引入 Word 合订本生成（含 PDF 渲染）后，`WarehouseExportAppService.export()` 在 `@Transactional` 方法内直接调用 `this.executeExportAsync()`，Spring AOP 代理不拦截同类内部方法调用，导致 `@Async("warehouseExportExecutor")` 注解静默失效。Word 合订本生成在 HTTP 请求线程同步执行，超过前端 axios 30 秒超时，前端 catch 块显示"创建导出任务失败"。

修复方案：提取 `executeExportAsync` / `executeExportByIdsAsync` 到独立的 Spring Bean `WarehouseExportAsyncExecutor`，通过依赖注入调用，使 `@Async` 代理生效。这是 Spring @Async 同类调用失效的标准修复模式，符合 Constitution §VIII Boring Proven Patterns。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.2, Spring AOP（代理模式，非 AspectJ 编译时织入）, Apache POI（Word 生成）, Apache PDFBox（PDF 渲染）

**Storage**: MySQL 8.0（WarehouseExportTaskEntity，schema 不变）+ 文件系统（/tmp/warehouse-exports）

**Testing**: JUnit 5 + Mockito + MockMvc（后端）, Vitest（前端单元）, Playwright（E2E）

**Target Platform**: Java 21 + Spring Boot 3.2 服务端

**Project Type**: web-service（Spring Boot 后端）

**Performance Goals**: HTTP 请求响应 < 2 秒（仅创建任务记录），导出在后台异步完成（ Constitution §Async Export 要求 >30 秒必须异步）

**Constraints**: 前端 axios timeout=30000ms；不涉及 DB schema 变更；不涉及 API 契约变更

**Scale/Scope**: 1 个新文件（WarehouseExportAsyncExecutor）+ 1 个修改文件（WarehouseExportAppService）+ 2 个测试文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. FP-Java Architecture | WarehouseExportAsyncExecutor 是 Imperative Shell（@Async 承载），不含业务规则；业务规则仍在 Domain Policy 和 AppService 编排层 | PASS |
| II. Real-API Only | 不涉及 Mock，修复 @Async 代理失效 | PASS |
| III. Test-Driven Development | 先写测试验证 @Async 生效（线程名断言）+ 降级语义不回归，再实现 | PASS |
| IV. Split-First & Simplicity | 新 Bean 单一职责（承载 @Async），<100 行；不引入过度抽象 | PASS |
| V. OSS Integration | 不涉及 | N/A |
| VI. Authorization Unification | 权限点 `WAREHOUSE_MANAGE_PERMISSION` 不变，Controller @PreAuthorize 不变 | PASS |
| VII. Defensive Collection & Graceful Degradation | 现有 `loadUsernames` 已有 merge function `(a,b)->a`（CO-027）；不引入新的 2 参数 toMap；Word 合订本降级语义保留 | PASS |
| VIII. Boring Proven Patterns | 提取独立 Bean 是 Spring @Async 官方推荐的标准修复模式，非"魔法"用法 | PASS |
| Performance Constraints §Async Export | 本次修复正是为了让 >30 秒的导出真正异步化，完全符合 | PASS |

**Gate Result**: 全部 PASS，无 Constitution 违规，无需 Complexity Tracking。

## Project Structure

### Documentation (this feature)

```text
specs/039-fix-warehouse-export-async/
├── plan.md              # This file
├── research.md          # Phase 0: @Async 同类调用失效根因与修复方案研究
├── data-model.md        # Phase 1: WarehouseExportTaskEntity（不变）+ WarehouseExportAsyncExecutor（新增）
├── quickstart.md        # Phase 1: 验证步骤
├── contracts/
│   └── async-executor-contract.md  # Phase 1: WarehouseExportAsyncExecutor 接口契约
├── checklists/
│   └── requirements.md  # specify 阶段已生成
└── tasks.md             # Phase 2: /speckit-tasks 生成
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/warehouse/
│   ├── application/
│   │   ├── WarehouseExportAppService.java          # 修改：删除 @Async 方法，改为调用新 Bean
│   │   └── WarehouseExportAsyncExecutor.java       # 新增：承载 @Async 方法的独立 Bean
│   └── infrastructure/
│       └── WarehouseExportTaskEntity.java          # 不变
└── src/test/java/com/xiyu/bid/warehouse/
    ├── application/
    │   ├── WarehouseExportAppServiceTest.java      # 新增：验证委托调用 + @Async 生效
    │   └── WarehouseExportAsyncExecutorTest.java   # 新增：验证异步执行 + 降级语义
    └── controller/
        └── WarehouseExportControllerTest.java      # 不变（API 契约不变）
```

**Structure Decision**: 遵循 FP-Java 分层，新增 `WarehouseExportAsyncExecutor` 放在 `application/` 包（Imperative Shell 编排层），与 `WarehouseExportAppService` 同包，通过构造器注入。不引入新的 domain/ 包，因为 @Async 是基础设施关注点而非业务规则。

## Complexity Tracking

> 无 Constitution 违规，本表为空。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| - | - | - |
