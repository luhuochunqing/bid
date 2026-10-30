# wecom 模块（企微消息发送）

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
独立的企微消息发送能力。任何模块需要发企微时，按工号直接调用 `WecomMessageSender`，
不依赖 notification 站内信管线。通道职责划分：

- **站内信** → `notification` 收件箱
- **企微** → 本模块（经西域统一消息中心 `POST /qywx/sendMSG`）

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `WecomMessageSender.java` | Service | 唯一入口：`send(userName=工号, message[, email])` |
| `WecomSendResult.java` | Record | 发送结果（success/code/message），模块自有 |
| `WecomMessageCenterClient.java` | Infrastructure | 调用统一消息中心 `/qywx/sendMSG` |
| `WecomMessageCenterProperties.java` | Config | `app.wecom.message-center.*` 配置 |

## 依赖方向
`wecom` 不依赖 `notification`，也不依赖 `crm`。
统一消息中心的 HTTP 调用用 Spring `RestTemplateBuilder` 直接实现。
**本模块不得依赖 `notification`**（由 ArchitectureTest 边界规则保护）。

## 配置

```yaml
app:
  wecom:
    message-center:
      base-url: ${XIYU_WECOM_MESSAGE_CENTER_BASE_URL:https://yapi.ehsy.com/mock/406}
      send-path: ${XIYU_WECOM_MESSAGE_CENTER_SEND_PATH:/qywx/sendMSG}
      application-code: ${XIYU_WECOM_MESSAGE_CENTER_CODE:}
      connect-timeout-ms: 3000
      read-timeout-ms: 10000
```

- `application-code`：对应消息中心里已登记的企微应用（消息中心侧保存 corpId/agentId/secret，我们只用 code 标识）。
- `userName`：接收人工号，对应 `User.employeeNumber`。

## 接口契约（`/qywx/sendMSG`）
- 请求：`{"userName":"工号","message":"消息内容","code":"应用code","email":"邮箱（可选）"}`
- 已知接收人邮箱由 `WeComPushService` 从 `User.email` 透传；旧双参数调用和空邮箱仍只传工号，不推测或修改用户资料。
- 成功：`code == 0`
- 非零业务响应记录工号、错误码、错误信息及消息中心 trace；不记录请求正文、应用 code 或 OAuth 链接。
- 自动标讯提醒在返回失败、空结果、跳过和抛异常时记录标讯 ID、提醒类型、用户 ID 及原因；只有成功才写 `SENT`，失败仍允许后续整点重试。

## 不做
- 不保存企微凭据（corpId/agentId/secret）。
- 不做站内信（由 `notification` 负责）。
- 不做重试（消息中心/调用方自行决定）。
- 不做批量聚合（按调用方单次入参）。
