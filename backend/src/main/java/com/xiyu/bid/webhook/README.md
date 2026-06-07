# Webhook 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
Webhook 模块负责外部系统的回调通知处理，支持事件订阅、签名验证、重试机制和投递状态追踪。
当投标平台发生标讯更新、项目状态变更等事件时，通过配置的 webhook URL 向外部系统推送通知。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `application/WebhookDispatcher.java` | Service | Webhook 分发处理服务 |
| `domain/TenderStatusChangedEvent.java` | Event | 标讯状态变更事件 |
| `infrastructure/WebhookDeliveryLog.java` | Entity | Webhook 投递日志实体 |
| `infrastructure/WebhookDeliveryLogRepository.java` | Repository | 投递日志持久化仓库 |
