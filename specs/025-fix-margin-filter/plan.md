# Implementation Plan: 保证金看板状态筛选修复

**Branch**: `agent/codex/fix-margin-filter-not-working` | **Date**: 2026-07-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/025-fix-margin-filter/spec.md`

## Summary

修复保证金看板（`/resource/margin`）状态筛选不生效 Bug。根因在 [MarginQuerySupport.java](../../backend/src/main/java/com/xiyu/bid/resources/service/MarginQuerySupport.java) 的 `appendStatusFilter` 方法：PENDING 分支漏处理 `exp_return_date IS NULL` 的立项占位行，RETURNED 分支漏包含 CANCELLED 状态。修复方案是对齐 `label()` 函数的语义，让筛选结果与表格标签 100% 一致。同时补充行为层回归测试（既有测试只验证 SQL 不抛异常，不验证返回行数）。

## Technical Context

**Language/Version**: Java 21（后端）/ Spring Boot 3.2

**Primary Dependencies**: Spring Boot 3.2 + JPA + MySQL 8.0 驱动

**Storage**: MySQL 8.0（数据库 `xiyu_bid_main`，表 `fees` + `project_initiation_details`）

**Testing**: JUnit 5 + Mockito + Testcontainers（MySQL 集成测试，已有 `MarginQuerySupportMysqlIntegrationTest` 基础设施）

**Target Platform**: Linux server（生产）/ macOS（开发）

**Project Type**: web-service（Spring Boot REST API）

**Performance Goals**: 不变。修复不引入新查询，只改 WHERE 子句谓词，查询计划无明显影响。

**Constraints**:
- 不改变派生表 UNION ALL 结构（fees 分支 + init 分支）
- 不改变 `label()` 函数语义
- 不改变其他筛选维度（项目名/时间/金额）
- 单文件修改范围（`MarginQuerySupport.java`）+ 测试补充
- 文件行数软上限 200 行，硬上限 300 行（当前 261 行，修复后不增长）

**Scale/Scope**: 1 个生产文件 + 1 个测试文件，约 10 行代码改动 + 3 个新测试用例

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查项 | 状态 | 说明 |
|------|--------|------|------|
| I. FP-Java Architecture | MarginQuerySupport 是 Imperative Shell（SQL 构建器），不涉及 Pure Core 改动 | ✅ PASS | 修复点在 Shell 层 SQL 拼接逻辑，不触碰业务规则核心 |
| II. Real-API Only | 修复后端真实查询逻辑，不引入 Mock | ✅ PASS | 测试用 Testcontainers 真实 MySQL，符合 Real-API 原则 |
| III. Test-Driven Development | 先补行为层测试（Red）→ 修复代码（Green）→ 重构 | ✅ PASS | 将按 TDD 顺序：先写 3 个失败测试 → 修复 2 个 case → 验证 |
| IV. Split-First & Simplicity | MarginQuerySupport 当前 261 行，修复后不增长 | ✅ PASS | 只改 case 分支内部谓词，不新增方法/类 |
| V. OSS Integration | 不涉及 OSS 集成 | ✅ N/A | 修复范围不触碰 OSS 调用 |
| VI. Authorization Unification | 不涉及权限改动 | ✅ N/A | MarginController 的 `@PreAuthorize` 不变 |
| VII. Boring Proven Patterns | 只改 SQL WHERE 子句，平淡修复 | ✅ PASS | 不引入新框架/魔法用法，对齐既有 `label()` 语义 |

**Gate 结论**：所有 Constitution 原则 PASS，无违规需要 Complexity Tracking 记录。可以进入 Phase 0。

## Project Structure

### Documentation (this feature)

```text
specs/025-fix-margin-filter/
├── plan.md              # 本文件
├── spec.md              # Feature 规格（已完成）
├── checklists/
│   └── requirements.md  # Spec 质量检查清单（已完成）
├── research.md          # Phase 0 输出（根因研究，根因已在排查阶段定位）
├── data-model.md        # Phase 1 输出（数据模型说明，复用既有实体）
├── quickstart.md        # Phase 1 输出（验证步骤）
├── contracts/
│   └── api-contract.md  # Phase 1 输出（API 契约不变说明）
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成，本阶段不创建）
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/resources/
│   ├── controller/
│   │   └── MarginController.java          # 不改（status 参数接收不变）
│   └── service/
│       ├── MarginService.java             # 不改（只调用 QuerySupport，无业务逻辑改动）
│       ├── MarginQuerySupport.java        # ⭐ 修改：appendStatusFilter 的 PENDING/RETURNED 分支
│       ├── MarginQueryRole.java           # 不改（角色权限过滤不变）
│       └── MarginDerivedTableColumns.java # 不改（UNION ALL 结构不变）
└── src/test/java/com/xiyu/bid/resources/service/
    └── MarginQuerySupportMysqlIntegrationTest.java  # ⭐ 补 3 个行为断言测试
```

**Structure Decision**: 选择"最小改动"结构。只动 2 个文件（1 生产 + 1 测试），不新增模块/包/类。符合 Constitution IV（Split-First & Simplicity）的"YAGNI 原则避免过度工程化"。

## Complexity Tracking

> 无 Constitution Check 违规，本表为空。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| - | - | - |
