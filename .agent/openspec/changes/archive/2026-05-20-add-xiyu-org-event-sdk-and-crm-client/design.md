# Design: add-xiyu-org-event-sdk-and-crm-client

## Context

### 当前现状
- 组织架构（参考 `backend/src/main/java/com/xiyu/bid/integration/organization/`）：
  - 已有 HTTP 中转入口（`OrganizationEventWebhookController`）+ HMAC 签名校验 + IP 白名单 + 启停开关
  - 已有 `OrganizationEventInboxService`（幂等 inbox，写入 `organization_event_logs`）
  - 已有 `OrganizationDirectoryHttpGateway`（实现 deptId/userId 回查 + 时间窗分页）
  - 已有 `OrganizationDepartmentSyncWriter`/`OrganizationUserSyncWriter`（业务主键 upsert）
  - 已有 `OrganizationSyncRunController`（手工/定时时间窗补偿对账）
  - **缺**：`OrganizationEventSdkConsumerPort` 只有空接口；`backend/pom.xml` 没有 `com.ehsy.eventlibrary:ClientSDK` 依赖；`application.yml` 没有 SDK 注册/续约/broker 配置；上线前全量初始化没有独立的用例
- CRM（参考 `backend/src/main/java/com/xiyu/bid/security/controller/CrmWebhookController.java`、`CrmPermissionSyncService.java`）：
  - 已有入向 webhook（`POST /api/webhooks/crm/permissions`），用 token 鉴权接收客户权限同步
  - 这条入向链路与本提案要求的"出向调用 CRM 7 个接口"是两件事，**保留不改**
  - **缺**：没有任何"投标系统主动调 CRM"的 client；没有 Token 缓存；没有出向接口契约

### 客户文档要点
- 组织架构（V1.0 文档）：
  - SDK 坐标 `com.ehsy.eventlibrary:ClientSDK`，参考版本 `release_0.0.2`
  - 必须订阅 `BaseOssDept`、`BaseOssUser` 两个 Topic
  - 事件 `data` 只携带 `deptId` / `userId`，**不可作为主数据使用**
  - 收到事件 → 解析 `eventTopic` → 调用对应主数据接口 → upsert / 失效
  - 处理成功返回 `EventResult code=200`，需重试返回 `code=500`
  - 上线前必须通过组织架构接口完成一次基础数据初始化
- CRM（CRM 接口文档）：
  - 协议 HTTPS，鉴权 Authorization Header Token
  - 响应统一为 `code/msg/data/success` 四字段
  - 7 个接口：applyToken（缓存 Token + 设有效期）、logout（作废 Token）、客户模糊查询（按名称返回前 20 条）、客户负责人列表（按公司 id 批量）、菜单树（按系统类型）、员工信息（按 Token）、发送消息（企微 + 站内）

## Goals / Non-Goals

### Goals
- 为"事件库 SDK 订阅 + 组织架构接口同步"主链路定义可执行的基线契约，使得 jar 一到位即可按本规范完成实现与联调。
- 把已实现的 HTTP 中转、幂等 inbox、回查 gateway、时间窗对账纳入同一份能力规范，避免双轨表述分裂。
- 为 CRM 7 个出向接口定义统一的客户端骨架契约（鉴权 / 缓存 / 重试 / 错误处理 / 日志脱敏），消除目前完全空白的状态。

### Non-Goals
- 不规范 OA 流程创建（`POST /oaWorkflow/createWorkflow`）、OA 回调或 8 个流程映射——按用户要求另起 change。
- 不规范企业微信 OAuth2 登录（已由 `add-wecom-oauth2-callback` 覆盖）。
- 不规范 CRM 入向 webhook（`/api/webhooks/crm/permissions`）——当前实现保留，与出向客户端独立。
- 不引入新的数据库表（CRM 客户端无状态，组织架构复用现有迁移）。

## Decisions

### D1: SDK 主链 + HTTP 中转灾备 双路并存，幂等 inbox 共用
- **决策**：`OrganizationEventSdkConsumerPort` 作为 SDK 接入唯一端口；SDK 与 HTTP 中转 controller 均委托给 `OrganizationDirectorySyncAppService` 处理，**共用同一份事件 inbox 与 writer**，对调用方只是"如何把消息送进系统"的差异。
- **理由**：客户文档明确"事件库 SDK 订阅"是主链路；但 jar 当前缺失，HTTP 中转既是上线前的联调手段也是 SDK 不可用时的灾备入口。两条路必须落到同一份幂等键、同一份解析逻辑，避免双轨写库导致脏数据。
- **替代方案**：(1) 删除 HTTP 中转，等 SDK 到位再上线——风险是 SDK 联调失败时没有回退路径；(2) SDK / HTTP 各自维护一份 inbox——风险是同一事件双路投递时去重失败。

### D2: 事件 `data` 仅作为触发器，主数据全部从组织架构接口回查
- **决策**：解析事件后只取 `eventTopic`、`key`、`data.deptId` 或 `data.userId`，立即调用 `fetchDepartmentByDeptId` / `fetchUserByUserId` 取最新明细；回查空则按"接口未查询到 = 已禁用"处理，**不物理删除**。
- **理由**：客户文档第 2.2 节明令禁止直接用事件 payload 作主数据；这是客户对接的硬约束。
- **替代方案**：用事件 payload 兜底——会与文档冲突且在客户侧重发事件时引入脏数据。

### D3: 幂等键采用 `traceId + spanId + eventTopic`，业务主键采用 `deptId` / `userId`
- **决策**：事件层幂等用 `traceId + spanId + eventTopic`（覆盖事件库重投递场景）；本地业务表 upsert 用 `deptId` / `userId`，禁止使用自增 ID 作为跨系统主键。
- **理由**：客户文档第 9 节明确这两层不同的幂等口径。
- **替代方案**：用 `eventTopic + key + time` 作为兜底键，已在现有 `OrganizationEventKeyFactory` 中保留。

### D4: 初始化 vs 增量 vs 对账，三条路径明确分离
- **决策**：定义三条独立用例：
  - **Init**：上线前通过 `listDepartmentsByWindow` / `listUsersByWindow` 拉满全量基线（按天/按周分页），结果写入正式表，并落一条"初始化完成"标记。
  - **Incremental**：SDK 主链 + HTTP 灾备，按事件触发回查。
  - **Reconciliation**：每日低峰拉最近 N 天时间窗，对账并对失败/缺漏发起重试。
- **理由**：文档第 8 节明确这三条路径不能合一；现有 `OrganizationSyncRunController` 已覆盖第三条，本提案需要补齐第一条。

### D5: CRM 客户端为无状态调用，Token 缓存默认内存级
- **决策**：用 Spring Cache（Caffeine / 简单 `ConcurrentHashMap`）缓存 Token，TTL 略短于服务端有效期；多实例不强制共享 Token，每个实例独立调用 `applyToken` 续约。
- **理由**：CRM 7 个接口都是同步调用，无需持久化；多实例同时持有不同 Token 不会破坏服务端语义。
- **替代方案**：用 Redis 共享 Token——可以减少 `applyToken` 调用，但引入了跨服务依赖与失效复杂度，本基线先不做。
- **延展点**：如果客户文档明确单点登录约束或 Token 限发，再升级到共享缓存。

### D6: CRM 错误码映射统一走 `code/msg/data/success`，业务失败不重试
- **决策**：HTTP 5xx / 网络超时进入指数退避重试（最多 3 次，3-5 秒首次超时），4xx 与业务 `code != 0` 直接抛出 `CrmBusinessException`（不重试），由上游决定降级。
- **理由**：文档明确 token / 业务参数失败不应自动重试；与组织架构事件失败语义保持一致。

### D7: 配置与密钥不入库，按环境变量 + 配置中心注入
- **决策**：所有上游地址、签名密钥、Token、IP 白名单都通过 `application-{profile}.yml` + 环境变量注入；不写入 MySQL，不提交到代码仓库。
- **理由**：客户文档第 12 节"安全与合规"对此明确要求。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
|---|---|---|
| `ClientSDK` jar 客户长期不交付 | SDK 主链无法实现 | 本提案规范了端口（`OrganizationEventSdkConsumerPort`），实现可延后；同时保留 HTTP 中转灾备作为上线退路 |
| 事件库消息乱序 / 重投递 | 本地数据短暂不一致 | 使用 `traceId+spanId+eventTopic` 幂等 + `deptId/userId` upsert + 日常对账三层兜底（文档第 9 节亦要求） |
| 客户主数据接口短时不可用 | 大量事件失败积压 | 指数退避 + 死信队列 + 监控告警；本地补偿任务接管重试 |
| CRM Token 同步失效（密钥换轮 / 客户端被踢出） | 出向接口全线 4xx | 检测到 401/403 立即清除缓存并重新 `applyToken`；超过阈值告警 |
| 敏感字段（手机号、客户银行账号）误入日志 | 合规风险 | 统一封装 `Masker` 工具，写日志前过滤；ArchUnit 测试禁止 `log.info(原始 phone)` 模式 |
| 多实例同时 `applyToken` 造成上游限流 | 启动期短暂可用性下降 | 进程内单飞（`CompletableFuture.allOf` 或 `Singleflight`），或 Token 首次拉取互斥锁 |

## Migration Plan

1. **依赖准备**（等待客户）：拿到 `ClientSDK` jar 后，通过 Nexus 私服或 `system` scope 引入 `backend/pom.xml`。
2. **配置接入**：先在 `application-dev.yml` 写入测试 broker / zk / serverRegisterUrl / consumerGroup，验证服务注册成功（TC-01）。
3. **SDK 适配器**：实现 `OrganizationEventSdkConsumer`（实现 `OrganizationEventSdkConsumerPort`，方法上挂 `@AcceptEvent`），委托给现有 `OrganizationDirectorySyncAppService`。
4. **初始化用例**：新增 `OrganizationInitializationAppService`，在上线前手工/脚本触发一次全量；HTTP 入口（管理后台）+ CLI 入口择一。
5. **日常对账**：现有 `OrganizationSyncRunController` 接定时任务（Quartz / Spring `@Scheduled`），每日低峰执行最近 3 天时间窗。
6. **CRM 客户端**：新增 `backend/src/main/java/com/xiyu/bid/integration/crm/` 模块，按本规范实现 7 个出向方法 + Token 缓存。
7. **联调验收**：按 spec.md 中的 Scenarios 逐项过 TC-01 ~ TC-08 与 CRM 7 个接口的最小样例。
8. **回退**：SDK 路径异常时关闭 `xiyu.integrations.organization.sdk.enabled`，回到 HTTP 中转 + 对账兜底；CRM 出向异常时关闭依赖该数据的前端页面（fallback 到本地缓存数据）。

## Open Questions

1. `ClientSDK` jar 的最终交付方式（Nexus 私服还是离线 jar）？决定 `pom.xml` 写法。
2. 客户是否允许多实例（前端 / Codex / Gemini agent 各自的 worktree）共用一个 `consumerGroup`？文档第 4.3 节说"同一系统多实例可共用，不同系统不可共用"，需确认开发环境是否算"同一系统"。
3. CRM `applyToken` 的返回 TTL 是固定值还是上游下发？决定缓存策略。
4. CRM `sendMessage` 是否需要保留发送流水（用于审计 / 重发）？本基线先按"无状态"处理，如有需求再加 `crm_message_log` 表。
5. 组织架构接口失败的"长期失败队列"目前没有，是新建表 `organization_event_dead_letters` 还是复用 `organization_event_logs.status=FAILED + retry_count`？倾向后者，需确认。
