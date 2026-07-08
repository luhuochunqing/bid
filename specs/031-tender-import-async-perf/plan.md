# Implementation Plan: 标讯批量导入异步化与性能优化 + MDC 用户上下文修复

**Branch**: `agent/trae/tender-import-async-perf` | **Date**: 2026-07-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/031-tender-import-async-perf/spec.md`

## Summary

将标讯批量导入从同步 `@Transactional`（103.5s/180 行）改造为 **@Async 异步任务 + 数据库持久化 + Redis 进度缓存 + 前端轮询** 范式（参考 `ImportPersonnelAppService`），3s 内返回 taskId，后台处理并提供进度查询。同时通过 **CRM 批次内缓存 + Hibernate batch_size** 优化性能，目标 500 行 <60s。另修复 `TraceFilter` 在 JWT 认证前写 anonymous 的 MDC 填充时机问题，并新增 `MdcTaskDecorator` 传递 MDC 到 @Async 线程。

## Technical Context

**Language/Version**: Java 21（后端）+ ECMAScript 2022（前端 Vue 3）

**Primary Dependencies**:
- 后端：Spring Boot 3.2.5 + Spring Data JPA + MySQL 8.0 + Flyway + POI（XSSFWorkbook）+ Redis（StringRedisTemplate）
- 前端：Vue 3 + Vite 5 + Element Plus + axios
- 测试：JUnit 5 + Mockito + MockMvc + Playwright

**Storage**: MySQL 8.0（`xiyu_bid_main`）+ Redis（DB 0）

**Testing**:
- 后端：`mvn test -Dtest=<TestClass>`（JUnit 5 + Mockito + MockMvc + Testcontainers）
- 前端：`npm run test:unit`（Vitest）+ `npm run test:e2e`（Playwright）
- 架构：`mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`

**Target Platform**: Linux server（生产 `jetty@172.16.38.78`）+ macOS 开发（主工作区 `/Users/user/xiyu/worktrees/trae`）

**Project Type**: Web application（前后端分离）

**Performance Goals**:
- 同步阶段（接收文件 → 返回 taskId）：<3s
- 异步处理 500 行端到端：<60s
- 进度查询接口响应：<200ms
- 进度更新延迟（后端处理 → 前端可见）：≤2s

**Constraints**:
- 不引入 RabbitMQ/Kafka 等消息队列（Constitution VIII: Boring Proven Patterns）
- 不重构 `TenderCommandService.createTender` 内部逻辑（spec Out of Scope）
- 单文件 ≤300 行（Constitution IV: Split-First & Simplicity）
- 不破坏现有 `@Idempotent` 语义（spec Assumptions）
- CO-373：roleCode 解析走 `EffectiveRoleResolver`，禁止直调 `User.getRoleCode()`

**Scale/Scope**: 单次导入 ≤500 行 Excel；并发用户量低（投标专员/组长，<10 并发）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|---|---|---|
| I. FP-Java Architecture | ✅ PASS | 新增 `TenderImportAppService`（应用服务编排）+ `TenderImportTask`（实体）+ `TenderImportProgressDTO`（DTO）；纯核心校验逻辑（`TenderImportValidator`）与应用服务分离；不依赖框架 API |
| II. Real-API Only | ✅ PASS | CRM 调用走真实 `CrmCompanySearchService`；测试用 MockMvc + Testcontainers MySQL，不引入 Mock 模式 |
| III. Test-Driven Development | ✅ PASS | 计划包含契约测试（`TenderImportAsyncContractTest`）、单元测试（`TenderImportAppServiceTest`）、E2E（`tender-import-async.spec.ts`）|
| IV. Split-First & Simplicity | ✅ PASS | 新增文件均 <200 行；`TenderImportAppService` 拆分为 `triggerImport` + `executeImportAsync` + `getProgress`；进度更新逻辑下沉到 `TenderImportProgressService` |
| V. OSS Integration | N/A | 本 feature 不涉及 OSS 集成 |
| VI. Authorization Unification | ✅ PASS | Controller 使用 `@PreAuthorize("isAuthenticated()")`；权限键复用现有标讯模块权限 |
| VII. Defensive Collection & Graceful Degradation | ✅ PASS | CRM 缓存使用 `Map<String, Optional<...>>`（merge function 不适用，使用 computeIfAbsent）；CRM 失败降级（已有 `tryAutoAssignFromCrm` catch）；异步任务 try-catch 全兜底 |
| VIII. Boring Proven Patterns | ✅ PASS | 完全复用 `ImportPersonnelAppService` + `PersonnelImportProgressService` 范式；@Async + DB + Redis 轮询；零新依赖 |
| Performance Constraints | ✅ PASS | 异步导出 >30s 改异步（本 feature 即为此约束的实现）；分页/导出限制不涉及 |
| Security & Access Control | ✅ PASS | taskId 使用 UUID 防猜测；进度查询接口校验任务归属（userId 匹配）；JWT 认证不变 |
| Development Workflow | ✅ PASS | 早操三连已执行；who-touches 待跑；Flyway 迁移规范遵循；WIP 分支已建 |

**Gate Result**: PASS — 无 Constitution 违规，无需 Complexity Tracking 表。

## Project Structure

### Documentation (this feature)

```text
specs/031-tender-import-async-perf/
├── plan.md              # This file
├── research.md          # Phase 0 output（调研报告，已内联到本 plan 的 Research 部分）
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── tender-import-api.md       # REST API 契约
│   └── tender-import-task-states.md # 状态机契约
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/
│   ├── tender/
│   │   ├── controller/
│   │   │   └── TenderController.java              # 改造 importTenders + 新增 getImportProgress
│   │   ├── service/
│   │   │   ├── TenderImportService.java           # 保留：Excel 解析 + 校验逻辑（纯核心）
│   │   │   ├── TenderImportAppService.java        # 新增：@Async 异步编排
│   │   │   ├── TenderImportProgressService.java   # 新增：Redis + DB 进度查询
│   │   │   └── TenderImportTaskStateService.java  # 新增：任务状态机（参考 WarehouseImportTaskStateService）
│   │   ├── entity/
│   │   │   └── TenderImportTask.java              # 新增：异步任务实体
│   │   ├── repository/
│   │   │   └── TenderImportTaskRepository.java    # 新增：JpaRepository
│   │   ├── dto/
│   │   │   ├── TenderImportTaskDTO.java           # 新增：任务创建响应 DTO
│   │   │   └── TenderImportProgressDTO.java       # 新增：进度查询响应 DTO
│   │   └── crm/
│   │       └── CachedCrmLookupService.java        # 新增：批次内 CRM 缓存（P1 性能优化）
│   ├── config/
│   │   ├── AsyncConfig.java                       # 改造：新增 tenderImportExecutor + MdcTaskDecorator
│   │   ├── TraceFilter.java                       # 改造：移除 putUserContext() 到 JwtAuthenticationFilter 之后
│   │   ├── MdcTaskDecorator.java                  # 新增：@Async 线程 MDC 传递
│   │   └── JwtAuthenticationFilter.java           # 改造：认证成功后刷新 MDC
│   └── security/
│       └── EffectiveRoleResolver.java             # 复用：roleCode 解析（CO-373）
├── src/main/resources/
│   ├── db/migration-mysql/
│   │   └── V{next}__create_tender_import_task.sql # 新增：建表迁移
│   └── application.yml                            # 改造：开启 Hibernate batch_size
└── src/test/java/com/xiyu/bid/
    └── tender/
        ├── TenderImportAppServiceTest.java        # 新增：异步任务单元测试
        ├── TenderImportProgressServiceTest.java   # 新增：进度服务单元测试
        ├── TenderImportAsyncContractTest.java     # 新增：契约测试（@Async 生效、MDC 传递、事务边界）
        └── TenderImportControllerTest.java        # 改造：现有测试适配异步响应

frontend/
├── src/
│   ├── api/modules/tenders.js                     # 改造：bulkImport 适配新响应 + 新增 getImportProgress
│   ├── components/tender/
│   │   └── BulkImportDialog.vue                   # 改造：进度轮询 UI
│   └── views/tender/
│       └── TenderList.vue                         # 改造：导入入口适配
└── tests/unit/components/tender/
    └── BulkImportDialog.spec.js                   # 新增：进度轮询单测

e2e/
└── tender-import-async.spec.ts                    # 新增：E2E 全流程
```

**Structure Decision**: Web application（前后端分离）。后端按 FP-Java 分层：`controller`（应用服务外壳）→ `service`（编排）→ `entity/repository`（数据访问）→ `dto`（数据传输）。纯核心校验逻辑保留在 `TenderImportService`（Excel 解析、表头校验、字段校验），异步编排下沉到 `TenderImportAppService`。

## Phase 0: Research

### R-001: Spring @Async 配置现状与陷阱

**Decision**: 新增 `tenderImportExecutor` 专用线程池（core=2, max=4, queue=50, CallerRunsPolicy），参考 `importExportExecutor` 模式。

**Rationale**:
- 项目已有 4 个专用线程池（[AsyncConfig.java:24-79](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java#L24-L79)），模式成熟
- 不复用 `importExportExecutor`（人员证书专用，避免抢线程）
- 已有 9+ 个 @Async 案例，最相似的是 [ImportPersonnelAppService.java:49](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L49)

**Alternatives considered**:
- 复用 `importExportExecutor`：语义混淆，且人员导入与标讯导入可能同时触发
- 默认 `SimpleAsyncTaskExecutor`：每次创建新线程，无池化，不适合生产
- RabbitMQ/Kafka：spec Out of Scope，过度工程化

### R-002: @Async 自调用陷阱

**Decision**: 从 `TenderController.importTenders` 同步阶段调用 `TenderImportAppService.triggerImport`，`triggerImport` 内部调 `@Async executeImportAsync`（跨类调用，代理生效）。

**Rationale**:
- Spring AOP PROXY 模式下同类内调用绕过代理（陷阱 1）
- [ImportPersonnelAppService.java:49](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L49) 是从 Controller 外部调用，代理生效
- 不使用 `@Lazy @Autowired self` 自注入（反模式）

### R-003: MultipartFile 在 @Async 失效

**Decision**: Controller 同步阶段读取 `MultipartFile` 为 `byte[]`，传给 @Async 方法。

**Rationale**:
- HTTP 请求结束后 Tomcat 立即清理临时文件（陷阱 2）
- [ImportPersonnelAppService.java:50](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L50) 入参就是 `byte[]`

### R-004: 异步任务持久化方案

**Decision**: 新建 `tender_import_task` 表，schema 参考 `personnel_import_task`（[V1022__personnel_batch_import_task.sql](file:///Users/user/xiyu/worktrees/trae/backend/src/main/resources/db/migration-mysql/V1022__personnel_batch_import_task.sql)）。

**Rationale**:
- 项目已有 3 个 import_task 表范式，`personnel_import_task` 最完整（5 状态机 + JSON error_details + 完成时间戳）
- spec Key Entities 已明确字段需求
- 不复用现有表：业务域不同，避免耦合

### R-005: 进度查询方案

**Decision**: 轮询（前端每 2s 查 `GET /api/tenders/import/{taskId}/progress`）+ Redis 缓存 + DB fallback，参考 [PersonnelImportProgressService.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/PersonnelImportProgressService.java)。

**Rationale**:
- 项目无 SSE/WebSocket（0 匹配）
- Constitution VIII: Boring Proven Patterns
- Redis 不可用时降级到 DB（已有 `Optional<StringRedisTemplate>` 注入范式）
- spec Assumptions 第 4 条已明确

### R-006: @Transactional 与 @Async 的事务边界

**Decision**:
- @Async 方法 `executeImportAsync` **不加** `@Transactional`（避免跨整个异步执行的长事务）
- 保留 `TenderCommandService.createTender` 的类级 `@Transactional`（每行独立事务）
- **语义变更**：从"全量回滚"改为"部分成功"（单行失败记录 error_details，继续处理）

**Rationale**:
- 当前 103.5s 长事务是性能瓶颈（180 行在一个事务里）
- spec Edge Case "失败行明细"明确要求记录失败行而非回滚
- spec FR-003 要求返回"失败明细"
- 单行独立事务：失败行回滚，成功行保留，符合用户预期（部分导入成功）

**Alternatives considered**:
- 批次分片事务（每 50 行一个事务）：复杂度高，500 行规模不需要
- 整批一个事务：当前模式，103.5s 长事务

### R-007: CRM 批次内缓存

**Decision**: 在 `TenderImportAppService.executeImportAsync` 内构建 `Map<String, Optional<CompanySearchResult>>` 本地缓存，通过 `CachedCrmLookupService` 包装。

**Rationale**:
- CRM 接口无批量方法（[CrmCompanySearchService.java:54](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/crm/application/CrmCompanySearchService.java#L54) 只有 `searchByName(String)`）
- 同一 Excel 中重复公司名（同一招标主体多个标讯）只调一次 CRM
- spec FR-008 明确要求
- `Optional` 包装：缓存"未找到"结果，避免重复查询空结果

### R-008: 数据库批量 INSERT

**Decision**: 开启 Hibernate `batch_size=50` + `order_inserts=true` + DB_URL 加 `rewriteBatchedStatements=true`。

**Rationale**:
- spec Out of Scope 限制不改 `createTender` 内部
- [application.yml:24-27](file:///Users/user/xiyu/worktrees/trae/backend/src/main/resources/application.yml#L24-L27) 当前未配置 batch_size
- 开启后 Hibernate 底层自动批量，无需改 `createTender` 的 `save(tender)` 调用
- 主要性能提升来自 CRM 缓存（360 次 → 去重后 N 次）

### R-009: MDC 修复方案

**Decision**:
1. `TraceFilter` 保留 `putUserContext()` 作为 anonymous 兜底（未认证请求）
2. `JwtAuthenticationFilter` 认证成功后立即刷新 MDC（覆盖 anonymous）
3. 新增 `MdcTaskDecorator` 传递 MDC 到 @Async 线程
4. roleCode 走 `EffectiveRoleResolver.resolveRoleCode(user)`（CO-373）

**Rationale**:
- 根因：[TraceFilter.java:52](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/config/TraceFilter.java#L52) 在 `filterChain.doFilter()` 之前调 `putUserContext()`，此时 `JwtAuthenticationFilter` 未执行
- 方案 A（推荐）：在 JwtAuthenticationFilter 认证成功后刷新 MDC，修改面小
- MdcTaskDecorator：@Async 线程不自动继承 ThreadLocal，必须显式传递（spec FR-007/FR-014）

### R-010: 异步任务异常兜底

**Decision**: `executeImportAsync` 内 try-catch `Throwable`（含 `Error`），finally 中调 `failImportTask` 三层降级。

**Rationale**:
- Spring `SimpleAsyncUncaughtExceptionHandler` 只 log 不传播，任务会卡在 PROCESSING（陷阱 3）
- [ImportPersonnelAppService.java:78-85](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L78-L85) 注释强调 catch 范围
- 三层降级（save → updateStatus → clearProgress）参考 [ImportPersonnelAppService.java:163-198](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L163-L198)

### R-011: 服务重启任务卡死

**Decision**: 启动时扫描 `tender_import_task` 中 `status=PROCESSING` 且 `updated_at < now()-30min` 的任务，标记为 `FAILED`（error_details=`"服务重启导致任务中断"`）。

**Rationale**:
- spec Edge Case "导入过程中服务重启"
- 参考 `TenderTaskWorkerService` 的启动扫描模式

### R-012: 同步阶段超 3s 风险

**Decision**: 同步阶段只做：`validateFile` + 创建 task 记录 + 读取 byte[]。Excel 解析移到异步阶段。

**Rationale**:
- XSSFWorkbook 解析 180 行可能耗时（陷阱 14）
- 参考 `ImportPersonnelAppService` 模式：同步阶段只创建任务，解析在异步阶段

## Phase 1: Design & Contracts

### Data Model

详见 [data-model.md](./data-model.md)

### Contracts

详见 [contracts/tender-import-api.md](./contracts/tender-import-api.md) 和 [contracts/tender-import-task-states.md](./contracts/tender-import-task-states.md)

### Quickstart

详见 [quickstart.md](./quickstart.md)

### Post-Design Constitution Re-check

| Principle | Status | Notes |
|---|---|---|
| I. FP-Java Architecture | ✅ PASS | `TenderImportValidator`（纯核心校验）与 `TenderImportAppService`（应用编排）分离 |
| IV. Split-First & Simplicity | ✅ PASS | 所有新增文件预计 <200 行；`TenderImportAppService` 拆分为 3 个方法 |
| VII. Defensive Collection | ✅ PASS | CRM 缓存使用 `computeIfAbsent`；error_details 使用 `List.copyOf`；异步 try-catch 全兜底 |
| VIII. Boring Proven Patterns | ✅ PASS | 完全复用 `ImportPersonnelAppService` + `PersonnelImportProgressService` 范式 |

**Gate Result**: PASS — 设计阶段无新增违规。

## Implementation Phases (Preview for tasks.md)

### Phase A: MDC 修复（FR-013~FR-017）
独立可交付，不依赖异步化。先修复 TraceFilter + JwtAuthenticationFilter + MdcTaskDecorator。

### Phase B: 异步任务基础设施（FR-001~FR-007）
建表 + 实体 + Repository + 状态机服务 + 进度服务 + AppService + Controller 改造。

### Phase C: 性能优化（FR-008~FR-012）
CRM 缓存 + Hibernate batch_size + DB_URL rewriteBatchedStatements。

### Phase D: Nginx 兜底（FR-018）
配置 patch，由用户亲自部署。

### Phase E: 测试与验收
契约测试 + 单元测试 + E2E + 生产日志验证。

---

**Plan Status**: Phase 0 + Phase 1 完成，待 `/speckit-tasks` 生成 tasks.md。
