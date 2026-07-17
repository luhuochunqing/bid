# Research: 修复仓库全量合订本导出任务创建失败

**Date**: 2026-07-17
**Feature**: 039-fix-warehouse-export-async

## 研究问题

1. Spring @Async 同类内部方法调用为何失效？
2. 标准修复模式是什么？
3. 现有 `warehouseExportExecutor` 线程池配置是否已就绪？
4. MDC 上下文透传到异步线程是否已配置？

## 研究结论

### 1. Spring @Async 同类内部方法调用失效根因

**Decision**: Spring AOP 基于动态代理（CGLIB 或 JDK Proxy）实现，代理只拦截从外部通过 Spring Bean 引用调用的方法。当 `WarehouseExportAppService.export()` 内部直接调用 `this.executeExportAsync()` 时，`this` 是目标对象本身而非代理对象，AOP 织入逻辑（包括 @Async 的 `AsyncExecutionInterceptor`）不会执行。

**Rationale**: 这是 Spring AOP 的基础机制。Spring 官方文档 "Understanding AOP Proxies" 明确说明：self-invocation 不会经过代理。@Async、@Transactional、@Cacheable 等 AOP 注解都受此限制。

**Alternatives considered**:
- **方案 A: 提取独立 Bean（选中）**：将 @Async 方法移到新的 Spring Bean，通过依赖注入调用。Spring 官方推荐的标准修复模式。
- **方案 B: AopContext.currentProxy()**：通过 `@EnableAspectJAutoProxy(exposeProxy = true)` + `((WarehouseExportAppService) AopContext.currentProxy()).executeExportAsync()`。侵入性强，耦合 Spring 框架 API，可读性差。
- **方案 C: AspectJ 编译时织入**：引入 aspectj-maven-plugin，切换到 AspectJ 模式，self-invocation 也能生效。改动大，需调整 pom.xml + 编译插件，过度工程化。
- **方案 D: ApplicationContextAware 自注入**：`@Autowired private WarehouseExportAppService self;` 然后 `self.executeExportAsync()`。可读性差，自注入反模式。

**选择方案 A 的理由**：最平淡、最可读、Spring 官方推荐（Constitution §VIII Boring Proven Patterns）。新增一个单一职责的 Bean，符合 Split-First 原则（Constitution §IV）。

### 2. 现有 warehouseExportExecutor 线程池配置

**Decision**: 现有 `AsyncConfig.java` 已定义 `warehouseExportExecutor` Bean，配置完整。

**Rationale**: 检查 `backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java` 确认：
- 线程名前缀：`warehouse-export-`（验证 @Async 生效的可观测指标）
- core=2, max=4, queue=20
- CallerRunsPolicy（队列满时调用线程执行）
- 已配置 MdcTaskDecorator（MDC 透传）

**Alternatives considered**: 无需调整线程池配置，现有配置足以支撑当前导出并发量。

### 3. MDC 上下文透传

**Decision**: MdcTaskDecorator 已配置在 warehouseExportExecutor 上，异步线程 MDC 透传已可用。

**Rationale**: AsyncConfig 中 `warehouseExportExecutor` 方法已 `decorator(new MdcTaskDecorator())`。修复 @Async 失效后，MDC 透传自动生效。

### 4. 修复影响范围

**Decision**: 仅影响 `WarehouseExportAppService.java` + 新增 `WarehouseExportAsyncExecutor.java`。

**Rationale**:
- Controller 层不变（API 契约不变）
- Domain 层不变（业务规则不变）
- Infrastructure 层不变（Word/ZIP 构建器不变）
- 前端不变（请求/响应格式不变）
- DB schema 不变（Entity 字段不变）

## 风险评估

| 风险 | 影响 | 缓解 |
|------|------|------|
| @Async 生效后任务执行时机变化 | 原同步执行时任务在 HTTP 线程内完成，现在异步执行 | 这是预期行为，前端已有轮询机制 |
| 异步任务异常不再冒泡到 Controller | 原同步异常会被 GlobalExceptionHandler 捕获返回 500 | executeExportAsync 内部已有 try-catch 写 FAILED 状态，不依赖 Controller 层异常处理 |
| 事务边界变化 | 原 @Transactional 覆盖整个同步流程，现在 @Transactional 只覆盖 createTask | doExport 内部的 markProcessing/completeTask/saveZip 各自有独立事务（已通过 @Transactional(propagation=REQUIRES_NEW) 或独立事务方法实现，需验证） |

**事务边界验证点**：检查 `WarehouseExportAppService` 中 `markProcessing`/`completeTask`/`failTask` 的事务传播策略，确保异步线程中能独立提交事务更新任务状态。
