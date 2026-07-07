# Research: 标讯批量导入异步化

## 调研方法

通过代码搜索（Grep/Glob/Read）+ 子代理深度调研，收集项目现有范式和陷阱。所有结论带 file:line 证据。

## R-001: Spring @Async 配置现状与陷阱

**Decision**: 新增 `tenderImportExecutor` 专用线程池（core=2, max=4, queue=50, CallerRunsPolicy）。

**Rationale**:
- 项目已有 4 个专用线程池（[AsyncConfig.java:24-79](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java#L24-L79)）
- 已有 9+ 个 @Async 案例，最相似的是 [ImportPersonnelAppService.java:49](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L49)
- 不复用 `importExportExecutor`（人员证书专用，避免抢线程）

**Alternatives**:
- 复用 `importExportExecutor`：语义混淆，拒绝
- 默认 `SimpleAsyncTaskExecutor`：无池化，拒绝
- RabbitMQ/Kafka：spec Out of Scope，拒绝

## R-002: @Async 自调用陷阱

**Decision**: 从 Controller 跨类调用 `TenderImportAppService.triggerImport`，代理生效。

**Rationale**:
- Spring AOP PROXY 模式下同类内调用绕过代理
- 不使用 `@Lazy @Autowired self` 自注入（反模式）

## R-003: MultipartFile 在 @Async 失效

**Decision**: Controller 同步阶段读取 `MultipartFile` 为 `byte[]`。

**Rationale**:
- HTTP 请求结束后 Tomcat 立即清理临时文件
- [ImportPersonnelAppService.java:50](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L50) 入参就是 `byte[]`

## R-004: 异步任务持久化方案

**Decision**: 新建 `tender_import_task` 表，schema 参考 `personnel_import_task`。

**Rationale**:
- 项目已有 3 个 import_task 表范式
- `personnel_import_task`（[V1022](file:///Users/user/xiyu/worktrees/trae/backend/src/main/resources/db/migration-mysql/V1022__personnel_batch_import_task.sql)）最完整：5 状态机 + JSON error_details + 完成时间戳

## R-005: 进度查询方案

**Decision**: 轮询（前端每 2s）+ Redis 缓存 + DB fallback，参考 [PersonnelImportProgressService.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/PersonnelImportProgressService.java)。

**Rationale**:
- 项目无 SSE/WebSocket（0 匹配）
- Constitution VIII: Boring Proven Patterns
- Redis 不可用时降级到 DB（`Optional<StringRedisTemplate>` 注入范式）

## R-006: @Transactional 与 @Async 的事务边界

**Decision**:
- @Async 方法不加 `@Transactional`
- 保留 `createTender` 的 `@Transactional`（每行独立事务）
- 语义变更：从"全量回滚"改为"部分成功"

**Rationale**:
- 当前 103.5s 长事务是性能瓶颈
- spec Edge Case 明确要求"失败行明细"而非回滚
- 单行独立事务：失败行回滚，成功行保留

## R-007: CRM 批次内缓存

**Decision**: 异步任务内构建 `Map<String, Optional<CompanySearchResult>>` 本地缓存。

**Rationale**:
- CRM 接口无批量方法（[CrmCompanySearchService.java:54](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/crm/application/CrmCompanySearchService.java#L54)）
- spec FR-008 明确要求
- `Optional` 包装：缓存"未找到"结果，避免重复查询空结果

## R-008: 数据库批量 INSERT

**Decision**: 开启 Hibernate `batch_size=50` + `order_inserts=true` + DB_URL 加 `rewriteBatchedStatements=true`。

**Rationale**:
- spec Out of Scope 限制不改 `createTender` 内部
- [application.yml:24-27](file:///Users/user/xiyu/worktrees/trae/backend/src/main/resources/application.yml#L24-L27) 当前未配置
- 开启后 Hibernate 底层自动批量

## R-009: MDC 修复方案

**Decision**:
1. `TraceFilter` 保留 `putUserContext()` 作为 anonymous 兜底
2. `JwtAuthenticationFilter` 认证成功后刷新 MDC
3. 新增 `MdcTaskDecorator` 传递 MDC 到 @Async 线程
4. roleCode 走 `EffectiveRoleResolver`（CO-373）

**Rationale**:
- 根因：[TraceFilter.java:52](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/config/TraceFilter.java#L52) 在 `filterChain.doFilter()` 之前调 `putUserContext()`
- @Async 线程不自动继承 ThreadLocal，必须显式传递

## R-010: 异步任务异常兜底

**Decision**: `executeImportAsync` 内 try-catch `Throwable`，finally 中 `failImportTask` 三层降级。

**Rationale**:
- Spring `SimpleAsyncUncaughtExceptionHandler` 只 log 不传播，任务卡在 PROCESSING
- 三层降级参考 [ImportPersonnelAppService.java:163-198](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L163-L198)

## R-011: 服务重启任务卡死

**Decision**: 启动时扫描 `status=PROCESSING AND updated_at < now()-30min` 的任务，标记 FAILED。

**Rationale**: 参考 `TenderTaskWorkerService` 的启动扫描模式

## R-012: 同步阶段超 3s 风险

**Decision**: 同步阶段只做 `validateFile` + 创建 task + 读取 byte[]。Excel 解析移到异步阶段。

**Rationale**: XSSFWorkbook 解析 180 行可能耗时，参考 `ImportPersonnelAppService` 模式

## 潜在陷阱清单

| # | 陷阱 | 应对 |
|---|---|---|
| 1 | @Async 自调用失效 | 从 Controller 跨类调用 |
| 2 | MultipartFile 在 @Async 失效 | Controller 读 byte[] 传入 |
| 3 | 异步任务静默失败 | try-catch Throwable + finally failImportTask |
| 4 | failImportTask 自身失败 | 三层降级：save → updateStatus → clearProgress |
| 5 | CallerRunsPolicy 阻塞 HTTP 线程 | queue=50 足够（500 行只产生 1 个任务） |
| 6 | Hibernate batch_size 不生效 | DB_URL 加 rewriteBatchedStatements=true |
| 7 | @Transactional + @Async 顺序 | @Async 方法不加 @Transactional |
| 8 | 全量回滚语义变更 | 单行独立事务，部分成功 |
| 9 | TraceFilter MDC 写入时机 | JwtAuthenticationFilter 认证后刷新 |
| 10 | @Idempotent 语义保持 | 缓存新响应结构（taskId） |
| 11 | 服务重启任务卡死 | 启动扫描 + 标记 FAILED |
| 12 | SecurityContext 不传递到 @Async | 通过 userId 参数显式传递 |
| 13 | 同步阶段超 3s | Excel 解析移到异步阶段 |
| 14 | 任务结果保留 24h | DB 永久保留 + Redis TTL=7 天 |
