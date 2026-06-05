# notification 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
统一通知收件箱模块 — 提供 per-user 通知的创建、查询、已读标记能力。
控制器层接收 Spring Security `UserDetails`，再通过 `AuthService` 解析为项目 `User` 实体，
确保通知查询和已读操作使用真实用户 ID。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `NotificationType.java` | Core Enum | 通知类型枚举 |
| `NotificationDispatchPolicy.java` | Core Policy | 派发校验纯核心 |
| `NotificationReadPolicy.java` | Core Policy | 已读校验纯核心 |
| `Notification.java` | Entity | 通知内容实体 |
| `UserNotification.java` | Entity | 用户通知状态实体 |
| `NotificationRepository.java` | Repository | 通知数据访问 |
| `UserNotificationRepository.java` | Repository | 用户通知数据访问 |
| `NotificationSummary.java` | DTO | 列表摘要 record |
| `NotificationDetail.java` | DTO | 详情 record |
| `CreateNotificationRequest.java` | DTO | 创建请求 record |
| `NotificationAssembler.java` | DTO Mapper | Entity→DTO 转换 |
| `NotificationApplicationService.java` | Service | 应用编排服务 |
| `NotificationController.java` | Controller | REST 端点 |

## API 端点

- `GET /api/notifications` — 分页列表
- `GET /api/notifications/unread-count` — 未读数
- `POST /api/notifications/{id}/read` — 标记已读
- `POST /api/notifications/read-all` — 全部已读
- `POST /api/admin/notifications` — 管理员创建通知
