# notification.outbound 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
站内通知的出站适配层：在 `NotificationApplicationService.createNotification` 发布 `NotificationCreatedEvent` 之后，异步将通知投递到外部通道（当前仅企业微信），并记录出站日志供管理员排查。对业务模块零耦合。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `OutboundChannel.java` | Core Enum | 出站通道枚举（WECOM） |
| `OutboundStatus.java` | Core Enum | SENT / FAILED / SKIPPED |
| `SkipReason.java` | Core Enum | NOT_BOUND / DISABLED / ERROR |
| `WeComMessageFormatter.java` | Core Policy | 纯函数：通知 → 企微 textcard 结构 |
| `NotificationCreatedEvent.java` | Event | 通知创建领域事件 |
| `OutboundLog.java` | Entity | 出站日志实体 |
| `OutboundLogRepository.java` | Repository | 出站日志数据访问 |
| `WeComPushService.java` | Service | 编排：绑定解析 + 格式化 + 调用发送 + 写日志 |
| `NotificationCreatedWeComListener.java` | Listener | `@Async + @TransactionalEventListener(AFTER_COMMIT)` |

## 设计要点
- **纯核心**：`core/` 无 Spring / DB / IO / 日志依赖，受 `FPJavaArchitectureTest` 保护
- **异步**：`@Async` 保证企微 API 延迟/失败不拖慢业务事务
- **幂等降级**：未绑定或全局关闭时写 `SKIPPED` 日志并返回，不重试（v1 scope）
- **深链对齐**：`WeComMessageFormatter` 构造的 URL 规则与前端 `resolveNotificationRoute` 对齐
