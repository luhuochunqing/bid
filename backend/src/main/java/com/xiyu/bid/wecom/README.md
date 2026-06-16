# wecom 模块（企微消息发送）

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
独立的企微消息发送能力。任何模块需要发企微时，按工号直接调用 `WecomMessageSender`，
不依赖 notification 站内信管线。通道职责划分：

- **站内信** → `notification` 收件箱
- **企微** → 本模块（经 CRM `/common/sendMessage`，`flag=3` 仅企微）

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `WecomMessageSender.java` | Service | 唯一入口：`send(工号[], title, content)`，`flag=3` 调 CRM |
| `WecomSendResult.java` | Record | 发送结果（success/code/message），模块自有 |

## 依赖方向
`wecom → crm`（复用 `CrmMessageService` / `CrmHttpClient` / `CrmAuthService`，不重造 HTTP/鉴权）。
**本模块不得依赖 `notification`**（由 ArchitectureTest 边界规则保护）。

## 配置前置
企微发送依赖 CRM 统一消息服务，必须先配置（否则发送失败 → 由调用方重试/DLQ）：

- `app.crm.message-base-url`（env `XIYU_CRM_MESSAGE_BASE_URL`）
- `app.crm.client-id` / `app.crm.client-secret`（或 OAuth 账号）

dev 环境 `message-base-url` 默认为空；联调企微前需显式注入。

## 不做
- 不做站内信（`flag=1/2`）。
- 不做重试（CRM 层已有 5xx 重试；任务级重试交给调用方）。
- 不做批量聚合（按调用方单次入参）。
