# Feature Specification: 防御性 Collection 与优雅降级治理

**Feature Branch**: `agent/qoder/tomap-robustness-defensive-collection`

**Created**: 2026-07-03

**Status**: Draft

**Input**: User description: "完整地修复 31 处 Collectors.toMap 无 merge function 隐患，装饰性 enrichment 加降级，异常 handler 对齐 SOP §23，加 ArchUnit 守卫和 pre-push gate 防新增，避免单条边界数据让整个模块崩溃的事件再次发生"

**Constitution Reference**: Core Principle VII (Defensive Collection & Graceful Degradation), Constitution v2.0.0

**Root Cause Context**: PR #1640 (2026-07-03) — tenderId=937 关联 2 个 Project（业务允许的二次招标场景），`Collectors.toMap` 无 merge function 抛 `IllegalStateException`，enrichment 无降级导致整个标讯模块崩溃，handler 无堆栈/Sentry 延迟定位。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 业务用户：边界数据不再让模块崩溃 (Priority: P1)

作为业务用户（投标专员/管理员），当系统中存在边界数据（如一个标讯关联多个项目、同一秒多条分配记录）时，我希望列表页和详情页仍能正常加载，而不是整个模块崩溃显示"加载失败"。我不需要知道边界数据如何产生的，只需要系统继续可用，可能某些装饰性字段（如负责人姓名）暂时为空也能接受。

**Why this priority**: 这是本次事件的直接痛点——一条边界数据让标讯中心整个模块不可用，业务中断。P1 确保"主功能可用"这个底线。

**Independent Test**: 在测试系统构造 tenderId 关联多 Project 的数据，访问标讯列表页和详情页，验证页面正常加载（负责人姓名可能为空但不报错）。

**Acceptance Scenarios**:

1. **Given** 一个 tenderId 关联 2 个 Project（managerId 分别为 585 和 7246，模拟二次招标），**When** 用户访问标讯列表页，**Then** 列表正常加载，该标讯的负责人姓名显示为第一条 Project 的负责人（或为空），不抛异常
2. **Given** 同一 tenderId 有多条 assignedAt 完全相同的分配记录，**When** 用户访问标讯列表页，**Then** 列表正常加载，分配人姓名显示为第一条记录的分配人（或为空），不抛异常
3. **Given** enrichment 阶段发生任何运行时异常（如用户已删除、DB 查询超时），**When** 用户访问列表页，**Then** 列表正常加载，装饰性字段为空，主数据完整返回，后端日志记录 warn 级降级信息
4. **Given** 系统中存在 28 处其他 `toMap` 无 merge function 的隐患点（调研已识别），**When** 对应模块遇到边界数据，**Then** 各模块均不崩溃，对应装饰性字段降级为空

---

### User Story 2 - 后端开发者：新增 toMap 无 merge function 被 CI 拦截 (Priority: P2)

作为后端开发者，当我写新代码时如果不小心用了 2 参数 `Collectors.toMap`（无 merge function），我希望在本地构建和 CI 阶段就被拦截，而不是等到生产环境崩溃才发现。如果我的 key 确实是主键且有唯一约束，我需要能通过豁免机制绕过。

**Why this priority**: P1 修复存量，P2 防新增。没有 P2 的话，P1 修完后又会有人写新的无 merge function 的 toMap，问题反复发生。

**Independent Test**: 在新代码中写一个 2 参数 `Collectors.toMap`，运行 `mvn test -Dtest=ArchitectureTest`，验证被守卫规则拦截；改为 3 参数版本后验证通过。

**Acceptance Scenarios**:

1. **Given** 开发者新增了 2 参数 `Collectors.toMap`（key 非主键），**When** 运行 ArchitectureTest，**Then** 测试失败，错误信息指向具体文件和行号，提示需加 merge function 或证明 key 唯一性
2. **Given** 开发者新增了 2 参数 `Collectors.toMap`（key 是主键且有 DB 唯一约束），**When** 运行 ArchitectureTest，**Then** 测试通过（豁免清单包含此位置）
3. **Given** 开发者尝试推送含新增 2 参数 `toMap` 的代码，**When** 执行 `git push`，**Then** pre-push gate 拦截并提示需修复或加入豁免清单

---

### User Story 3 - 运维/SRE：异常 handler 提供完整诊断信息 (Priority: P3)

作为运维/SRE，当生产环境发生 `IllegalStateException` 或其他 5xx 异常时，我希望 Sentry Dashboard 能看到完整堆栈和请求 Payload，后端日志有 error 级堆栈，而不是只有一行 warn 信息。这样我能快速定位根因，不用反复 grep 日志猜问题。

**Why this priority**: P1/P2 是预防，P3 是观测。本次事件中 handler 只 log.warn 一行，Sentry 看不到，导致定位困难。P3 确保未来类似问题可被快速发现和定位。

**Independent Test**: 触发一个 5xx 异常，验证 Sentry 收到事件、后端日志有 error 级堆栈+Payload、前端收到通用错误信息。

**Acceptance Scenarios**:

1. **Given** 后端抛出 `IllegalStateException`（或任何 5xx 异常），**When** 异常被 GlobalExceptionHandler 捕获，**Then** 后端日志记录 error 级完整堆栈 + 请求 URI + IP + Payload
2. **Given** 同上场景，**When** 异常被捕获，**Then** Sentry 收到异常事件（无 DSN 时为 no-op，不报错）
3. **Given** 同上场景，**When** 前端收到响应，**Then** 响应体是通用错误信息（"系统繁忙，请稍后重试"），不暴露内部实现细节（如 Duplicate key 的具体 key/value）

---

### Edge Cases

- **二次招标场景**：同一 tenderId 关联多 Project 是业务允许的（`ProjectClosureService.rebidProject`），不能加 DB 唯一约束。merge function 取第一条与 `findByTenderId().findFirst()` 语义一致。
- **assignedAt 并列**：`findLatestByTenderIds` 用 `MAX(assignedAt)` 子查询取最新，但同一秒分配两次会并列，子查询返回多条。
- **enrichment 降级后用户体验**：装饰性字段为空时，前端不应显示"加载失败"，应显示"-"或留空。需确认前端对 null/空字段的处理。
- **ArchUnit 豁免清单维护**：31 处存量修复后需从豁免清单删除，清单不能成为永久豁免借口。
- **pre-push gate 性能**：扫描全仓 `toMap` 调用需快速（<2s），避免拖慢推送。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 修复所有 31 处 `Collectors.toMap` 2 参数版本调用，添加 `(a, b) -> a` merge function（或等价策略），确保 key 重复时不抛异常
- **FR-002**: 系统 MUST 在所有装饰性 enrichment 方法（name resolution、display field 补充、批量关联查询）外层加 try-catch 降级，失败时 `log.warn` 并返回原数据，不抛异常
- **FR-003**: GlobalExceptionHandler 的所有 5xx 异常 handler MUST 满足三条诊断标准：`log.error` 打印堆栈 + 打印 Payload + `Sentry.captureException` 上报
- **FR-004**: ArchitectureTest MUST 包含守卫规则，扫描 `Collectors.toMap` 2 参数版本调用，命中即失败（31 处存量作为白名单豁免，逐处修复后从清单删除）
- **FR-005**: pre-push gate MUST 检查新增 2 参数 `Collectors.toMap` 调用，命中即拒绝推送并提示修复方式
- **FR-006**: 修复后的 enrichment 降级行为 MUST 有单元测试覆盖（模拟异常场景验证降级返回原数据）
- **FR-007**: ArchUnit 守卫规则 MUST 有测试覆盖（验证无 merge function 的 toMap 被拦截，有 merge function 的通过）

### Key Entities *(include if feature involves data)*

- **Collectors.toMap 调用点**: 全仓 62 处，其中 31 处无 merge function（隐患），31 处已有 merge function（安全）
- **装饰性 enrichment 方法**: name resolution、display field 补充等不影响主功能的装饰性操作，需识别并加降级
- **GlobalExceptionHandler 5xx handlers**: 所有返回 5xx 状态码的异常处理方法，需对齐诊断标准
- **ArchUnit 守卫规则**: 新增的架构测试规则，扫描 toMap 2 参数版本，带白名单豁免机制
- **pre-push gate 检查脚本**: 新增的推送前检查脚本，扫描新增 toMap 2 参数版本

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 构造 tenderId 关联多 Project 的边界数据，标讯列表页和详情页正常加载，不抛异常（P1 验证）
- **SC-002**: 全仓 `Collectors.toMap` 2 参数版本调用从 31 处降至 0 处（或仅剩白名单豁免的主键 key 调用）（P1 验证）
- **SC-003**: 新增 2 参数 `toMap` 代码在 ArchitectureTest 阶段被拦截，无法进入 main 分支（P2 验证）
- **SC-004**: 触发 5xx 异常时，Sentry 收到事件 + 后端日志有 error 级堆栈 + Payload（P3 验证）
- **SC-005**: 装饰性 enrichment 失败时，主功能（列表加载、详情查看）仍正常返回，装饰性字段为空（P1 验证）
- **SC-006**: 全部测试通过：`mvn test -Dtest=ArchitectureTest` + 受影响模块的单元测试全绿

## Assumptions

- **二次招标是合法业务流程**：同一 tenderId 可关联多 Project，DB 不加唯一约束。merge function 取第一条与 `findByTenderId().findFirst()` 语义一致。
- **装饰性 enrichment 识别标准**：方法名含 `enrich`、`fetchXxxNames`、`fetchXxxMap` 且返回值用于补充显示字段（非业务决策）的，视为装饰性 enrichment。
- **5xx handler 范围**：GlobalExceptionHandler 中所有返回 `INTERNAL_SERVER_ERROR` (500) 或 `CONFLICT` (409) 的 handler 方法。
- **ArchUnit 豁免清单**：31 处存量作为初始白名单，每修一处删除一处，全部修复后清单清空、守卫升级为硬失败。
- **pre-push gate 脚本位置**：放在 `scripts/` 下，与现有 `check-rolecode-direct-calls.mjs` 等检查脚本同目录，接入 `pre-push-gate.sh`。
- **前端对空字段处理**：假设前端对 null/空字段已有兜底显示（"-"或留空），无需前端改动。如验证发现前端依赖字段非空，需补前端兜底。
