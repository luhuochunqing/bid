# Feature Specification: 西域对接 — 组织架构同步SDK接入

**Feature Branch**: `agent/cursor/organization-sdk-integration`

**Created**: 2026-05-21

**Status**: Draft

**Input**: 西域对接 — 组织架构同步SDK接入：基于泊冉SDK（com.ehsy.eventlibrary:ClientSDK:release_0.0.2）实现 BaseOssDept 和 BaseOssUser 事件订阅，通过动态换取 Bearer token 调用 YAPI 组织架构接口，幂等落库、自动重试与每日低峰对账，移除 HTTP fallback 路径。

---

## User Scenarios & Testing

### User Story 1 - 部门变更事件同步 (Priority: P1)

西域组织架构系统发生部门新增、修改、启停等变更时，投标系统自动接收 BaseOssDept 事件，查询最新部门数据并更新本地记录，运维可在日志中追踪全流程。

**Why this priority**: 部门是项目成员指派和数据权限的基础维度，部门数据不一致会直接影响业务功能。

**Independent Test**: 可通过模拟发送 BaseOssDept 测试事件，验证本地部门表 upsert 结果和事件状态为成功。

**Acceptance Scenarios**:

1. **Given** 投标系统已启动 SDK 订阅，**When** 西域推送包含 `data.deptId=3730158` 的 BaseOssDept 事件，**Then** 系统调用 YAPI 部门详情接口，按 `deptId` 幂等 upsert 本地部门记录，事件状态为成功，日志含 traceId/deptId。
2. **Given** 同一 deptId 的事件在短时间内重复到达，**When** 系统再次收到相同 deptId 的 BaseOssDept 事件，**Then** 命中幂等键直接返回成功，不重复写库，不调用 YAPI。

---

### User Story 2 - 员工变更事件同步 (Priority: P1)

西域组织架构系统发生员工新增、修改、离职、启停等变更时，投标系统自动接收 BaseOssUser 事件，查询最新员工数据并更新本地记录，员工离职后本地记录标记为禁用而非物理删除。

**Why this priority**: 员工是投标项目成员和审批链的核心数据源，数据准确性直接影响业务操作。

**Independent Test**: 可通过模拟发送 BaseOssUser 测试事件，验证本地员工表 upsert/disable 结果。

**Acceptance Scenarios**:

1. **Given** 投标系统已启动 SDK 订阅，**When** 西域推送包含 `data.userId=720518523` 的 BaseOssUser 事件，**Then** 系统调用 YAPI 员工详情接口，按 `userId` 幂等 upsert 本地员工记录，事件状态为成功。
2. **Given** 某员工在 YAPI 接口中状态为禁用/不存在，**When** 系统收到该员工的 BaseOssUser 事件并调用 YAPI 返回查无/禁用，**Then** 本地员工记录标记为禁用状态，保留历史引用，不物理删除。

---

### User Story 3 - YAPI 接口调用鉴权 (Priority: P1)

调用 YAPI 组织架构接口前，系统自动以动态换取的 Bearer token 鉴权，令牌过期前自动刷新，鉴权失败时进入可识别的错误路径而非静默失败。

**Why this priority**: YAPI 基于网络白名单安全，无需动态 token 换取；只需正确的链路追踪 Header 即可鉴权。

**Independent Test**: 模拟 token 过期场景，验证系统在过期后自动重新换取并继续处理。

**Acceptance Scenarios**:

1. **Given** 系统启动时，**When** 首次调用 YAPI 接口，**Then** 先调用 applyToken 接口换取 access_token 并缓存在内存，过期前按比例自动续期。
2. **Given** 链路追踪 Header 缺失或不正确，**When** 系统调用 YAPI 接口，**Then** YAPI 按 HTTP 4xx 拒绝，系统记录失败原因到事件日志。

---

### User Story 4 - 事件处理失败重试 (Priority: P2)

YAPI 接口调用失败或数据库写入失败时，事件进入重试队列，系统按指数退避策略自动重试，达到最大次数后进入死信队列等待人工处理，避免事件丢失。

**Why this priority**: 网络抖动、接口限流等临时故障不可避免，重试机制保证最终一致性。

**Independent Test**: 模拟 YAPI 返回 5xx 或数据库死锁，验证重试次数、退避延迟和死信状态。

**Acceptance Scenarios**:

1. **Given** 事件处理中 YAPI 返回 5xx 或网络超时，**When** 系统记录失败并触发重试，**Then** 重试按指数退避（5min → 10min → 20min → 40min），达到最大次数后事件进入死信状态，失败原因可定位。
2. **Given** 死信队列中存在积压事件，**When** 运维在低峰期触发手工重放，**Then** 死信事件重新进入处理流程，claim 后若服务中断则状态回退到死信。

---

### User Story 5 - 定时对账 (Priority: P2)

每天在低峰时段按时间窗口拉取最近变更数据，与本地数据进行对账，发现差异时记录并按冻结语义修复，保证本地数据与西域组织架构的最终一致。

**Why this priority**: 事件订阅只覆盖接入之后的变更，定时对账可发现漏接事件并补齐数据。

**Independent Test**: 在低峰时段触发定时对账，验证时间窗口内差异被记录和修复。

**Acceptance Scenarios**:

1. **Given** 系统配置了每日低峰对账，**When** 定时任务在低峰时段触发，**Then** 按时间窗口调用部门/员工时间窗接口，分页同步，空页结束，差异被记录并按 upsert/disable 语义处理。
2. **Given** 对账发现差异数量超过预设阈值，**Then** 触发告警并暂停自动修复，等待人工判断。

---

### User Story 6 - 运维手工重同步 (Priority: P3)

运维可在任意时刻按 deptId 或 userId 触发单个对象的重新同步，用于客户补数、单条纠错和 UAT 验收。

**Why this priority**: 覆盖自动化流程无法处理的边界场景，提供降级恢复手段。

**Independent Test**: 通过 API 触发单个 deptId 的手工重同步，验证 upsert 结果和操作记录。

**Acceptance Scenarios**:

1. **Given** 运维已知某 deptId 在本地数据异常，**When** 调用手工重同步接口，**Then** 系统调用 YAPI 部门详情接口并 upsert 到本地，写入操作记录（操作人、时间、对象ID、结果）。
2. **Given** 手工重同步失败，**Then** 错误写入操作记录并返回失败原因，不进入自动重试或死信队列。

---

### Edge Cases

- 收到未知 Topic 事件：记录告警，跳过处理，不进入死信队列。
- YAPI 返回的字段比预期少：按已有字段处理，缺失字段保留原值或空值，不报系统错误。
- 短时间内同一对象多次变更：以最后一次 YAPI 查询结果为准，覆盖本地旧数据。
- 大批量组织调整事件积压：按批次限流处理，避免瞬时大量调用 YAPI。
- SDK 启动注册失败：服务不进入 READY 状态，告警通知运维，进程不退出。

---

## Requirements

### Functional Requirements

- **FR-001**: 系统必须能通过 Maven 引入 `com.ehsy.eventlibrary:ClientSDK:release_0.0.2` 并成功编译，SDK 依赖在 CI/CD 流水线可复现。
- **FR-002**: 系统必须支持通过 `@AcceptEvent` 注解订阅 `BaseOssDept` 和 `BaseOssUser` 事件，consumerGroup 可通过配置注入。
- **FR-003**: 调用 YAPI 接口前，系统必须先通过 applyToken 动态换取 Bearer token，令牌按 `expires_in` 缓存，过期前按配置比例自动续期。
- **FR-004**: 收到 BaseOssDept 事件后，系统必须使用事件中的 `data.deptId` 调用 YAPI 部门详情接口，以 deptId 为幂等键 upsert 本地部门记录。
- **FR-005**: 收到 BaseOssUser 事件后，系统必须使用事件中的 `data.userId` 调用 YAPI 员工详情接口，以 userId 为幂等键 upsert 本地员工记录。
- **FR-006**: YAPI 返回禁用/查无时，本地员工记录必须标记为禁用状态，不得物理删除；本地部门记录按同理处理。
- **FR-007**: 重复事件（相同 traceId + spanId + eventTopic）必须命中幂等键，直接返回成功，不重复调用 YAPI 或写库。
- **FR-008**: YAPI 调用超时、5xx、临时限流、数据库瞬时失败必须触发指数退避重试，次数耗尽后事件进入死信队列。
- **FR-009**: 每天在低峰时段（可配置 cron 表达式），系统必须调用部门/员工时间窗接口进行增量同步，对账差异按冻结语义处理。
- **FR-010**: 运维必须能通过 API 按 deptId 或 userId 触发单个对象的手工重同步，并留下操作记录。
- **FR-011**: 日志必须记录 eventTopic、key、traceId、spanId、deptId/userId、处理结果、耗时、错误分类；不得记录完整 token、手机号、邮箱。
- **FR-012**: HTTP fallback 路径必须被移除，不维护 SDK 和 HTTP 双入口。

### Key Entities

- **OrganizationEvent**: 事件记录，含 topic、key、traceId、spanId、deptId/userId、状态（PROCESSING / PROCESSED / PENDING_RETRY / DEAD_LETTER）、重试次数、下次重试时间、最后错误信息。
- **OrganizationDepartment**: 本地部门，含 externalDeptId、departmentName、parentId、disabled、lastSyncedAt。
- **OrganizationUser**: 本地员工，含 externalUserId、username、departmentId、disabled、lastSyncedAt。
- **OrganizationSyncRun**: 对账/初始化运行记录，含 runType、triggeredBy、windowStart、windowEnd、成功数、失败数。
- **OrganizationSyncItem**: 同步项明细，含 syncRunId、对象类型、对象ID、结果、错误信息。

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: SDK 启动后 30 秒内完成服务注册和事件订阅初始化，无异常日志。
- **SC-002**: 收到 BaseOssDept/BaseOssUser 事件后，事件从接收到本地落库完成的全流程延迟 P95 ≤ 3 秒。
- **SC-003**: 重复事件 100% 命中幂等，返回成功且不重复写库。
- **SC-004**: 临时故障（YAPI 5xx、超时）触发的重试，在 60 分钟内最终成功或进入死信队列，零事件静默丢失。
- **SC-005**: 每日对账可在 10 分钟内完成，发现的差异 100% 被记录和处理（自动修复或人工告警）。
- **SC-006**: 事件消费成功率（PROCESSED / 总接收）≥ 99%，低于阈值触发告警。

---

## Assumptions

- 客户西域提供 `com.ehsy.eventlibrary:ClientSDK` Maven 私服地址和 `release_0.0.2` 版本坐标。
- YAPI 接口无需 Bearer token，基于网络白名单 + 链路追踪 Header（`EHSY-TraceID`、`EHSY-SRCAPP`）鉴权。
- YAPI 部门/员工详情接口使用 POST 方法，部门详情为 `application/x-www-form-urlencoded`，时间窗口为 `application/json`。返回 `code=0` 或 `code=200` 为成功。
- 生产 consumerGroup 命名由西域在联调阶段最终确认，当前使用 `bid-org-consumer-test` 作为占位符。
- 客户西域在联调前提供测试环境和生产环境的 YAPI base URL、IP 白名单和 Kafka/Zookeeper broker 列表。
