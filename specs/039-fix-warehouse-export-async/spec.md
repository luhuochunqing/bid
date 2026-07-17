# Feature Specification: 修复仓库全量合订本导出任务创建失败

**Feature Branch**: `agent/claude/fix-warehouse-export-async`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "本地测试环境和西域测试环境现在导出仓库全量合订本会报错，创建导出任务失败"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 投标管理员导出仓库全量合订本 (Priority: P1)

投标管理员（bid_admin）在仓库管理页面点击"导出台账（含附件）"按钮，选择"当前筛选结果"或"当前勾选的仓库"，附件导出范围选择"全部文件导出"，附件组织形式勾选"Word 合订本"（默认），点击"开始导出"。系统应在 30 秒内返回 202 Accepted 并创建导出任务，前端显示"导出任务排队中..."，随后用户可通过轮询查看任务状态，任务完成后可下载 ZIP 包（含台账 xlsx + Word 合订本 .docx + 可选附件目录）。

**Why this priority**: 这是核心业务功能，CO-582 上线后该路径完全不可用，用户无法导出仓库全量合订本，阻断日常运营。

**Independent Test**: 以 bid_admin 登录，打开仓库管理页，点击"导出台账（含附件）" → 保持默认选项 → 点击"开始导出"。预期 30 秒内创建任务并进入轮询状态，而非显示"创建导出任务失败"。

**Acceptance Scenarios**:

1. **Given** bid_admin 已登录且仓库列表有数据，**When** 点击"导出台账（含附件）" → "开始导出"（filter 模式 + ALL + WORD_COMBINED），**Then** 30 秒内前端收到 202 + taskId，进入"导出任务排队中..."状态
2. **Given** bid_admin 已登录且已勾选若干仓库，**When** 选择"当前勾选的仓库" → "开始导出"（ids 模式 + ALL + WORD_COMBINED），**Then** 30 秒内前端收到 202 + taskId
3. **Given** 导出任务已创建，**When** 后端执行 Word 合订本生成（含 PDF 渲染），**Then** 执行在线程名以 `warehouse-export-` 开头的线程中，不阻塞 HTTP 请求线程
4. **Given** Word 合订本生成失败（如 PDF 损坏），**When** 后端执行导出，**Then** 降级为仅含台账 + 附件目录的 ZIP，任务状态为 COMPLETED 而非 FAILED
5. **Given** 仓库数量较多（如 50+）且附件较多，**When** 触发全量合订本导出，**Then** HTTP 请求快速返回 202，导出在后台异步完成

---

### Edge Cases

- 当用户未登录或会话过期时点击"开始导出"，返回 401 并引导重新登录（已有逻辑，不在本次修复范围）
- 当 attachmentForms 为空数组时，返回 400"请至少选择一种附件组织形式"（已有逻辑）
- 当 ids 模式下 selectedIds 为空时，返回 400"未选择任何仓库"（已有逻辑）
- 当 @Async 线程池（warehouseExportExecutor）队列满时，由 CallerRunsPolicy 兜底，调用线程执行（已是现状，但修复后 HTTP 线程不会被调用，因为 @Async 会生效）

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在用户点击"开始导出"后 30 秒内返回 202 Accepted 和 taskId，无论后续 Word 合订本生成耗时多长
- **FR-002**: 系统 MUST 确保 Word 合订本生成（含 PDF 渲染）在专用线程池（warehouseExportExecutor）中异步执行，不阻塞 HTTP 请求线程
- **FR-003**: 系统 MUST 保留 CO-582 §4 的异常降级语义：Word 合订本生成失败时降级为仅含台账 + 附件目录的 ZIP，不影响整体导出任务
- **FR-004**: 系统 MUST 保留现有 API 契约（POST /api/knowledge/warehouses/export 请求/响应格式不变）
- **FR-005**: 系统 MUST 保留现有任务状态机（PENDING → PROCESSING → COMPLETED/FAILED）和 24 小时文件 TTL
- **FR-006**: 系统 MUST 保留 MDC 上下文透传（traceId/userId/roleCode）到异步线程，确保异步任务日志可追溯

### Key Entities *(include if feature involves data)*

- **WarehouseExportTaskEntity**: 导出任务记录，字段不变（id, status, filterSnapshot, totalCount, storedFilePath, downloadUrl, expiresAt, createdBy, createdAt, completedAt, failureReason, resultSummary）
- **WarehouseExportAsyncExecutor** *(新增)*: 承载 @Async 方法的独立 Spring Bean，由 WarehouseExportAppService 通过依赖注入调用，使 @Async 代理生效

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: bid_admin 在仓库管理页点击"导出台账（含附件）" → "开始导出"，30 秒内前端进入"导出任务排队中..."状态，不再显示"创建导出任务失败"
- **SC-002**: 后端日志中，导出任务执行线程名以 `warehouse-export-` 开头（而非 `http-nio-18089-exec-*`），证明 @Async 生效
- **SC-003**: 50+ 仓库 + 大量附件的全量合订本导出，HTTP 请求响应时间 < 2 秒（仅创建任务记录），导出在后台异步完成
- **SC-004**: Word 合订本生成异常时，任务仍能 COMPLETED 并返回含台账 + 附件目录的 ZIP（降级语义不回归）

## Assumptions

- 现有 `warehouseExportExecutor` 线程池配置（core=2, max=4, queue=20, CallerRunsPolicy）足以支撑当前导出并发量，本次不调整线程池参数
- MdcTaskDecorator 已配置在 warehouseExportExecutor 上，异步线程 MDC 透传已可用
- 本次仅修复 @Async 失效问题，不重构整个导出流程
- 不涉及数据库 schema 变更（WarehouseExportTaskEntity 字段不变）
- 不涉及前端 API 契约变更（请求/响应格式不变）
