# 设计：独立企微消息发送能力（按工号，走 CRM `/common/sendMessage`）

- 日期：2026-06-16
- 作者：Gemini agent（brainstorm 会话产出）
- 状态：待评审
- 关联：`backend/src/main/java/com/xiyu/bid/{wecom,crm,notification}`

## 1. 背景与问题

西域集团提供一个统一消息服务接口 `POST /common/sendMessage`，按**工号**（`recipientNos`）派发消息，`flag` 控制通道：`1` = 企微+站内信、`2` = 站内信、`3` = 企微。该接口在 `crm` 模块已有封装：`CrmMessageService.sendMessage(recipientNos, title, content, flag)`，经 `CrmHttpClient` 调用，含 5xx 重试与 CRM token 续期。`CrmController` 在 `POST /api/xiyu/crm/messages` 暴露了同步代理。

当前本平台的**企微**提醒出口与**站内信**收件箱耦合在一起：

```
业务模块 → NotificationApplicationService.createNotification（写站内信 UserNotification）
         → 发布 NotificationCreatedEvent
         → NotificationDeliveryTaskListener（AFTER_COMMIT，每收件人写一条投递任务）
         → NotificationDeliveryJobService（@Scheduled 轮询 + 重试/DLQ/可观测）
         → WeComPushService.push
            → 解析 User.wecomUserId
            → WeComMessagePublisher.sendTextMessage（直连企微 API，自管 corpId/agentId/secret/access_token）
```

企微发送被埋在站内信的出站管线里，业务模块想单独发一条企微消息，没有干净、独立的入口；同时站内信与企微两个通道在 `notification/outbound` 里互相绑定。

## 2. 关键架构事实（已核对）

1. **所有提醒生产者都汇入 `NotificationApplicationService.createNotification` 这一个入口**：`TenderPendingAssignmentNotifier`、`TenderEvaluationNotificationService`、`WarehouseExpiryScanTask`、`ProjectNotificationService`、`MentionApplicationService`、`CasePrecipitationNotifier`、资质/品牌到期扫描、CA 派发等，无一例外。
2. **全仓库直连企微发消息只有 `WeComPushService.push` 一处**：`WeComMessagePublisher` 仅被 `WeComPushService` 与 `WeComIntegrationAppService`（连通性探测/admin）引用。
3. `User` 实体同时具备 `wecomUserId`（企微绑定）与 `employeeNumber`（工号）字段；组织架构同步已普遍填充 `employeeNumber`。
4. `notification/outbound` 的投递管线（任务表 + 定时轮询 + 重试 + DLQ + 可观测 + 出站日志）成熟且与传输方式无关。

## 3. 目标 / 非目标

### 目标
- **G1**：提供独立、可复用的企微消息发送能力：任何模块按工号即可发企微消息，不依赖 `notification` 站内信管线。
- **G2**：把 `notification/outbound` 镜像企微的那条路改为调用该独立能力，解除"站内信耦合企微"的关系。
- **G3**：站内信仍由本平台 `notification` 收件箱单一承担；独立能力只发企微（`flag=3`），不重复发站内信。

### 非目标
- 不改站内信收件箱的存储/查询/已读 API。
- 不动企微 OAuth 登录（`WeComAuthController`）与 `WeComApiClient`/`WeComMessagePublisher` 本身（仍供连通性探测/admin 使用，只是通知链路不再调用）。
- 不引入站内信经 CRM 发送（`flag=1/2`）的路径。
- 本期不加 feature flag / 回退路径（单一能力，保持简单）。
- 本期不做 DB schema 变更。

## 4. 设计决策

### D1 — 企微发送独立成块，放在新 `wecom` 模块
新建顶层包 `com.xiyu.bid.wecom`，作为"企微消息发送"这一独立能力的归属。它对外暴露按工号发送企微的入口，传输委托给既有 `crm` 模块的 HTTP 鉴权基础设施（`CrmMessageService` → `CrmHttpClient`），不重复造轮子。

依赖方向：`wecom → crm`，且 `wecom` **不依赖** `notification`。`notification/outbound` 反向依赖 `wecom`。无环。

### D2 — 独立能力只发企微，固定 `flag=3`
该能力名为"企微发送"，语义即 `flag=3`（仅企微）。站内信职责不在本能力范围（由 `notification` 收件箱承担）。`flag` 在 `WecomMessageSender` 内固定为常量 `3`，不作为调用方参数暴露，避免误触发站内信重复。

### D3 — `notification/outbound` 改调独立能力（解耦，不删管线）
`WeComPushService.push` 的站内信镜像-到-企微编排（任务/重试/DLQ/出站日志）保留；只把"收件人解析 + 传输调用"两点改为用工号 + 调 `WecomMessageSender`。入口签名不变，`NotificationDeliveryJobService` 零改动。

### D4 — 通道彻底分离
最终形态按通道解耦：站内信 = `notification` 模块；企微 = `wecom` 模块。业务模块按需调对应通道，不再有"站内信耦合企微镜像"的强绑定。

## 5. 架构

```
任何业务模块（标讯提醒 / 仓库到期 / 审批 / mention …）
        │  谁需要企微，谁按工号直接调
        ▼
  ┌──────────────────────────────────────────┐
  │  wecom 模块（新·独立）                    │   ← 本期核心交付（G1）
  │  WecomMessageSender @Service              │
  │   · send(List<String> 工号, title, content)│
  │   · 内部 flag=3                           │
  │   · 返回 WecomSendResult                  │
  └──────────────────┬───────────────────────┘
                     │ 委托传输 + 鉴权（复用，不重造）
                     ▼
  crm 模块（既有）：CrmMessageService → CrmHttpClient → POST /common/sendMessage
                                                  ↑ CrmAuthService 管 token（缓存/续期/401 重刷）


notification/outbound（既有管线，G2 改传输点）：
  … → NotificationDeliveryJobService → WeComPushService.push
                                         · 收件人 = User.employeeNumber（工号）
                                         · 传输 = wecomMessageSender.send(...)
                                         · 结果 = WecomSendResult → NotificationDeliveryResult
```

## 6. 组件清单

### 新增（`com.xiyu.bid.wecom`）

| 文件 | 类型 | 职责 |
|---|---|---|
| `WecomMessageSender.java` | `@Service` | 唯一入口：`WecomSendResult send(List<String> recipientNos, String title, String content)`；内部以 `flag=3` 调 `CrmMessageService.sendMessage`，映射 `CrmApiResponse → WecomSendResult` |
| `WecomSendResult.java` | record | `(boolean success, int code, String message)`；本模块自有结果类型，不引用 `notification` |

`WecomMessageSender` 约定：
- `flag` 固定 `3`，以命名常量 `FLAG_WECOM_ONLY = 3` 表达。
- 入参 `recipientNos` 为工号列表；空列表由调用方或方法内校验（返回 `failure`，不抛异常）。
- `CrmApiResponse.success()` → `WecomSendResult.success(code, msg)`；否则 `failure(code, msg)`。
- 不做重试（CRM 层 `CrmHttpClient` 已有 5xx 重试；上层若需任务级重试由调用方/`notification` 管线负责）。

### 改动

| 文件 | 改动 |
|---|---|
| `notification/outbound/service/WeComPushService.java` | ① 收件人 `User.wecomUserId` → `User.employeeNumber`；② 传输 `WeComMessagePublisher.sendTextMessage` → `wecomMessageSender.send(...)`；③ 移除依赖 `WeComIntegrationJpaRepository`/`WeComCredentialCipher`/`WeComMessagePublisher`，新增 `WecomMessageSender`；④ 结果映射 `WecomSendResult → NotificationDeliveryResult`。入口签名不变。 |
| `WeComPushService` 的测试 | 随依赖变化更新（见 §8） |

### 不动
投递任务表/重试/DLQ/可观测、`NotificationApplicationService`、站内信收件箱、`NotificationDeliveryJobService`、企微 OAuth 登录、`WeComApiClient`/`WeComMessagePublisher`（仍供连通性探测/admin）。

## 7. 数据流

### 7.1 独立能力直调（任意业务模块）
```
业务模块 → wecomMessageSender.send([工号…], title, content)
        → CrmMessageService.sendMessage(recipientNos, title, content, 3)
        → CrmHttpClient.post(messageBaseUrl, "/common/sendMessage", token, body)
        → CrmApiResponse → WecomSendResult 返回调用方
```

### 7.2 notification/outbound 镜像企微（改后）
```
… → NotificationDeliveryJobService.processTaskSafely(task)
  → WeComPushService.push(command)
     · userRepository.findById(recipientUserId) → user
     · user.employeeNumber 空？→ NotificationDeliveryResult.skip("no employee number")
     · WeComMessageFormatter.format(title, type, sourceEntityType, sourceEntityId, platformBaseUrl)
       → title + content(= description + "\n" + url)
     · wecomMessageSender.send([employeeNumber], title, content) → WecomSendResult
     · → NotificationDeliveryResult.success/failure
  → 既有 handleSuccess/handleFailure（DELIVERED + OutboundLog；或重试/DLQ）
```

## 8. 结果与错误映射

| 层 | 类型 | 字段 |
|---|---|---|
| 独立能力 | `WecomSendResult` | `success` / `code`（CRM code）/ `message` |
| 通知层适配 | `NotificationDeliveryResult` | `successful` / `skipped` / `errcode` / `message` |

映射规则：
- 工号为空 → `NotificationDeliveryResult.skip("no employee number")`（同当前"未绑定"语义，不重试）。
- `WecomSendResult.success()` → `NotificationDeliveryResult.success(code, message)`。
- 否则 → `NotificationDeliveryResult.failure(code, message)` → 进入既有重试/DLQ（`NotificationFailureClassifier` + `AsyncDecisionResolver`，最多 3 次 + 指数退避）。

**Plan 阶段待办**：确认 `NotificationFailureClassifier` 能识别 CRM 错误（`CrmApiResponse.isClientError/isServerError`、`CrmHttpClient` 的 `parseError` 串），区分瞬时（5xx/超时 → 重试）与持久（4xx，如非法工号 → 落 DLQ/丢弃）；不足则补。

`OutboundLog` 复用现有 `channel=WECOM`、`wecomErrcode`/`wecomErrmsg` 字段记录 CRM 的 `code`/`msg`（语义从"企微 errcode"变为"CRM code"，v1 不改 schema，在代码注释与 README 注明）。

## 9. 测试

- **`WecomMessageSenderTest`**（mock `CrmMessageService`）：
  - 断言调用传入 `flag=3`、`recipientNos` 透传。
  - `CrmApiResponse.success()` → `WecomSendResult.success`。
  - 非 success → `WecomSendResult.failure`。
  - 空 `recipientNos` → 不抛异常、返回 `failure`（防御式契约，调用方无需预校验）。
- **`WeComPushServiceTest`** 更新：改为工号驱动；mock `UserRepository` + `WecomMessageSender`；断言无 `employeeNumber` 时 skip、成功/失败结果映射、`content` 含 description+url。
- 沿用 `NotificationDeliveryJobServiceTest` 既有端到端模式（mock 传输层）。
- `core/`（`WeComMessageFormatter` 等）不碰；`FPJavaArchitectureTest` 不受影响。`wecom` 模块属 `service`/应用层，不引入对 `notification` 的依赖（必要时新增 ArchUnit 规则固化该边界）。

## 10. 配置与前置条件

- **无需 DB 迁移**；**不加 feature flag**（单一能力，按 §3 非目标）。
- **前置**：`app.crm.message-base-url`（当前 `application-dev.yml` 中 `XIYU_CRM_MESSAGE_BASE_URL` 默认空）与 CRM 鉴权（`app.crm.client-id/secret` 或 OAuth）必须配好；否则企微发送失败 → 重试 → DLQ。
  - Plan 需纳入：dev 环境是否配置可达 CRM、或在文档/runbook 明确"企微发送需先配 CRM"。
  - 影响：改后 dev 每条通知都会触发一次企微发送尝试；CRM 未配时会产生失败→重试→DLQ 噪声。Plan 需评估是否需要 dev 级静默开关（**非**路径回退，仅为降噪）——若需要，作为独立可选项，不破坏"单一能力"语义。
- `app.platform.base-url`（深链生成）保持现状。

## 11. 范围外 / 未来

- 站内信经 CRM 发送（`flag=1/2`）：不做。
- 给 `OutboundLog` 增加 `provider` 列以区分历史直连企微日志与新 CRM 日志：v1 不做（单出口后无并存），未来若需留存历史区分再加。
- 批量发送优化（`CrmProperties.messageBatchMaxSize`）：本期按单条/调用方自定，不做批聚合。
- 将其它模块潜在的"绕过 notification 自行发企微"的散点统一收口：本期范围仅 `notification/outbound` 改造 + 独立能力；统一收口作为后续排查项。

## 12. 决策记录（alternatives considered）

会话中先后否定了三个出发点，记录如下以防回退：

1. **"从零新建 `/common/sendMessage` 接口"** — 否决：该接口在 `crm` 模块已实现（`CrmMessageService` + `CrmController` 代理），仓库内已存在且"状态：已完成"。
2. **"在 notification 出站插入端口 + 直连/CRM 双适配器 + flag 回退"**（原方案 A）— 否决：用户指出出发点错了，企微发送应是唯一出口，不应保留直连企微路径与回退开关。
3. **"把 CRM 出口焊进 notification 出站管线（与站内信耦合）"** — 否决：用户要求把企微发送**单独拿出来**作为独立能力，按通道彻底解耦。

最终采纳：独立 `wecom` 模块 + 固定 `flag=3` + `notification/outbound` 改调它（D1–D4）。

## 13. Plan 阶段待办

- [ ] 确认 `NotificationFailureClassifier` 对 CRM 错误串的分类覆盖（§8）。
- [ ] 确认 `CrmAuthService` 在 `@Scheduled` 线程下取 token 的线程安全性（应为缓存式，需验证）。
- [ ] dev 环境 CRM 可达性 / 降噪策略（§10；若需静默开关，仅为 dev 降噪，非路径回退）。
- [ ] 是否新增 ArchUnit 规则：`wecom` 不得依赖 `notification`（§9）。
- [ ] 更新 `notification/outbound/README.md`（传输改为 CRM）与新增 `wecom/README.md`。
