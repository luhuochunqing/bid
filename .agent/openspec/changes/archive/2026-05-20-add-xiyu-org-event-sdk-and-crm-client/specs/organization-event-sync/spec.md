# Capability: organization-event-sync

> 客户组织架构事件订阅与主数据同步能力。事件库只作变更通知，事实源为西域组织架构主数据接口；SDK 主链 + HTTP 中转灾备共用同一份幂等 inbox 和回查写入路径。

## ADDED Requirements

### Requirement: Customer Event Library SDK Subscription
系统 MUST 通过 `com.ehsy.eventlibrary:ClientSDK` 订阅 `BaseOssDept` 与 `BaseOssUser` 两类事件，使用 `@AcceptEvent` 注解暴露消费方法，每个方法只处理一个 `eventTopic`，方法入参为 `String eventMessage`，返回值兼容客户 SDK 约定的 `EventResult` 结构。

#### Scenario: 注册与启动
- **GIVEN** 服务启动时 `xiyu.integrations.organization.sdk.enabled = true` 且 `serverRegisterUrl`、`serviceName`、`consumerGroup`、broker 配置齐全
- **WHEN** Spring 容器完成初始化
- **THEN** SDK 完成服务注册与订阅关系绑定，`BaseOssDept`、`BaseOssUser` 处理器在 SDK 日志中可见，并保持续约心跳

#### Scenario: 部门事件消费成功
- **WHEN** 事件库向消费方推送一条合法的 `BaseOssDept` 事件
- **THEN** 系统通过 `@AcceptEvent` 方法接收，按 §"Authoritative Directory Lookup"调用主数据接口、按 §"Local Upsert by Business Key" upsert 到本地部门表，并向 SDK 返回 `EventResult code=200, msg=success`

#### Scenario: 员工事件消费成功
- **WHEN** 事件库向消费方推送一条合法的 `BaseOssUser` 事件
- **THEN** 系统按 `data.userId` 回查员工接口、按 `userId` upsert 到本地员工表，并返回 `EventResult code=200`

#### Scenario: 业务处理失败需要重试
- **WHEN** 事件解析或下游处理失败且属于可重试错误（5xx / 网络超时 / 数据库瞬时异常）
- **THEN** 系统返回 `EventResult code=500, msg=<具体错误>`，由 SDK 触发事件库重投递，且不在本地写入脏数据

### Requirement: HTTP Webhook Failover
当 SDK 不可用或上线前的联调阶段，系统 MUST 同时提供受签名校验保护的 HTTP 中转入口 `POST /api/integrations/organization/events`，作为事件接入的灾备路径。

#### Scenario: 灾备入口接收合法事件
- **GIVEN** 请求携带有效的 `EHSY-TraceID`、`EHSY-SRCAPP` 与 HMAC-SHA256 `EHSY-Signature`
- **AND** 调用方 IP 在 `xiyu.integrations.organization.ip-whitelist` 内
- **WHEN** 请求体为合法的事件 JSON
- **THEN** 系统按 §"Authoritative Directory Lookup" 路径处理，返回 `code=200`、`status=PROCESSED`

#### Scenario: 签名失败拒绝
- **WHEN** 请求 `EHSY-Signature` 与 `webhook-secret` 计算结果不一致
- **THEN** 系统返回 HTTP 401，事件不写入 inbox，不调用任何下游接口

#### Scenario: 集成开关关闭
- **GIVEN** `xiyu.integrations.organization.enabled = false`
- **WHEN** 任何来源（SDK 或 HTTP）投递事件
- **THEN** 系统在 inbox 中将事件标记为 `REJECTED`，向调用方返回 `code=500, msg=组织架构事件接入已关闭`，不调用任何下游接口

### Requirement: Idempotent Event Inbox
系统 MUST 使用 `traceId + spanId + eventTopic` 作为事件层幂等键，并在调用任何下游主数据接口、本地写库前将事件以 `PROCESSING` 状态预占至 `organization_event_logs`；同一事件重复投递时必须返回成功且不重复落库。

#### Scenario: 重复事件秒级返回成功
- **GIVEN** 事件 inbox 中已存在 `status=PROCESSED` 且 `event_key` 一致的记录
- **WHEN** SDK 或 HTTP 再次投递同一事件
- **THEN** 系统直接返回 `code=200, msg=success, duplicate=true`，不调用主数据接口、不再写本地表

#### Scenario: 处理中并发投递
- **WHEN** 同一事件在前次 `PROCESSING` 占位期间被并发投递
- **THEN** 第二次投递获得 `duplicate=true` 响应，不触发并发的主数据回查或写库

#### Scenario: 失败事件保留可重试
- **WHEN** 事件处理失败并被标记 `FAILED`
- **THEN** inbox 记录保留 `retry_count` 与 `last_error`，下一次事件库重投或对账任务可基于业务主键继续重试

### Requirement: Authoritative Directory Lookup
系统 MUST 仅以西域组织架构主数据接口的返回结果作为最终事实源；事件 `data` 字段只允许作为路由依据，禁止直接 upsert 到本地业务表。

#### Scenario: BaseOssDept 触发部门回查
- **WHEN** 收到合法的 `BaseOssDept` 事件
- **THEN** 系统按 `data.deptId` 调用"根据部门编码获取部门数据"接口，使用接口返回值（部门编码、名称、父级、状态、负责人等）执行本地 upsert

#### Scenario: BaseOssUser 触发员工回查
- **WHEN** 收到合法的 `BaseOssUser` 事件
- **THEN** 系统按 `data.userId` 调用"根据员工 ID 获取员工数据"接口，并以接口返回值执行本地 upsert

#### Scenario: 接口超时与降级
- **WHEN** 主数据接口调用超时或返回 5xx
- **THEN** 系统不写本地业务表，事件标记 `FAILED` 并进入指数退避重试

### Requirement: Local Upsert By Business Key
系统 MUST 以 `deptId` / `userId` 作为本地业务表的唯一业务主键；禁止使用本地自增 ID 作为跨系统主键，禁止物理删除上游存在过的对象。

#### Scenario: 部门数据 upsert
- **GIVEN** 接口返回部门 `{deptId, deptName, parentDeptId, status, ...}`
- **WHEN** 写入本地 `organization_departments`
- **THEN** 系统按 `deptId` 执行 upsert，保留本地审计列（`updated_at` 等），不新增、不覆盖业务无关字段

#### Scenario: 员工数据 upsert
- **GIVEN** 接口返回员工 `{userId, userName, mobile?, email?, departmentId, position, status, ...}`
- **WHEN** 写入本地员工目录表
- **THEN** 系统按 `userId` 执行 upsert，敏感字段（手机号、邮箱）按统一脱敏策略入库或入日志

### Requirement: Status-Driven Disable Handling
当主数据接口返回"对象不存在"或"状态失效"时，系统 MUST 按接口字段语义对本地记录做禁用 / 离职处理，禁止物理删除。

#### Scenario: 部门撤销
- **GIVEN** 主数据接口对某 `deptId` 返回 404 或状态字段 `status=DISABLED`
- **WHEN** 系统处理该事件
- **THEN** 本地部门记录被标记 `enabled = false`，保留历史引用关系，不删除行

#### Scenario: 员工离职
- **GIVEN** 主数据接口对某 `userId` 返回状态字段 `status=RESIGNED`
- **WHEN** 系统处理该事件
- **THEN** 本地员工记录被标记为禁用 / 离职，保留历史 `projectId` 关联与 `assignee` 字段映射

### Requirement: Pre-Launch Full Initialization
系统上线前 MUST 通过组织架构主数据接口完成至少一次基础数据全量初始化；初始化完成后再开启事件订阅以执行增量同步。

#### Scenario: 全量初始化执行
- **GIVEN** `OrganizationInitializationAppService.runFullInitialization(startAt, endAt)` 被触发
- **WHEN** 接口按 `listDepartmentsByWindow` + `listUsersByWindow` 分页拉取全量
- **THEN** 系统将所有部门、员工、必要的任职 / 职位明细按业务主键 upsert，并在 `organization_event_logs` 写入 `eventTopic=INIT, status=PROCESSED` 标记

#### Scenario: 重复触发不重复写库
- **WHEN** 同一时间窗的初始化被重复触发
- **THEN** 系统按业务主键 upsert，结果幂等，不产生重复行

#### Scenario: 初始化未完成禁止启用 SDK 订阅
- **GIVEN** `organization_event_logs` 中不存在 `INIT, PROCESSED` 标记
- **WHEN** 服务尝试启用 SDK 订阅
- **THEN** 启动失败并提示"上线前未完成基础数据初始化"，禁止开启增量订阅

### Requirement: Periodic Reconciliation
系统 MUST 提供每日定时对账能力，按时间窗拉取最近 N 天的部门 / 员工变更，与本地数据比对并自动补偿遗漏 / 失败事件。

#### Scenario: 定时对账触发
- **GIVEN** Spring `@Scheduled` 在低峰期触发对账（默认 02:00）
- **WHEN** 系统调用 `listDepartmentsByWindow` + `listUsersByWindow` 取最近 3 天
- **THEN** 系统将上游差异写入 `organization_sync_items`，并对每条差异按主键 upsert

#### Scenario: 手工对账入口
- **WHEN** 管理员通过 `POST /api/integrations/organization/sync-runs` 触发自定义时间窗
- **THEN** 系统返回 `OrganizationSyncRunResponse` 包含统计与处理结果，调用方权限受限于 `ADMIN` / `MANAGER`

#### Scenario: 长期失败进入死信
- **WHEN** 一条事件 `retry_count` 超过阈值仍未成功
- **THEN** 系统将其标记为 `DEAD_LETTER` 并发出告警，需人工介入

### Requirement: Retry and Timeout Policy
组织架构主数据接口调用 MUST 设置 3-5 秒超时；任何瞬时失败（超时 / 5xx / 网络异常 / 数据库瞬时异常）MUST 进入指数退避重试，**禁止短时连续重试**，避免对上游造成放大冲击。

#### Scenario: 单次超时
- **WHEN** 单次主数据调用超过 `xiyu.integrations.organization.directory.read-timeout-ms`
- **THEN** 系统抛出 `OrganizationDirectoryHttpGatewayException`，事件标记 `FAILED`，下一次按退避时间重试

#### Scenario: 短时间多次变更
- **WHEN** 同一 `userId` / `deptId` 在 1 秒内连续多次事件触发
- **THEN** 系统串行处理（行级或分布式锁），保证最终查询接口的最新状态覆盖本地旧数据

### Requirement: Logging and Monitoring
所有事件处理 MUST 记录 `traceId`、`spanId`、`parentId`、`eventTopic`、`key`、`time`、`consumerGroup`、`status`、`elapsed`、`error?`；MUST 暴露 Micrometer 指标，并配置消费成功率 / 处理延迟 / 失败积压 / 主数据调用成功率 / 本地补偿成功率告警。

#### Scenario: 处理日志字段完整
- **WHEN** 任何事件被处理（成功 / 失败 / 重复 / 拒绝）
- **THEN** 日志输出至少包含上述字段，且敏感字段（手机号、邮箱、客户姓名）做脱敏

#### Scenario: 成功率持续低于阈值
- **GIVEN** 事件消费成功率持续低于 99%
- **WHEN** 监控指标采样
- **THEN** 系统触发告警，标识 `org_event_success_rate_low`

### Requirement: Configuration and Security Boundary
组织架构相关密钥、地址、白名单 MUST 通过配置中心或环境变量注入；MUST NOT 入库、MUST NOT 提交到代码仓库；HTTP 入口 MUST 校验 IP 白名单 + HMAC 签名 + 启停开关。

#### Scenario: 生产密钥来源
- **WHEN** 生产环境启动
- **THEN** `xiyu.integrations.organization.webhook-secret`、`directory.base-url`、SDK `broker.*` 等敏感配置全部来自环境变量或配置中心

#### Scenario: 测试 / 开发环境无生产数据
- **WHEN** dev / test profile 启动
- **THEN** 默认配置只能连接测试事件库与测试主数据接口，禁止连接生产域名
