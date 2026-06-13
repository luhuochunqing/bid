# crm-customer-client Specification

## Purpose
TBD - created by archiving change add-xiyu-org-event-sdk-and-crm-client. Update Purpose after archive.
## Requirements
### Requirement: CRM Token Acquisition and Caching
系统 MUST 通过 `applyToken` 接口获取 CRM 访问 Token，并将 Token 缓存到本地（内存级），在过期前自动续约；多线程并发调用 MUST 通过单飞机制保证一次 token 申请只发起一次。

#### Scenario: 首次申请并缓存
- **GIVEN** 本地 Token 缓存为空
- **WHEN** 任意调用方需要发起 CRM 出向请求
- **THEN** 系统调用 `applyToken` 取得 Token，写入本地缓存并附加 TTL，后续请求直接复用

#### Scenario: 过期前续约
- **GIVEN** Token 缓存剩余有效期低于阈值（默认 `tokenCacheTtlSeconds * 0.2`）
- **WHEN** 任一出向请求被发起
- **THEN** 系统异步触发续约调用，请求继续使用旧 Token 不阻塞

#### Scenario: 并发申请单飞
- **WHEN** 多个线程同时遇到 Token 缺失
- **THEN** 仅一个线程实际调用 `applyToken`，其余线程等待结果共享，不重复打到 CRM

#### Scenario: 401/403 强制清理
- **WHEN** 任一 CRM 出向调用返回 401 或 403
- **THEN** 系统立即清理本地 Token 缓存并重新 `applyToken`，本次请求重试一次

### Requirement: CRM Token Logout
系统 MUST 提供 `logout` 接口调用，在管理员触发或服务下线时作废 Token；调用成功后立即清空本地缓存。

#### Scenario: 管理员主动登出
- **WHEN** 管理员调用登出入口
- **THEN** 系统调用 CRM `logout` 接口并清空 Token 缓存，后续请求触发重新申请

#### Scenario: 服务停机
- **WHEN** Spring 容器收到 `SmartLifecycle.stop()` 或 `@PreDestroy`
- **THEN** 系统尝试调用 `logout`（best-effort），失败仅记录日志不阻塞停机

### Requirement: Customer Fuzzy Search
系统 MUST 提供按公司名称模糊查询 CRM 存量有效客户的能力，结果按公司名称长度升序排序（短名靠前），返回前 20 条。

#### Scenario: 输入名称返回候选列表
- **GIVEN** 用户在新建项目页输入"某某客户"
- **WHEN** 前端调用本系统 `GET /api/integrations/crm/customers?keyword=...`
- **THEN** 系统调用 CRM 模糊查询接口，返回最多 20 条候选 `{customerId, customerName, ...}`，按 `customerName.length` 升序

#### Scenario: 空关键字拒绝
- **WHEN** `keyword` 为空或仅含空白字符
- **THEN** 系统返回 HTTP 400，禁止把空查询透传到 CRM

#### Scenario: CRM 业务失败
- **WHEN** CRM 返回 `code != 0`
- **THEN** 系统抛出 `CrmBusinessException`，不重试，返回 502 与脱敏后的错误信息

### Requirement: Customer Owner Lookup
系统 MUST 支持按公司 ID 列表批量查询 CRM 客户负责人 / 客户经理，用于项目页负责人列表展示。

#### Scenario: 批量负责人查询
- **GIVEN** 调用方提供 `customerIds = [c1, c2, c3, ...]`
- **WHEN** 调用 `GET /api/integrations/crm/customer-owners?customerIds=...`
- **THEN** 系统调用 CRM 批量负责人接口，按 customerId 聚合返回 `{customerId, owners: [{userId, userName, mobile?, ...}]}`

#### Scenario: customerIds 超长限流
- **WHEN** 一次请求 `customerIds.size > 50`
- **THEN** 系统拆分为多次后端调用并合并结果，调用方接收一份合并响应

### Requirement: Menu Tree Retrieval
系统 MUST 通过 CRM 接口按系统类型获取菜单树，用于本平台权限初始化或菜单同步。

#### Scenario: 按系统类型取菜单
- **GIVEN** 调用方提供 `systemType=BID`
- **WHEN** 调用 `GET /api/integrations/crm/menu-tree?systemType=BID`
- **THEN** 系统调用 CRM 菜单树接口，返回完整树结构

#### Scenario: 菜单结果本地缓存
- **WHEN** 同一 `systemType` 在 `menuCacheTtlSeconds` 内重复查询
- **THEN** 系统从本地缓存返回，避免每次请求都打到 CRM

### Requirement: Employee Info By Token
系统 MUST 提供"按 Token 获取员工信息"能力，作为 WeCom OAuth 之外的辅助身份获取通道。

#### Scenario: 获取当前员工信息
- **WHEN** 调用方携带 CRM Token 调用 `GET /api/integrations/crm/me`
- **THEN** 系统调用 CRM `getEmployeeByToken`，返回 `{userId, userName, mobile?, departmentId, ...}`

#### Scenario: Token 失效
- **WHEN** CRM 返回 401 / Token 失效
- **THEN** 系统按 §"CRM Token Acquisition and Caching" 的策略清理并重新申请，再原样重试一次

### Requirement: Message Sending (WeCom + In-System)
系统 MUST 支持通过 CRM `sendMessage` 接口同时发送企微消息与站内消息，用于审批 / 评分 / 标书状态通知等业务事件触发。

#### Scenario: 单接收人发送
- **WHEN** 调用 `POST /api/integrations/crm/messages` 携带 `{targetUserId, content, channels: [WECOM, INTRA]}`
- **THEN** 系统调用 CRM `sendMessage`，按 channels 透传发送方式，返回 CRM `messageId`

#### Scenario: 批量接收人发送
- **WHEN** 携带 `targetUserIds: [u1, u2, ..., uN]`
- **THEN** 系统按 CRM 单次发送上限拆分，所有发送结果聚合返回；任一失败不影响其他成功

#### Scenario: 消息内容长度限制
- **GIVEN** `content.length > maxContentLength`
- **THEN** 系统返回 HTTP 400，禁止把超长消息透传到 CRM

### Requirement: Common Outbound Request Contract
所有 CRM 出向 HTTP 调用 MUST 遵守以下统一契约：HTTPS、`Authorization: Bearer <token>`、`Content-Type: application/json`、UTF-8 编码、CRM 响应统一解析为 `{code, msg, data, success}` 四字段，`code == 0 && success == true` 才视为业务成功。

#### Scenario: 标准请求成功
- **WHEN** 任一 CRM 出向调用成功
- **THEN** `CrmHttpClient` 校验 `code == 0 && success == true`，从 `data` 字段抽取业务返回交给调用方

#### Scenario: 业务失败语义
- **WHEN** `code != 0` 或 `success == false`
- **THEN** 系统抛出 `CrmBusinessException`，错误信息只暴露 `msg` 中的业务文案，不暴露 stacktrace / token

#### Scenario: traceId 透传
- **WHEN** 调用 CRM
- **THEN** 系统在请求头补一份本地 `traceId`，便于双方对账定位问题

### Requirement: Retry and Timeout Policy
CRM 出向调用 MUST 设置 3-5 秒首次超时；瞬时失败（5xx、网络异常、超时）MUST 进入指数退避重试，最多 3 次；业务失败（4xx、`code != 0`）MUST NOT 重试。

#### Scenario: 5xx 重试
- **WHEN** CRM 返回 502
- **THEN** 系统按 `backoff = baseDelay * 2^retryCount` 退避，最多重试 3 次，仍失败则抛 `CrmTransientException`

#### Scenario: 4xx 不重试
- **WHEN** CRM 返回 400 或 `code != 0`
- **THEN** 系统不重试，直接抛 `CrmBusinessException`，避免错误请求被放大投递

### Requirement: Secret Management
CRM 客户端密钥、`appKey`、`appSecret`、`baseUrl`、Token MUST 通过配置中心或环境变量注入，MUST NOT 入库，MUST NOT 提交到代码仓库；生产环境必须使用 HTTPS。

#### Scenario: 生产配置来源
- **WHEN** 生产 profile 启动
- **THEN** `xiyu.integrations.crm.app-key`、`app-secret`、`base-url` 全部来自环境变量或配置中心，本地 `application-prod.yml` 不允许写入明文

#### Scenario: HTTP 协议拒绝
- **WHEN** 配置的 `xiyu.integrations.crm.base-url` 以 `http://` 开头且非测试 profile
- **THEN** 系统启动失败，并提示"CRM 生产必须使用 HTTPS"

### Requirement: Sensitive Data Masking
CRM 出向 / 入向的日志、异常 message MUST 对手机号、邮箱、证件号、银行账号、客户联系人姓名、Token 等敏感字段进行脱敏。

#### Scenario: 日志脱敏
- **WHEN** `CrmHttpClient` 写入请求 / 响应日志
- **THEN** 手机号格式输出 `138****1234`、邮箱输出 `a***@example.com`、Token 输出 `tk_***<尾 4 位>`，banker_account / id_card 全脱敏

#### Scenario: 异常 message 不泄漏
- **WHEN** 抛出 `CrmBusinessException` 或 `CrmTransientException`
- **THEN** 错误信息中不出现完整 Token、原始密钥、未脱敏的客户联系人字段

### Requirement: Observability and Metrics
系统 MUST 暴露 CRM 出向调用的 Micrometer 指标：调用次数、成功率、P95/P99 延迟、按接口路径分桶；指标 MUST 与组织架构事件指标共同接入告警体系。

#### Scenario: 指标采集
- **WHEN** 任一 CRM 出向接口被调用
- **THEN** 系统记录 `crm_outbound_calls_total{path,status}`、`crm_outbound_latency_seconds{path}`，路径维度按接口划分

#### Scenario: 持续失败告警
- **WHEN** 任一接口 5 分钟内失败率高于 5%
- **THEN** 监控系统触发 `crm_outbound_failure_rate_high` 告警，包含接口名 / 错误码 / 最近一次 traceId

