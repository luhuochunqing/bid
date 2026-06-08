# 组织架构集成 Runbook

本文面向部署、联调、运维和验收。当前项目采用真实 API 单一路径 (API-only)。OA 流程创建和 CRM 客户接口不在本 Runbook 范围内。

## 当前状态

- 客户 `ClientSDK` 版本固定为 `release_0.0.2`；JAR 当前仍待从私服拉取验证，禁止伪造 SDK、注解或返回类型。
- 当前使用统一应用服务链路处理 SDK 事件、YAPI 回查、幂等、落库、重试和对账。
- 拿到真实 SDK 后，只新增 `infrastructure/sdk` adapter，adapter 不承载业务规则，只调用现有 application service。

## 环境变量

| Environment Variable | Required | Purpose | Notes |
| --- | --- | --- | --- |
| `XIYU_ORG_SYNC_ENABLED` | Yes | 组织架构集成总开关 | 测试/生产开启前需完成 YAPI 与 SDK 输入冻结。 |
| `XIYU_ORG_EVENT_SDK_ENABLED` | Yes after SDK delivery | SDK 事件消费开关 | SDK JAR 缺失时保持关闭。 |
| `XIYU_ORG_EVENT_CONSUMER_GROUP` | Yes after SDK delivery | SDK consumer group | 命名规则待客户确认，禁止多环境共用生产 group。 |
| `XIYU_ORG_EVENT_SERVICE_NAME` | Yes after SDK delivery | SDK `client.register.serviceName` | 测试参考 `BidSystemOrgConsumer`，生产需按西域命名规范确认。 |
| `XIYU_ORG_EVENT_SERVER_REGISTER_URL` | Yes after SDK delivery | SDK `client.register.serverRegisterUrl` | 测试参考 `http://event-busserver-test.ehsy.com`，生产由西域另行提供。 |
| `XIYU_ORG_EVENT_ENABLE_REGISTER` | Yes after SDK delivery | SDK `client.register.enableRegister` | 默认 `false`，只有真实 SDK/JAR 与环境参数齐备后开启。 |
| `XIYU_ORG_EVENT_RENEWAL_INITIAL_DELAY` | No | SDK `client.renewal.initialDelay` | 客户文档测试参考 `3`。 |
| `XIYU_ORG_EVENT_RENEWAL_PERIOD` | No | SDK `client.renewal.period` | 客户文档测试参考 `3`。 |
| `XIYU_ORG_EVENT_RENEWAL_DURATION_MS` | No | SDK `client.renewal.renewalDuration` | 客户文档测试参考 `3000`，单位毫秒。 |
| `XIYU_ORG_DIRECTORY_BASE_URL` | Yes for YAPI lookup | 组织架构 YAPI base URL | 真实 host 由客户交付，本文不编造。 |
| `XIYU_ORG_DIRECTORY_AUTH_TOKEN` | Yes if token auth | YAPI 不需要 Bearer token（基于网络白名单安全），此变量为外部环境备用。 |
| `XIYU_ORG_DIRECTORY_SOURCE_APP` | Yes if required | 调用方来源标识 | Header 名称和值待确认。 |
| `XIYU_ORG_DIRECTORY_TRACE_HEADER_NAME` | Pending | trace header 名称 | 待 YAPI 鉴权合同冻结后配置。 |
| `XIYU_ORG_DIRECTORY_SOURCE_HEADER_NAME` | Pending | source app header 名称 | 待 YAPI 鉴权合同冻结后配置。 |
| `XIYU_ORG_DIRECTORY_CONNECT_TIMEOUT_MS` | No | YAPI 连接超时 | 建议由生产压测后冻结。 |
| `XIYU_ORG_DIRECTORY_READ_TIMEOUT_MS` | No | YAPI 读取超时 | 建议由生产压测后冻结。 |
| `XIYU_ORG_RETRY_ENABLED` | Yes for production | 失败事件自动重试开关 | 未完成重试表结构和验收前不得宣称生产完成。 |
| `XIYU_ORG_RETRY_MAX_ATTEMPTS` | Yes for production | 最大重试次数 | 默认建议待压测和运维确认。 |
| `XIYU_ORG_RETRY_BATCH_SIZE` | Yes for production | 单批重试数量 | 需结合 YAPI 限流确认。 |
| `XIYU_ORG_RETRY_FIXED_DELAY_MS` | No | 重试扫描间隔 | 与告警阈值联动。 |
| `XIYU_ORG_RECONCILIATION_ENABLED` | Yes for production | 每日低峰对账开关 | 初始默认关闭，完成 UAT 后开启。 |
| `XIYU_ORG_RECONCILIATION_CRON` | Yes for production | 每日对账 cron | 推荐低峰窗口，具体时间由生产排期冻结。 |
| `XIYU_ORG_RECONCILIATION_LOOKBACK_DAYS` | Yes for production | 对账回看天数 | 计划口径为 1-3 天，最终值待客户确认。 |

## 事件 Topic

| Topic | Identifier | Expected Action |
| --- | --- | --- |
| `BaseOssDept` | `data.deptId` | 回查部门详情并按 `externalDeptId` 幂等 upsert/disable。 |
| `BaseOssUser` | `data.userId` | 回查员工详情并按 `externalUserId` 幂等 upsert/disable。 |

注意: 事件 `data` 不是主数据，只能作为回查标识和追踪信息。

## YAPI 端点

| Capability | Endpoint Name | Method | Path | Status |
| --- | --- | --- | --- | --- |
| Department detail | 部门详情 | POST | /subscription/msg/dept | ✅ 已冻结 |
| User detail | 员工详情 | POST | /subscription/msg/user | ✅ 已冻结 |
| Department window | 部门时间窗 | POST | /subscription/msg/getDeptByTimeWindow | ✅ 已冻结 |
| User window | 员工时间窗 | POST | /subscription/msg/getUserByTimeWindow | ✅ 已冻结 |

字段、鉴权 Header、响应 envelope、禁用/查无语义以 `docs/integration/organization-directory-yapi-mapping.md` 为准。

兼容口径: 当前 YAPI 响应 envelope 尚未冻结。若响应体没有 `code` 字段，系统会按直接 payload 兼容处理，并从 `data`、`result` 或根节点读取部门/员工字段。契约冻结后应收紧 envelope 校验，避免异常 JSON 被误判为成功。

## 重试策略

- 可重试: YAPI timeout、连接失败、5xx、临时限流、数据库瞬时失败。
- 不可重试: 鉴权失败、合同字段缺失、未知 Topic、缺少 `deptId` / `userId`。
- 幂等: 同一事件重复到达应稳定返回成功，不重复写部门或员工。
- 退避: 采用指数退避并设置上限；耗尽最大次数后进入 dead letter，等待人工处理。
- 恢复: 首次处理或自动重试 claim 后若服务中断，stale `PROCESSING` 会回到 `PENDING_RETRY`；死信手工重放中断则回到 `DEAD_LETTER`，避免绕过人工复核。
- 观测: 记录事件状态、retry count、next retry time、last error code，但不得记录 token、手机号、邮箱完整值。

## 每日低峰对账

1. 在低峰窗口运行部门时间窗同步和员工时间窗同步。
2. 时间窗使用 `startAt`、`endAt`、`pageNo`、`pageSize` 请求 YAPI。
3. 空页结束本次同步。
4. 每次同步写入 sync run 和 sync item 证据，保留窗口、成功数、失败数、差异摘要。
5. 对账发现查无或禁用时，按已冻结的禁用/查无语义处理，未冻结前不得自动删除本地数据。

## 手工重同步

手工重同步用于客户补数、单条纠错和 UAT 验收。死信事件优先使用 event log 原地重放，命令中的 token、host 和 ID 由运维现场提供。

```bash
curl -X POST "$BACKEND_BASE_URL/api/integrations/organization/resync/departments/$DEPT_ID" \
  -H "Authorization: Bearer $ORG_ADMIN_JWT" \
  -H "X-Request-Id: $REQUEST_ID"
```

```bash
curl -X POST "$BACKEND_BASE_URL/api/integrations/organization/resync/users/$USER_ID" \
  -H "Authorization: Bearer $ORG_ADMIN_JWT" \
  -H "X-Request-Id: $REQUEST_ID"
```

```bash
curl -X POST "$BACKEND_BASE_URL/api/integrations/organization/operations/dead-letters/$EVENT_KEY/replay" \
  -H "Authorization: Bearer $ORG_ADMIN_JWT" \
  -H "X-Request-Id: $REQUEST_ID"
```

操作要求:

- 每次手工重同步必须能定位操作人、时间、对象 ID、结果和失败原因。
- `deptId` / `userId` 不得写成测试账号或真实个人信息示例。
- 单用户/单部门手工重同步不进入事件重试或死信机制；失败只写入 sync run/item 证据并返回错误，后续由运维判断是否再次触发。
- 死信事件原地重放如果 claim 后服务中断，stale `PROCESSING` 会回到 `DEAD_LETTER`，继续等待人工复核。
- 失败后先查 sync item 和 event log，再判断是否需要原地重放事件、客户重新投递或补齐 YAPI 数据。

## 运维状态接口

- `pendingRetryCount`: 当前等待自动重试的事件数。
- `deadLetterCount`: 当前需要人工复核或重放的死信事件数。
- `failedCount`: 遗留失败计数，仅统计重试/死信拆分前残留的 `FAILED` 状态，新流程不再写入该状态。

## 日志脱敏

- 禁止记录 YAPI token、签名原文、SDK credential、测试账号密码。
- 手机号只允许保留前 3 后 4，中间用 `****`。
- 邮箱只允许保留首字符和域名。
- 事件日志允许记录 `traceId`、`spanId`、`parentId`、`eventSource`、`eventTopic`、`time`、`key`、`deptId` / `userId`。
- 错误日志记录远端错误码、分类和 trace，不记录完整响应体中的个人字段。

## 告警阈值

以下阈值必须在生产启用前由客户、交付和运维共同冻结:

| Metric | Threshold |
| --- | --- |
| Event success rate | TBD |
| Event processing latency P95/P99 | TBD |
| YAPI HTTP failure rate | TBD |
| YAPI HTTP latency P95/P99 | TBD |
| Pending retry count | TBD |
| Dead letter count | TBD |
| Daily reconciliation diff count | TBD |
| Consecutive reconciliation failures | TBD |

## 验收用例

| Case | Name | Steps | Expected Result |
| --- | --- | --- | --- |
| TC-01 | SDK startup registration | 在真实 SDK JAR、`@AcceptEvent` 包和 `consumerGroup` 冻结后启动服务。 | `BaseOssDept` 和 `BaseOssUser` 订阅注册成功；SDK 缺失时该用例标记 blocked，不以假 SDK 通过。 |
| TC-02 | Department event | 发送或接收包含 `data.deptId` 的 `BaseOssDept` 事件。 | 系统回查部门详情，写入/更新本地部门，事件状态成功，日志不含敏感字段。 |
| TC-03 | User event | 发送或接收包含 `data.userId` 的 `BaseOssUser` 事件。 | 系统回查员工详情，写入/更新本地用户和部门关系，未知角色不自动提权。 |
| TC-04 | Duplicate event | 重放同一 trace/key/topic 的事件。 | 返回成功且标记 duplicate，不重复写入本地记录。 |
| TC-05 | Timeout/5xx retry | 让 YAPI 返回 timeout 或 5xx。 | 事件进入 retry，按退避策略重试；达到最大次数后进入 dead letter 并可人工重同步。 |
| TC-06 | Missing field | 发送缺少 `deptId` 或 `userId` 的事件，或 YAPI 返回缺少必填字段。 | 事件被分类为合同错误或不可重试失败，错误信息可定位但不泄漏个人字段。 |
| TC-07 | Initialization | 在低峰窗口执行部门和员工时间窗初始化。 | 分页同步完成，空页结束，sync run/item 留痕，失败项可重试或手工处理。 |
| TC-08 | Reconciliation | 开启每日低峰对账，回看已确认天数。 | 差异被记录并按冻结语义修复；超过阈值触发告警。 |

## 上线前检查

- YAPI 四类端点、鉴权 Header、响应 envelope、字段映射已冻结。
- ClientSDK 坐标和版本已冻结为 `com.ehsy.eventlibrary:ClientSDK:release_0.0.2`；上线前必须完成私服拉包、`@AcceptEvent` 包、`EventResult` 类型、consumer group 命名验证。
- TC-01 到 TC-08 在客户测试环境完成并留证。
- CRM 和 OA 未被标记为组织架构集成交付内容。

## 已知联调风险

- `maven.ehsy.com/nexus` 当前在本地验证中可解析到内网地址，但 HTTPS 入口会关闭 TLS 握手，HTTP 入口返回空响应；需西域确认网络白名单、协议和完整 releases 仓库路径。
