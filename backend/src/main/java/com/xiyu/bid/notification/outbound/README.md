# notification.outbound 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
站内通知的出站适配层：在 `NotificationApplicationService.createNotification` 发布 `NotificationCreatedEvent` 之后，异步将通知投递到企微通道，并记录出站日志供管理员排查。对业务模块零耦合。

> 企微传输由独立的 `wecom` 模块承担（`WecomMessageSender`，按工号、走西域统一消息中心 `/qywx/sendMSG`）。本层不再直连企微 API；`WeComPushService` 仅负责"按工号解析收件人 + 透传已知邮箱 + 委托发送 + 结果映射"。站内信仍由 `notification` 收件箱单一承担。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `OutboundChannel.java` | Core Enum | 出站通道枚举（WECOM） |
| `OutboundStatus.java` | Core Enum | SENT / FAILED / SKIPPED |
| `SkipReason.java` | Core Enum | NOT_BOUND / DISABLED / ERROR |
| `WeComMessageFormatter.java` | Core Policy | 纯函数：通知 → 企微消息结构 |
| `NotificationCreatedEvent.java` | Event | 通知创建领域事件 |
| `OutboundLog.java` | Entity | 出站日志实体 |
| `OutboundLogRepository.java` | Repository | 出站日志数据访问 |
| `WeComPushService.java` | Service | 编排：收件人解析 + 格式化 + 调用发送 |
| `NotificationDeliveryTaskListener.java` | Listener | `@TransactionalEventListener(AFTER_COMMIT) + REQUIRES_NEW` — 企微推送任务入库（唯一监听器） |

## 设计要点
- **纯核心**：`core/` 无 Spring / DB / IO / 日志依赖，受 `FPJavaArchitectureTest` 保护
- **异步**：业务事务提交后入投递队列，由定时 worker 发送，不在业务请求中等待企微。
- **并发投递**：每个任务使用独立 READ_COMMITTED 事务，先取得任务行的写锁，再复核最新状态和重试时间；持锁到单次发送及结果落库结束，防止不同实例重复发送同一候选。
- **推送事务隔离**：持有任务行锁时，以 NOT_SUPPORTED 暂停任务事务调用推送；推送依赖的 REQUIRED 事务失败不会把任务事务标成 rollback-only，恢复任务事务后保存退避与失败记录。
- **故障边界**：任务事务失败会回滚并记录错误，不影响后续任务；外部发送成功但数据库提交前进程崩溃仍是跨系统不确定窗口，不宣称 exactly-once。
- **幂等降级**：未配置工号时写 `SKIPPED` 日志并返回，不重试（v1 scope）
- **深链对齐**：`WeComMessageFormatter` 构造的 URL 规则与前端 `resolveNotificationRoute` 对齐
