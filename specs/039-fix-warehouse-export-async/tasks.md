# Tasks: 修复仓库全量合订本导出任务创建失败

**Feature**: 039-fix-warehouse-export-async
**Branch**: `agent/claude/fix-warehouse-export-async`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Implementation Strategy

**MVP 范围**: 仅 User Story 1（bid_admin 导出仓库全量合订本）。这是单一 P1 故事，修复后核心功能即可恢复。

**TDD 流程**: Red（先写测试，验证 @Async 生效 + 降级语义不回归）→ Green（提取 WarehouseExportAsyncExecutor）→ Refactor（清理 AppService）。

**关键验证点**:
1. 异步线程名以 `warehouse-export-` 开头（@Async 生效）
2. Word 合订本异常时任务仍 COMPLETED（降级不回归）
3. HTTP 请求快速返回（不再同步执行 PDF 渲染）

---

## Phase 1: Setup

- [ ] T001 确认当前分支 `agent/claude/fix-warehouse-export-async` 基于最新 origin/main，工作区干净

---

## Phase 2: Foundational（TDD Red - 先写测试）

> 先写测试，预期 FAIL（因为 WarehouseExportAsyncExecutor 还不存在），驱动实现。

- [ ] T002 [P] 创建测试 `WarehouseExportAsyncExecutorTest.java` 在 `backend/src/test/java/com/xiyu/bid/warehouse/application/`，覆盖以下用例：
  - `executeExport` 调用后任务状态 PENDING → PROCESSING → COMPLETED
  - `executeExportByIds` 调用后任务状态转移
  - Word 合订本 buildBundle 抛 RuntimeException 时降级为 null，任务仍 COMPLETED（降级语义不回归）
  - doExport 内部异常时任务状态 FAILED + failureReason 填充
  - markProcessing/completeTask/failTask 使用 REQUIRES_NEW 传播

- [ ] T003 [P] 创建测试 `WarehouseExportAppServiceTest.java` 在 `backend/src/test/java/com/xiyu/bid/warehouse/application/`，覆盖以下用例：
  - `export()` 调用 createTask 保存 PENDING 任务后，委托调用 `asyncExecutor.executeExport(taskId, ...)`
  - `exportByIds()` 调用 createTask 后，委托调用 `asyncExecutor.executeExportByIds(taskId, ...)`
  - `export()` 不再直接调用 `this.executeExportAsync`（验证委托模式）
  - createTask 在 @Transactional 事务中执行

---

## Phase 3: User Story 1 - 投标管理员导出仓库全量合订本 [US1]

> 实现 Green：创建 WarehouseExportAsyncExecutor + 重构 AppService，让测试通过。

- [ ] T004 [US1] 创建 `WarehouseExportAsyncExecutor.java` 在 `backend/src/main/java/com/xiyu/bid/warehouse/application/`：
  - `@Component` + 构造器注入所有依赖（从 AppService 迁移）
  - `@Async("warehouseExportExecutor")` 标注 `executeExport` 和 `executeExportByIds`
  - 迁移 `doExport` / `loadAttachments` / `loadUsernames` / `markProcessing` / `completeTask` / `failTask` 等私有方法
  - `markProcessing`/`completeTask`/`failTask` 标注 `@Transactional(propagation = Propagation.REQUIRES_NEW)`
  - 保留 Word 合订本 try-catch 降级语义（RuntimeException → log.warn + wordBytes=null）
  - 保留 `FILE_TTL = Duration.ofHours(24)` 常量

- [ ] T005 [US1] 重构 `WarehouseExportAppService.java` 在 `backend/src/main/java/com/xiyu/bid/warehouse/application/`：
  - 删除 `executeExportAsync` / `executeExportByIdsAsync` / `doExport` 等 @Async 和私有方法
  - 删除已迁移到 AsyncExecutor 的依赖（WordBundleBuilder/ZipBuilder/ExcelWriter 等）
  - 保留 `export()` / `exportByIds()` 方法，改为：`@Transactional` createTask → `asyncExecutor.executeExport(taskId, ...)` 委托调用
  - 注入 `WarehouseExportAsyncExecutor`（构造器注入）
  - 保留 `createTask` 方法（@Transactional 创建 PENDING 任务记录）
  - 保留 `listExportTasks` / `getExportTaskStatus` / `getExportFile` 等查询方法

- [ ] T006 [US1] 运行后端单元测试验证 Green：
  ```bash
  cd backend && mvn test -Dtest=WarehouseExportAsyncExecutorTest,WarehouseExportAppServiceTest
  ```
  预期：全部通过

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T007 [P] 运行架构测试验证 FP-Java 分层不违规：
  ```bash
  cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
  ```
  预期：全绿，新增 WarehouseExportAsyncExecutor 在 application/ 包符合分层

- [ ] T008 [P] 运行 Controller 契约测试验证 API 不回归：
  ```bash
  cd backend && mvn test -Dtest=WarehouseExportControllerTest
  ```
  预期：全绿

- [ ] T009 [P] 运行后端全量测试验证无回归：
  ```bash
  cd backend && mvn test
  ```
  预期：全绿

- [ ] T010 [P] 运行前端构建验证（前端无改动，仅验证无回归）：
  ```bash
  npm run build
  ```
  预期：构建成功

- [ ] T011 [P] 运行前端数据边界检查：
  ```bash
  npm run check:front-data-boundaries
  ```
  预期：通过

- [ ] T012 提交代码（原子提交，message 说明根因 + 修复方案）

- [ ] T013 推送分支并创建 PR（Gitee MCP），PR 描述包含：
  - 根因：@Async 同类内部调用失效
  - 修复：提取 WarehouseExportAsyncExecutor 独立 Bean
  - 验证：单元测试 + 架构测试 + Controller 契约测试
  - 不涉及 DB schema / API 契约 / 前端改动

---

## Dependencies

```
T001 (Setup)
  ↓
T002, T003 (并行：TDD Red，先写测试)
  ↓
T004 → T005 (串行：先创建 AsyncExecutor，再重构 AppService)
  ↓
T006 (TDD Green，验证测试通过)
  ↓
T007, T008, T009, T010, T011 (并行：Polish 验证)
  ↓
T012 → T013 (提交 + PR)
```

## Parallel Opportunities

- T002, T003: 两个测试文件可并行编写
- T007-T011: 5 项验证可并行执行
