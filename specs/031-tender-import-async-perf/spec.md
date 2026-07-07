# Feature Specification: 标讯批量导入异步化与性能优化 + MDC 用户上下文修复

**Feature Branch**: `agent/trae/tender-import-async-perf`

**Created**: 2026-07-07

**Status**: Draft

**Input**: User description: "批量导入标讯的时候报错 request timeout，但实际导入成功。开 spec 走方案 B/C（异步导入或性能优化）；同时排查日志里 userId/roleCode 显示 anonymous 的 MDC 问题，一并修复。"

## 背景与根因（来自生产日志全链路排查）

2026-07-07 生产环境（`jetty@172.16.38.78`）批量导入 180 行标讯时：

- 后端 `TenderImportService.importFromExcel` 同步 `@Transactional` 处理 180 行，耗时 103.5s
- Nginx `/api/` location 未配置 `proxy_read_timeout`，使用默认 60s，先于前端 axios(120s) 超时返回 504
- 后端事务不受 Nginx 连接断开影响，继续执行到 commit，导致"前端 timeout 但数据实际入库"
- 链路日志（traceId=`e746b5d980354c70a9c364eb8c35bb9f`）显示 `userId=anonymous`、`roleCode=anonymous`，根因是 `TraceFilter` 在 `filterChain.doFilter()` 之前调用 `putUserContext()`，此时 `JwtAuthenticationFilter` 尚未填充 `SecurityContextHolder`

证据链：
- Nginx error.log：`2026/07/07 18:06:13 [error] upstream timed out (110: Connection timed out) while reading response header from upstream, request: "POST /api/tenders/import"`
- 后端 access_log：`method=POST uri=/api/tenders/import status=201 elapsed=103565ms`
- 每行 `createTender` 串行调用 CRM `POST /company/getCompanyNameByLikeName`（单次 0.5-1s），180 行累计约 100s
- `TraceFilter.java:52` 在 `filterChain.doFilter()`（line 55）之前调用 `putUserContext()`，此时 `SecurityContextHolder.getAuthentication()` 为 null

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 批量导入不再超时，用户看到实时进度 (Priority: P1)

投标专员/组长上传含 100-500 行标讯的 Excel 文件后，前端立即返回"导入进行中"并显示进度（已处理 N/总行数、成功/失败计数），用户无需等待 60s+ 即可看到反馈。导入完成后用户能看到完整结果（成功条数、失败行明细）。无论导入耗时多久，用户都不会再看到 "request timeout" 错误。

**Why this priority**: 这是用户报告的核心痛点——"request timeout 但实际成功"造成严重困惑和重复操作。解决超时是最高优先级。

**Independent Test**: 上传 500 行 Excel，前端应在 <3s 内显示"导入进行中"，导入过程中实时更新进度条，完成后显示"成功 X 条/失败 Y 条"明细。整个过程不出现 timeout。

**Acceptance Scenarios**:

1. **Given** 投标组长已登录并进入标讯批量导入页面，**When** 上传 180 行标讯 Excel，**Then** 前端在 3 秒内显示"导入任务已创建，正在处理"，并展示进度条（已处理 0/180）
2. **Given** 导入任务正在后台处理，**When** 后端处理到第 90 行，**Then** 前端进度条更新为"已处理 90/180，成功 88，失败 2"
3. **Given** 导入任务已完成（180 行全部处理），**When** 用户查看结果，**Then** 显示"成功 178 条，失败 2 条"并展示失败行的行号和错误原因
4. **Given** 用户在导入过程中关闭浏览器或刷新页面，**When** 用户重新进入导入页面，**Then** 能看到未完成任务的当前进度（任务持久化）
5. **Given** 用户上传同一文件两次（Idempotency-Key 相同），**When** 第二次上传，**Then** 返回首次任务的结果而不重复导入

---

### User Story 2 - 单次导入 500 行在 60s 内完成（性能优化） (Priority: P2)

通过优化后端处理逻辑（CRM 查询批量化/缓存、数据库批量插入），单次导入 500 行标讯的端到端耗时从当前约 280s（推算）降至 60s 以内。即使保持同步导入模式，也能在 Nginx 默认 60s 超时前完成。

**Why this priority**: 性能优化是异步化的补充。即使异步化解决了 UX 问题，后端仍应高效处理，避免长时间占用数据库事务和 CRM 调用配额。

**Independent Test**: 上传 500 行 Excel（含已存在公司名和不存在公司名混合），端到端耗时 <60s，无 timeout，数据全部正确入库。

**Acceptance Scenarios**:

1. **Given** 500 行标讯 Excel（其中 50 行公司名在 CRM 中存在），**When** 用户上传并等待完成，**Then** 端到端耗时 <60s，500 行全部处理（成功或失败明细）
2. **Given** 同一批 180 行标讯，**When** 对比优化前后，**Then** 耗时从 103s 降至 <30s
3. **Given** CRM 服务正常响应（单次 0.5s），**When** 导入 500 行，**Then** CRM 调用次数不超过 500 次（无冗余调用），且相同公司名只查询一次（缓存命中）

---

### User Story 3 - 日志正确显示操作用户（MDC 修复） (Priority: P3)

所有已登录用户发起的请求，其日志（业务日志、access_log、异常日志）中的 `userId` 和 `roleCode` 字段必须显示真实用户信息，而非 "anonymous"。Sentry 错误事件也能正确定位到触发用户。

**Why this priority**: 不直接影响用户功能，但严重影响线上问题排查效率。当前所有日志的 userId 都是 anonymous，无法按用户筛选关联请求，违背 lessons-learned.md §23 全链路日志排查 SOP 的设计目标。

**Independent Test**: 用非管理员账号（如 `xiaowang`）登录并执行任意操作（如批量导入），查看后端日志，`userId` 字段应显示该用户的 ID 而非 "anonymous"。

**Acceptance Scenarios**:

1. **Given** 用户 `xiaowang`（roleCode=`bid-Team`）已登录，**When** 调用任意需要认证的 API（如 POST /api/tenders/import），**Then** 后端日志中该请求的所有日志条目（TraceFilter、AccessLogFilter、Service、异常）的 `userId` 字段为 `xiaowang` 的用户 ID，`roleCode` 为 `bid-Team`
2. **Given** 未登录用户访问需认证端点，**When** 请求被拒绝，**Then** 日志中 `userId=anonymous`（保持现有行为，符合预期）
3. **Given** OSS 用户（`role_id=NULL`）触发异常，**When** Sentry 捕获异常，**Then** Sentry 用户上下文显示正确的 roleCode（走 `EffectiveRoleResolver`，不返回 `manager` 兜底值）

---

### Edge Cases

- **Excel 文件超过 5MB 或行数超过 500**：当前 `TenderImportService` 已有校验（`MAX_FILE_BYTES=5MB`、`MAX_ROWS=500`），异步化后这些校验必须在"接收文件"阶段同步完成，避免后台任务才发现文件过大
- **CRM 服务不可用或响应慢**：异步任务中调用 CRM 失败时，标讯仍应入库（CRM 信息非必填），但日志需记录 CRM 调用失败，且失败不应阻塞导入
- **导入过程中服务重启**：异步任务需持久化到数据库（非内存队列），服务重启后能恢复或标记为失败，不能让用户无限等待
- **同一文件并发上传**：Idempotency-Key 相同时直接返回首次任务结果；不同 Idempotency-Key 但内容相同时，需通过去重逻辑（`TenderDeduplicationPolicy`）处理重复标讯
- **用户在导入过程中再次发起导入**：需决策是否允许并行任务，或限制每用户同时只能有一个进行中的导入任务
- **进度查询失败**：前端轮询进度接口失败时，需有重试机制，不能直接显示"导入失败"
- **MDC 在异步线程中的传递**：`@Async` 线程不会自动继承主线程的 MDC，需显式传递，否则异步任务内的日志仍是 anonymous

## Requirements *(mandatory)*

### Functional Requirements

**导入异步化（方案 B）**

- **FR-001**: 系统 MUST 在用户上传 Excel 后 3 秒内返回"导入任务已创建"响应，包含任务 ID 和初始进度（0/总行数）
- **FR-002**: 系统 MUST 在后台异步处理导入任务，不阻塞 HTTP 请求线程
- **FR-003**: 系统 MUST 提供进度查询接口，返回当前已处理行数、成功数、失败数、失败明细
- **FR-004**: 系统 MUST 持久化导入任务状态（进行中/已完成/失败），服务重启后任务状态不丢失
- **FR-005**: 系统 MUST 在导入完成后保留任务结果至少 24 小时，供用户回看
- **FR-006**: 系统 MUST 保持现有 `Idempotent` 语义：相同 Idempotency-Key 的重复请求返回首次任务结果
- **FR-007**: 系统 MUST 在异步任务内正确填充 MDC（userId/roleCode/traceId），使后台处理日志可追溯

**性能优化（方案 C）**

- **FR-008**: 系统 MUST 对同一批次 Excel 中相同的公司名只调用一次 CRM 查询接口（批次内缓存）
- **FR-009**: 系统 MUST 使用批量数据库插入替代循环单条 `createTender`，减少数据库往返次数
- **FR-010**: 系统 MUST 保证 500 行 Excel 端到端处理耗时 <60 秒（在 CRM 正常响应条件下）
- **FR-011**: 系统 MUST 在 CRM 调用失败时降级处理（标讯仍入库，CRM 字段为空），不阻塞导入流程
- **FR-012**: 系统 MUST 保持现有校验逻辑（必填字段、客户类型/优先级/项目类型枚举、去重），性能优化不得绕过校验

**MDC 用户上下文修复**

- **FR-013**: 系统 MUST 在 JWT 认证成功后立即刷新 MDC 的 userId/roleCode，覆盖 TraceFilter 写入的 "anonymous" 兜底值
- **FR-014**: 系统 MUST 在异步线程（`@Async`、`@Transactional` 子线程等）中正确传递 MDC 上下文
- **FR-015**: 系统 MUST 使用 `EffectiveRoleResolver.resolveRoleCode(user)` 解析 roleCode，禁止直调 `User.getRoleCode()`（CO-373 治理）
- **FR-016**: 系统 MUST 在请求结束时清理 MDC，避免线程池线程复用导致 MDC 泄漏到下一个请求
- **FR-017**: 系统 MUST 保证未认证请求的日志仍显示 "anonymous"（符合预期，不破坏现有行为）

**Nginx 配置（兜底）**

- **FR-018**: Nginx `/api/` location MUST 配置 `proxy_read_timeout` ≥ 180s，作为异步化完成前的兜底防护（避免异步化未上线期间仍出现 504）

### Key Entities *(include if feature involves data)*

- **TenderImportTask**: 异步导入任务实体，属性包括 taskId、userId、fileName、totalRows、processedRows、successCount、failureCount、status（PENDING/PROCESSING/COMPLETED/FAILED）、errors（失败行明细 List）、createdAt、completedAt
- **TenderImportTaskError**: 失败行明细，属性包括 rowNumber、field、errorMessage、tenderTitle（用于用户定位是哪条标讯失败）
- **TenderImportProgressDTO**: 进度查询响应 DTO，属性同 TenderImportTask 但脱敏（不暴露内部 userId 等）

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 用户上传 500 行标讯 Excel 后，前端在 3 秒内显示"导入任务已创建"（不再出现 request timeout）
- **SC-002**: 500 行标讯导入端到端耗时 <60 秒（性能优化生效后）
- **SC-003**: 导入过程中用户可实时看到进度（已处理行数/总行数），进度更新延迟 ≤2 秒
- **SC-004**: 导入完成后用户能看到完整的失败行明细（行号、字段、原因），无需重新下载文件核对
- **SC-005**: 服务重启后，进行中的导入任务能被正确标记为 FAILED 并通知用户，不会让用户无限等待
- **SC-006**: 所有已登录用户请求的日志（含异步任务内日志）的 userId 字段为真实用户 ID，非 "anonymous"
- **SC-007**: Sentry 错误事件能正确显示触发用户的 userId/username/roleCode（OSS 用户走 EffectiveRoleResolver，不返回 manager 兜底值）
- **SC-008**: 异步化上线后，Nginx error.log 中不再出现 `POST /api/tenders/import` 的 `upstream timed out` 记录

## Assumptions

- 现有 `TenderImportService` 的 Excel 解析逻辑（表头校验、行级校验、字段映射）保持不变，仅改造"处理"阶段（同步循环 → 异步批处理）
- 现有 `TenderDeduplicationPolicy` 去重逻辑保持不变，异步化后仍按行级去重
- CRM 接口（`/company/getCompanyNameByLikeName`、`/customerManager/getCustomerManagerListByCompanyId`）响应时间在正常范围（单次 <1s），不存在持续性故障
- 前端 `BulkImportDialog` 组件可改造为支持进度轮询（每 2 秒查询一次进度接口）
- 异步任务使用 Spring `@Async` + 数据库持久化（非消息队列），与现有技术栈一致，不引入 RabbitMQ/Kafka 等新依赖
- MDC 修复仅涉及 `TraceFilter` 和 `JwtAuthenticationFilter`，不重构整个过滤器链
- Nginx 配置修改由用户亲自部署（按用户偏好），本 spec 只产出配置文件改动，不包含部署执行
- 本 spec 不改变 `@Idempotent` 注解的现有语义和 `IdempotencyFilter` 的行为

## Out of Scope

- 不重构 `TenderCommandService.createTender` 的核心业务逻辑（仅批量化调用方式，不改 createTender 内部）
- 不引入消息队列（RabbitMQ/Kafka）——使用 Spring `@Async` + DB 持久化即可满足 500 行规模
- 不改造前端 `BulkImportDialog` 的 Excel 模板下载功能
- 不修复 Nginx 默认 60s 超时的其他端点（仅 `/api/tenders/import` 相关 location）——其他端点如有类似问题另行处理
- 不重构 Sentry SDK 的异步发送机制（仅修复用户上下文注入源）
