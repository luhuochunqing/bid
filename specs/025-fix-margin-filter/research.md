# Phase 0 Research: 保证金看板状态筛选修复

**Date**: 2026-07-02
**Status**: Complete
**Feature**: [spec.md](./spec.md) | [plan.md](./plan.md)

## 研究背景

根因已在【全链路日志排查 SOP】Layer 2（代码层结构化溯源）阶段定位完成。本文件将排查结论固化为研究决策，作为 Phase 1 设计的输入。

## 决策清单

### Decision 1: PENDING 分支添加 `OR m.exp_return_date IS NULL` 谓词

**Decision**: 在 `MarginQuerySupport.appendStatusFilter` 的 `case "PENDING"` 分支中，将 `m.exp_return_date >= NOW()` 改为 `(m.exp_return_date IS NULL OR m.exp_return_date >= NOW())`。

**Rationale**: 派生表 init 分支（立项未缴占位行）的 `exp_return_date` 为 NULL，MySQL 中 `NULL >= NOW()` 求值为 NULL（falsy），导致这些行被 PENDING 筛选漏掉。但 `label()` 函数把这些行标为"未到期"，造成"未筛选可见 / 筛选后消失"的体验断层。添加 `IS NULL` 分支让筛选语义对齐标签语义。

**Alternatives considered**:
- 在 init 分支用 `COALESCE(exp_return_date, '2099-12-31')` 给 NULL 填充远期日期 → 拒绝，会改变派生表结构，影响其他筛选维度（如时间范围筛选），违反 spec Assumptions。
- 在前端过滤 → 拒绝，后端返回数据不全，分页失效，且违反"前后端语义一致"原则。

### Decision 2: RETURNED 分支改为 `m.status IN ('RETURNED','CANCELLED')`

**Decision**: 将 `case "RETURNED"` 分支的 `m.status = 'RETURNED'` 改为 `m.status IN ('RETURNED','CANCELLED')`。

**Rationale**: `label()` 函数把 RETURNED 和 CANCELLED 都标为"已退回"（[MarginQuerySupport.java:254](../../backend/src/main/java/com/xiyu/bid/resources/service/MarginQuerySupport.java#L254)），`summaryBase` 也用 `NOT IN ('RETURNED','CANCELLED')` 定义"非退回"。但筛选分支只匹配 RETURNED，遗漏 CANCELLED。CANCELLED 的 BID_BOND 来自 `FeeService.cancelFee`（取消已发起的缴费）。修复后筛选语义与标签语义对齐。

**Alternatives considered**:
- 把 CANCELLED 的 fee 物理删除而非保留 → 拒绝，破坏审计追溯，违反生产数据保留原则。
- 在 `label()` 中把 CANCELLED 单独标为"已取消" → 拒绝，会引入新 UI 标签，扩大改动范围，且 spec 明确"标签函数语义不变"。

### Decision 3: OVERDUE 分支保持不变

**Decision**: `case "OVERDUE"` 分支不动，仍为 `m.status NOT IN ('RETURNED','CANCELLED') AND m.exp_return_date < NOW()`。

**Rationale**: 当前 OVERDUE 语义正确——立项占位行（`exp_return_date IS NULL`）不应被算作"已超期"（`NULL < NOW()` 为 NULL/falsy，自动排除），与 `label()` 中"exp != null && exp.toLocalDateTime().isBefore(now)"的判断一致。P3 User Story 明确要求回归保护。

**Alternatives considered**: 无。保持不变是唯一选择。

### Decision 4: 测试模式升级——从"语法验证"到"行为验证"

**Decision**: 在 `MarginQuerySupportMysqlIntegrationTest` 中新增 3 个行为断言测试，验证筛选返回的行数和内容，而非仅验证 SQL 不抛异常。

**Rationale**: 既有 46 个测试只断言 SQL "不抛异常"（[L186](../../backend/src/test/java/com/xiyu/bid/resources/service/MarginQuerySupportMysqlIntegrationTest.java#L186)），逻辑错误全部漏过。这是本次 Bug 没能在 CI 拦截的直接原因。新增测试必须造数据 + 断言返回行数，形成真正的回归保护。

**Alternatives considered**:
- 只补单元测试（不连真实 MySQL）→ 拒绝，SQL 语义验证需要真实数据库验证 NULL 比较行为，纯单元测试无法捕获。
- 重构既有 46 个测试为行为断言 → 拒绝，超出本 Bug 修复范围，违反"最小改动"原则。本次只新增 3 个测试，既有测试保持原样。

## 根因时间线（用于 lessons-learned 沉淀）

| 时间 | Commit | 事件 |
|------|--------|------|
| 早期 | `9db750a60` 之前 | filter 用 `f.fee_date >= NOW()`（只针对 fees 表），语义正确 |
| 2026-06 | `4b63ed642` | 引入 UNION ALL init 分支，`exp_return_date` 改为派生表列，但 PENDING filter 未同步处理 NULL |
| 2026-06 | `1735618e5` | 后续调整，NULL 处理仍缺失 |
| 2026-07-02 | 本次修复 | 对齐 label() 语义，补 IS NULL 分支 + 行为测试 |

**关键教训**：UNION ALL 重构时，所有引用派生表列的 filter 都必须重新评估 NULL 语义。`label()` 函数与 filter 函数的语义必须保持一致，最好有共享的语义定义源。

## 结论

所有决策已明确，无 NEEDS CLARIFICATION 残留。可以进入 Phase 1 设计阶段。
