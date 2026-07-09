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
| `NotificationMessagePolicy.java` | Core Policy | 蓝图 §消息中心-系统通知类 6 条通知文案模板纯核心（项目结项归档/任务状态变更/任务分配/@ 提及/文档变更/阶段自动推进） |
| `Notification.java` | Entity | 通知内容实体 |
| `UserNotification.java` | Entity | 用户通知状态实体 |
| `NotificationRepository.java` | Repository | 通知数据访问 |
| `UserNotificationRepository.java` | Repository | 用户通知数据访问 |
| `NotificationSummary.java` | DTO | 列表摘要 record |
| `NotificationDetail.java` | DTO | 详情 record |
| `CreateNotificationRequest.java` | DTO | 创建请求 record |
| `NotificationAssembler.java` | DTO Mapper | Entity→DTO 转换 |
| `NotificationApplicationService.java` | Service | 应用编排服务 |
| `ProjectNotificationRecipientPolicy.java` | Service Policy | 按项目角色（投标管理员/组长/负责人/辅助人员/业主/任务执行人/审核人/成员）解析接收人用户 ID |
| `NotificationRecipientResolver.java` | Service | 通知接收人通用解析器；按项目角色委托 `ProjectNotificationRecipientPolicy`，并补充管理员、项目成员、项目可见性过滤 |
| `NotificationController.java` | Controller | REST 端点 |

## API 端点

- `GET /api/notifications` — 分页列表
- `GET /api/notifications/unread-count` — 未读数
- `POST /api/notifications/{id}/read` — 标记已读
- `POST /api/notifications/read-all` — 全部已读
- `POST /api/admin/notifications` — 管理员创建通知

## NotificationType 枚举值与触发源

| 枚举值 | 触发源 | 说明 |
|---|---|---|
| `INFO` / `SYSTEM` | 多处 | 通用信息/系统通知 |
| `SYSTEM` | `ProjectEventNotificationDispatcher` / `ProjectNotificationService.notifyProjectArchived` / `notifyStageTransition` | 项目结项归档、阶段自动推进（蓝图 §消息中心-系统通知 序号 1、6） |
| `MENTION` | `MentionApplicationService` | @ 提及（蓝图 §消息中心-系统通知 序号 4） |
| `APPROVAL` | `ProjectNotificationService` | 立项/复盘/结项审核 |
| `TASK_UPDATE` | `ProjectEventNotificationDispatcher` / `TaskReviewNotificationService` | 任务分配、任务状态变更（蓝图 §消息中心-系统通知 序号 2、3） |
| `DOCUMENT_CHANGE` | `DocumentChangeNotificationService` ← `ProjectDocumentWorkflowService` | 文档上传/删除（蓝图 §消息中心-系统通知 序号 5） |
| `BID_REVIEW` | `ProjectNotificationService.notifyBidReviewSubmitted` | 标书审核 |
| `DEADLINE` | `tenderreminder`、`TaskDueReminderScanTask` | 截止/到期提醒 |
| `TENDER_MATCH` | 标讯匹配 | 标讯匹配 |
| `CA_*`（6 个） | `CaNotificationDispatcher` | CA 证书到期/借用 |
