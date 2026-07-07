---

description: "Task list for 标讯批量导入异步化与性能优化 + MDC 修复"
---

# Tasks: 标讯批量导入异步化与性能优化 + MDC 修复

**Input**: Design documents from `/specs/031-tender-import-async-perf/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: 包含测试任务（Constitution III: TDD 强制要求）

**Organization**: 按 User Story 分组（US1=异步化 P1, US2=性能优化 P2, US3=MDC 修复 P3）

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，无依赖）
- **[Story]**: 所属 User Story（US1/US2/US3）
- 包含精确文件路径

## Path Conventions

- **Web app**: `backend/src/main/java/com/xiyu/bid/`, `frontend/src/`, `e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 项目基础设施与共享配置

- [ ] T001 预约 Flyway 迁移版本号：运行 `bash scripts/new-migration.sh create_tender_import_task`，记录返回的 V{next} 版本号
- [ ] T002 [P] 运行 `./scripts/who-touches.sh backend/src/main/java/com/xiyu/bid/tender/service/TenderImportService.java backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java backend/src/main/java/com/xiyu/bid/config/TraceFilter.java`，确认无其他 agent 改动冲突
- [ ] T003 [P] 运行 `./scripts/who-touches.sh backend/src/main/java/com/xiyu/bid/config/JwtAuthenticationFilter.java backend/src/main/resources/application.yml src/api/modules/tenders.js src/components/tender/`，确认无前端/配置冲突

**Checkpoint**: 版本号已预约，文件锁已确认无冲突

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 异步任务基础设施（实体 + Repository + 状态机 + 进度服务），所有 User Story 都依赖

**⚠️ CRITICAL**: US1 和 US2 的实现都依赖此阶段完成

### 数据库迁移

- [ ] T004 创建 `tender_import_task` 表迁移脚本：`backend/src/main/resources/db/migration-mysql/V{next}__create_tender_import_task.sql`（参考 `V1022__personnel_batch_import_task.sql` 范式，字段见 [data-model.md](./data-model.md)）
- [ ] T005 [P] 创建回滚脚本：`backend/src/main/resources/db/rollback/migration-mysql/U{next}__drop_tender_import_task.sql`（DROP TABLE）

### 实体与 Repository

- [ ] T006 [P] 创建 `TenderImportTask` 实体：`backend/src/main/java/com/xiyu/bid/tender/entity/TenderImportTask.java`（JPA 实体，字段对应 data-model.md，含 `@Entity`/`@Table`/`@Column`/`@Convert` 用于 error_details JSON）
- [ ] T007 [P] 创建 `TenderImportTaskError` 值对象：`backend/src/main/java/com/xiyu/bid/tender/dto/TenderImportTaskError.java`（`record`，含 rowNumber/field/errorMessage/tenderTitle）
- [ ] T008 [P] 创建 `TenderImportTaskRepository`：`backend/src/main/java/com/xiyu/bid/tender/repository/TenderImportTaskRepository.java`（`extends JpaRepository<TenderImportTask, Long>`，含 `findByTaskId`/`findByStatusAndUpdatedAtBefore` 自定义查询）

### DTO

- [ ] T009 [P] 创建 `TenderImportTaskDTO`（任务创建响应）：`backend/src/main/java/com/xiyu/bid/tender/dto/TenderImportTaskDTO.java`（`record`，含 taskId/status/totalRows/processedRows/successCount/failureCount/message）
- [ ] T010 [P] 创建 `TenderImportProgressDTO`（进度查询响应）：`backend/src/main/java/com/xiyu/bid/tender/dto/TenderImportProgressDTO.java`（`record`，字段见 [data-model.md](./data-model.md)）

### 状态机服务

- [ ] T011 创建 `TenderImportTaskStateService`：`backend/src/main/java/com/xiyu/bid/tender/service/TenderImportTaskStateService.java`（参考 `WarehouseImportTaskStateService`，含 `createTask`/`markProcessing`/`markCompleted`/`markPartialSuccess`/`markFailed`/`failTaskWithThreeLayerFallback`，每次状态变更是独立 `@Transactional`）

### 进度服务

- [ ] T012 创建 `TenderImportProgressService`：`backend/src/main/java/com/xiyu/bid/tender/service/TenderImportProgressService.java`（参考 `PersonnelImportProgressService`，含 `updateProgress(taskId, processed, success, failure)`/`getProgress(taskId)`/`clearProgress(taskId)`，Redis 优先 + DB fallback，`Optional<StringRedisTemplate>` 注入降级）

**Checkpoint**: 数据库表已建，实体/Repository/DTO/状态机/进度服务就绪，可开始 User Story 实现

---

## Phase 3: User Story 3 - 日志正确显示操作用户（MDC 修复）(Priority: P3) 🎯 独立可交付

**Goal**: 修复 `TraceFilter` 在 JWT 认证前写 anonymous 的 MDC 填充时机问题，并新增 `MdcTaskDecorator` 传递 MDC 到 @Async 线程

**Independent Test**: 用 `xiaowang`（roleCode=`bid-Team`）登录并调用任意认证 API，后端日志中 `userId` 字段应为 xiaowang 的用户 ID 而非 `anonymous`

**Why P3 先做**: MDC 修复独立于异步化，且 US1 的异步任务内 MDC 传递依赖 `MdcTaskDecorator`（T019）。先做 US3 可避免 US1 完成后异步日志仍是 anonymous。

### Tests for User Story 3 (TDD - 先写测试，确认 FAIL)

- [ ] T013 [P] [US3] 创建 MDC 契约测试：`backend/src/test/java/com/xiyu/bid/config/TraceFilterMdcContractTest.java`（测试已登录用户请求的 MDC userId 非 anonymous；测试未登录请求仍为 anonymous；使用 `MockMvc` + `MockFilterChain` + 模拟 JWT）
- [ ] T014 [P] [US3] 创建 `MdcTaskDecorator` 单元测试：`backend/src/test/java/com/xiyu/bid/config/MdcTaskDecoratorTest.java`（测试 MDC 上下文从主线程传递到异步线程；测试异步线程结束后 MDC 被清理）

### Implementation for User Story 3

- [ ] T015 [US3] 改造 `TraceFilter.java`：保留 `putUserContext()` 作为 anonymous 兜底，但移除"在 filterChain.doFilter() 之前调用"的注释，明确标注"此处仅为未认证请求兜底，已认证请求的 MDC 由 JwtAuthenticationFilter 刷新"（文件：`backend/src/main/java/com/xiyu/bid/config/TraceFilter.java`，当前 line 52）
- [ ] T016 [US3] 改造 `JwtAuthenticationFilter.java`：在 `SecurityContextHolder.getContext().setAuthentication(authentication)` 之后，立即调用 `MDC.put(TraceConstants.MDC_USER_ID_KEY, String.valueOf(user.getId()))` 和 `MDC.put(TraceConstants.MDC_ROLE_CODE_KEY, effectiveRoleResolver.resolveRoleCode(user))`（文件：`backend/src/main/java/com/xiyu/bid/config/JwtAuthenticationFilter.java`，参考 line 85,98 的 setAuthentication 位置）
- [ ] T017 [US3] 将 `TraceFilter.putUserContext()` 中的 `user.getRoleCode()` 改为 `effectiveRoleResolver.resolveRoleCode(user)`（CO-373 治理，文件：`backend/src/main/java/com/xiyu/bid/config/TraceFilter.java` line 85；移除现有 `// SAFE:` 豁免注释，因改为 EffectiveRoleResolver 后已不需要）
- [ ] T018 [US3] 注入 `EffectiveRoleResolver` 到 `TraceFilter` 构造函数（文件：`backend/src/main/java/com/xiyu/bid/config/TraceFilter.java`，构造函数新增参数）
- [ ] T019 [P] [US3] 创建 `MdcTaskDecorator`：`backend/src/main/java/com/xiyu/bid/config/MdcTaskDecorator.java`（实现 `TaskDecorator`，在 `decorate(Runnable)` 中复制主线程 MDC 的 traceId/userId/roleCode 到异步线程，执行完后 `MDC.clear()`）

**Checkpoint**: US3 完成。已登录用户请求的日志 userId 非 anonymous，MdcTaskDecorator 可供 US1 使用

---

## Phase 4: User Story 1 - 批量导入不再超时，用户看到实时进度 (Priority: P1) 🎯 MVP

**Goal**: 将标讯批量导入改造为 @Async 异步任务，3s 内返回 taskId，后台处理，提供进度查询接口

**Independent Test**: 上传 180 行标讯 Excel，前端在 3 秒内显示"导入任务已创建"，轮询可见实时进度，完成后显示"成功 X 条/失败 Y 条"明细。整个过程不出现 timeout

### Tests for User Story 1 (TDD - 先写测试，确认 FAIL)

- [ ] T020 [P] [US1] 创建异步任务契约测试：`backend/src/test/java/com/xiyu/bid/tender/TenderImportAsyncContractTest.java`（测试：① POST /import 返回 202 + taskId；② GET /progress 返回进度；③ @Async 实际在新线程执行；④ 异步线程内 MDC userId 与主线程一致；⑤ 任务失败时 status=FAILED 且 error_details 正确）
- [ ] T021 [P] [US1] 创建 `TenderImportAppService` 单元测试：`backend/src/test/java/com/xiyu/bid/tender/service/TenderImportAppServiceTest.java`（测试 triggerImport 创建任务并触发异步；executeImportAsync 处理行循环并更新进度；异常时 failImportTask 三层降级）
- [ ] T022 [P] [US1] 创建 `TenderImportProgressService` 单元测试：`backend/src/test/java/com/xiyu/bid/tender/service/TenderImportProgressServiceTest.java`（测试 Redis 优先查询；Redis 未命中查 DB；clearProgress 清理 Redis）

### Implementation for User Story 1

#### 后端 - 异步编排服务

- [ ] T023 [US1] 创建 `TenderImportAppService`：`backend/src/main/java/com/xiyu/bid/tender/service/TenderImportAppService.java`（参考 `ImportPersonnelAppService`，含 3 个方法：
  - `triggerImport(MultipartFile, Long userId)`：同步阶段，校验文件 + 创建 task 记录 + 读取 byte[] + 调 `executeImportAsync`
  - `@Async("tenderImportExecutor") executeImportAsync(Long taskId, byte[] fileBytes, Long userId)`：异步阶段，Excel 解析 + 循环 createTender + 进度更新 + 状态机推进 + try-catch Throwable + finally failImportTask
  - `getProgress(String taskId, Long userId)`：校验任务归属 + 调 progressService.getProgress
  - 注意：从 Controller 跨类调用 triggerImport，避免 @Async 自调用失效）

#### 后端 - 线程池配置

- [ ] T024 [US1] 改造 `AsyncConfig.java`：新增 `tenderImportExecutor` Bean（core=2, max=4, queue=50, CallerRunsPolicy, prefix=`tender-import-`），并在所有 4 个 executor + 新 executor 上调用 `executor.setTaskDecorator(new MdcTaskDecorator())`（文件：`backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java`）

#### 后端 - Controller 改造

- [ ] T025 [US1] 改造 `TenderController.importTenders`：同步阶段调用 `tenderImportAppService.triggerImport(file, userId)`，返回 `202 Accepted` + `TenderImportTaskDTO`（文件：`backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`，当前 line 166-194）
- [ ] T026 [US1] 新增 `GET /api/tenders/import/{taskId}/progress` 端点：在 `TenderController.java` 新增 `getImportProgress(@PathVariable String taskId)` 方法，调用 `tenderImportAppService.getProgress(taskId, currentUserId)`，返回 `TenderImportProgressDTO`（文件：`backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`）

#### 后端 - 卡死任务恢复

- [ ] T027 [US1] 创建 `TenderImportTaskRecoveryRunner`：`backend/src/main/java/com/xiyu/bid/tender/service/TenderImportTaskRecoveryRunner.java`（实现 `ApplicationRunner` 或监听 `ApplicationReadyEvent`，启动时扫描 `status=PROCESSING AND updated_at < now()-30min` 的任务，标记 FAILED，清 Redis）

#### 后端 - 现有测试适配

- [ ] T028 [US1] 改造 `TenderImportControllerTest`：适配新的 202 响应结构（taskId/status/message），原有同步响应断言改为异步响应断言（文件：`backend/src/test/java/com/xiyu/bid/tender/controller/TenderImportControllerTest.java`）

#### 前端 - API 层

- [ ] T029 [P] [US1] 改造 `src/api/modules/tenders.js` 的 `bulkImport`：适配新响应结构（taskId/status/message），timeout 从 120000 改为 30000（同步阶段只需 30s）（文件：`src/api/modules/tenders.js`，当前 line 92-99）
- [ ] T030 [P] [US1] 新增 `getImportProgress(taskId)` 方法：在 `src/api/modules/tenders.js` 新增，调用 `GET /api/tenders/import/{taskId}/progress`，timeout=10000

#### 前端 - UI 层

- [ ] T031 [US1] 改造 `BulkImportDialog.vue`：上传后显示"导入任务已创建" + 进度条（已处理 N/总行数、成功/失败计数），每 2 秒轮询 `getImportProgress`，状态为 COMPLETED/PARTIAL_SUCCESS/FAILED 时停止轮询并显示结果（含失败行明细表格）（文件：`src/components/tender/BulkImportDialog.vue`）
- [ ] T032 [US1] 改造 `TenderList.vue` 的导入入口：适配新的导入对话框交互（文件：`src/views/tender/TenderList.vue`）

#### 前端 - 测试

- [ ] T033 [P] [US1] 创建 `BulkImportDialog` 单元测试：`frontend/tests/unit/components/tender/BulkImportDialog.spec.js`（测试进度轮询、状态显示、失败明细展示）

#### E2E

- [ ] T034 [US1] 创建 E2E 测试：`e2e/tender-import-async.spec.ts`（全流程：登录 → 上传 Excel → 验证 taskId 返回 → 轮询进度 → 验证完成状态 + 失败明细）

**Checkpoint**: US1 完成。用户上传 Excel 后 3s 内看到"导入进行中"，可实时查看进度，不再出现 timeout

---

## Phase 5: User Story 2 - 单次导入 500 行在 60s 内完成（性能优化）(Priority: P2)

**Goal**: 通过 CRM 批次内缓存 + Hibernate batch_size 优化性能，500 行端到端 <60s

**Independent Test**: 上传 500 行 Excel（含已存在公司名和不存在公司名混合），端到端耗时 <60s，无 timeout，数据全部正确入库

**Why P2 后做**: 性能优化依赖 US1 的异步基础设施（CRM 缓存需要在异步任务内构建）

### Tests for User Story 2 (TDD - 先写测试，确认 FAIL)

- [ ] T035 [P] [US2] 创建 `CachedCrmLookupService` 单元测试：`backend/src/test/java/com/xiyu/bid/tender/crm/CachedCrmLookupServiceTest.java`（测试相同公司名只查一次 CRM；测试 Optional.empty 缓存；测试 CRM 失败时返回 Optional.empty 不抛异常）

### Implementation for User Story 2

- [ ] T036 [US2] 创建 `CachedCrmLookupService`：`backend/src/main/java/com/xiyu/bid/tender/crm/CachedCrmLookupService.java`（包装 `CrmCompanySearchService` 和 `CrmCustomerManagerLookupService`，使用 `Map<String, Optional<CompanySearchResult>>` + `computeIfAbsent` 实现批次内缓存；CRM 调用失败时 catch 并缓存 `Optional.empty`）
- [ ] T037 [US2] 改造 `TenderImportAppService.executeImportAsync`：在循环前构建 `CachedCrmLookupService` 实例，通过 `TenderAutoAssignmentService` 或直接传入（注意：spec Out of Scope 不改 createTender 内部，需通过 ThreadLocal 或参数传递缓存；最简方案是在 executeImportAsync 内构建缓存 Map，并临时替换 CrmCompanySearchService 的行为 — 但这违反不改 createTender 的约束。**实际方案**：在 `TenderImportAppService` 内预查所有公司名，构建 `Map<String, CompanyDTO>`，然后循环 createTender 时通过 `TenderRequest` 的 `purchaserName` 查 Map 预填 customerCompanyId 字段，跳过 createTender 内的 CRM 调用。需在 plan.md 补充说明此设计调整）
- [ ] T038 [US2] 开启 Hibernate 批量插入配置：在 `backend/src/main/resources/application.yml` 的 `spring.jpa.properties.hibernate` 下新增 `jdbc.batch_size: 50`、`order_inserts: true`、`order_updates: true`（文件：`backend/src/main/resources/application.yml`，当前 line 19-27）
- [ ] T039 [US2] DB_URL 加 `rewriteBatchedStatements=true`：在 `backend/src/main/resources/application.yml` 的 `spring.datasource.url` 后追加 `&rewriteBatchedStatements=true`（文件：`backend/src/main/resources/application.yml`，当前 line 14；注意：仅 dev profile，生产由环境变量注入）
- [ ] T040 [US2] 性能基准测试：手动上传 500 行 Excel，记录端到端耗时，验证 <60s（使用 [quickstart.md](./quickstart.md) 的验证步骤 3）

**Checkpoint**: US2 完成。500 行导入 <60s，CRM 调用次数从 360 次降至去重后 N 次

---

## Phase 6: Nginx 兜底配置 (FR-018)

**Purpose**: 异步化上线前的临时防护，避免异步化未上线期间仍出现 504

- [ ] T041 [P] 准备 Nginx 配置 patch：在 `docs/release/nginx-tender-import-timeout.md` 记录需要在 `/etc/nginx/conf.d/xiyu-bid.conf` 的 `location /api/` 块中新增的配置（`proxy_read_timeout 180s;` `proxy_send_timeout 180s;` `proxy_connect_timeout 60s;`），由用户亲自登服务器部署

**Checkpoint**: Nginx 配置 patch 已准备，等用户部署

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 跨 Story 的收尾工作

- [ ] T042 [P] 运行架构测试：`cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`，确认全绿
- [ ] T043 [P] 运行前端门禁：`npm run check:front-data-boundaries && npm run check:doc-governance && npm run check:line-budgets && npm run build`
- [ ] T044 [P] 运行后端全量测试：`cd backend && mvn test`
- [ ] T045 [P] 运行 E2E：`npm run test:e2e -- --grep "tender-import-async"`
- [ ] T046 [P] 运行 `npm run ci:pre-pr`（21 道门禁全绿）
- [ ] T047 更新 `CLAUDE.md` 的 SPECKIT START/END 块：将 `specs/031-tender-import-async-perf/plan.md` 加入活跃 feature 列表
- [ ] T048 [P] 在 `docs/lessons/lessons-learned.md` 补充本次根因和修复方案（§23 全链路日志排查 SOP 的新案例）
- [ ] T049 提交 PR：使用 `pr-create.sh`，PR 描述包含 spec/plan/tasks 链接 + 18 项 FR 对照表 + 验证证据
- [ ] T050 [P] 通知用户亲自部署 Nginx 配置 patch（T041）+ 后端 + 前端

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational: 表+实体+状态机+进度服务)  ← BLOCKS US1/US2
    ↓
Phase 3 (US3: MDC 修复)  ← 独立可交付，但 MdcTaskDecorator 是 US1 的依赖
    ↓
Phase 4 (US1: 异步化 MVP)  ← 依赖 Phase 2 + T019 (MdcTaskDecorator)
    ↓
Phase 5 (US2: 性能优化)  ← 依赖 Phase 4 (异步基础设施)
    ↓
Phase 6 (Nginx 兜底)  ← 独立，可随时准备
    ↓
Phase 7 (Polish: 测试+PR)
```

### User Story Dependencies

- **US3 (MDC 修复)**: 可在 Phase 2 完成后立即开始，不依赖 US1/US2
- **US1 (异步化)**: 依赖 Phase 2 + T019 (MdcTaskDecorator from US3)
- **US2 (性能优化)**: 依赖 US1 的异步基础设施（executeImportAsync 方法）

### Within Each User Story

- Tests (TDD) → Models → Services → Endpoints → Integration
- 每个任务完成后 commit，保持原子提交

### Parallel Opportunities

- Phase 2: T004-T010 全部可并行（不同文件）
- Phase 3 (US3): T013/T014 测试可并行；T015-T018 串行（同一文件依赖）
- Phase 4 (US1): T020-T022 测试可并行；T029/T030 前端 API 可并行；T033 前端测试可与后端并行
- Phase 5 (US2): T035 测试独立
- Phase 7: T042-T046 门禁可并行；T048 文档独立

---

## Parallel Example: Phase 2 (Foundational)

```bash
# 并行启动所有独立文件创建：
Task: "T004 创建迁移脚本 V{next}__create_tender_import_task.sql"
Task: "T005 创建回滚脚本 U{next}__drop_tender_import_task.sql"
Task: "T006 创建 TenderImportTask 实体"
Task: "T007 创建 TenderImportTaskError 值对象"
Task: "T008 创建 TenderImportTaskRepository"
Task: "T009 创建 TenderImportTaskDTO"
Task: "T010 创建 TenderImportProgressDTO"
```

## Parallel Example: Phase 3 (US3 Tests)

```bash
# 并行启动 US3 的两个测试：
Task: "T013 创建 TraceFilterMdcContractTest"
Task: "T014 创建 MdcTaskDecoratorTest"
```

---

## Implementation Strategy

### MVP First (US3 + US1)

1. Complete Phase 1: Setup（预约版本号 + who-touches）
2. Complete Phase 2: Foundational（表 + 实体 + 状态机 + 进度服务）
3. Complete Phase 3: US3 MDC 修复（独立可交付，且提供 MdcTaskDecorator 给 US1）
4. Complete Phase 4: US1 异步化（核心 MVP，解决用户报告的 timeout 痛点）
5. **STOP and VALIDATE**: 验证 US3 + US1 独立工作
6. 可选：先部署 US3 + US1，解决生产 timeout 问题

### Incremental Delivery

1. Setup + Foundational → 基础设施就绪
2. US3 MDC 修复 → 日志可追溯（独立价值）
3. US1 异步化 → 解决 timeout 痛点（核心 MVP）
4. US2 性能优化 → 500 行 <60s（体验优化）
5. Nginx 兜底 → 临时防护（可随时部署）
6. Polish → 全量测试 + PR

### Parallel Team Strategy

- Developer A: Phase 2 Foundational（表 + 实体 + 服务）
- Developer B: Phase 3 US3 MDC 修复（独立于 A）
- Phase 2 完成后：
  - Developer A: Phase 4 US1 后端
  - Developer B: Phase 4 US1 前端
- Phase 4 完成后：
  - Developer A: Phase 5 US2 性能优化
  - Developer B: Phase 7 Polish 测试

---

## Notes

- [P] 任务 = 不同文件，无依赖，可并行
- [Story] 标签映射到 spec.md 的 User Story
- 每个 User Story 应独立可完成和可测试
- TDD：先写测试，确认 FAIL，再实现
- 每个任务或逻辑组完成后 commit（原子提交 + 测试证据）
- 任意 Checkpoint 可停下来独立验证 Story
- 避免：模糊任务、同文件冲突、跨 Story 依赖破坏独立性
- T037 有设计调整：需在实现时确认 CRM 缓存的注入方式（spec Out of Scope 不改 createTender 内部，需通过预查 + TenderRequest 字段预填实现）
