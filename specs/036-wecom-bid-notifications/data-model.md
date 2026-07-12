# Data Model: 投标关键节点企微通知

## 新增/变更概念

### 1. Notification Business Action（通知业务动作）

用于区分不同业务触点的通知类型，供消息模板工厂与去重键使用。

| 动作标识 | 业务含义 | 触发场景 |
|---|---|---|
| `PENDING_INITIATION` | 待立项 | 投标管理员对评估后标讯点击"立即投标"并创建项目后 |
| `PENDING_CLOSURE_APPLICATION` | 待结项申请 | 项目阶段从 `RETROSPECTIVE` 推进至 `CLOSED` 后 |

### 2. Notification Dedup Key（去重键）

保证同一业务动作在短时间内不因重复提交而发送多条通知。

| 字段 | 说明 |
|---|---|
| `sourceEntityType` | 源实体类型：`TENDER` 或 `PROJECT` |
| `sourceEntityId` | 源实体 ID |
| `actionType` | 业务动作：`PENDING_INITIATION` / `PENDING_CLOSURE_APPLICATION` |
| `createdAt` | 创建时间，用于 5 分钟滑动窗口去重 |

索引：`(sourceEntityType, sourceEntityId, actionType, createdAt)`

### 3. 通知模板变量

#### PENDING_INITIATION

| 变量 | 来源 |
|---|---|
| `tenderName` | `Tender.name` |
| `projectName` | `Project.name` |
| `projectOwnerName` | `User.name`（可选） |
| `targetUrl` | `/projects/{projectId}/initiation` |

#### PENDING_CLOSURE_APPLICATION

| 变量 | 来源 |
|---|---|
| `projectName` | `Project.name` |
| `projectOwnerName` | `User.name`（可选） |
| `targetUrl` | `/projects/{projectId}/closure` |

## 相关现有实体

### Tender（标讯）

| 字段 | 说明 |
|---|---|
| `id` | 标讯 ID |
| `name` | 标讯名称 |
| `status` | 标讯状态：`PENDING_ASSIGNMENT / TRACKING / EVALUATED / BIDDING / WON / LOST / ABANDONED` |
| `projectId` | 关联项目 ID（投标立项后回填） |
| `projectManagerId` | 标讯项目经理 ID |

### Project（项目）

| 字段 | 说明 |
|---|---|
| `id` | 项目 ID |
| `name` | 项目名称 |
| `tenderId` | 关联标讯 ID |
| `managerId` | 项目经理 ID |
| `stage` | 当前阶段：`INITIATED / DRAFTING / EVALUATING / RESULT_PENDING / RETROSPECTIVE / CLOSED` |
| `status` | 项目状态 |

### ProjectInitiationDetails（立项详情）

| 字段 | 说明 |
|---|---|
| `projectId` | 项目 ID |
| `ownerUserId` | 项目负责人/业主方负责人 |

### User（用户）

| 字段 | 说明 |
|---|---|
| `id` | 用户 ID |
| `name` | 用户名 |
| `employeeNumber` | 工号，企微推送使用 |

### Notification（站内通知）

| 字段 | 说明 |
|---|---|
| `id` | 通知 ID |
| `type` | 通知类型 |
| `title` | 标题 |
| `content` | 内容 |
| `payload` | JSON，含 `targetUrl` 等变量 |
| `recipientUserId` | 接收人用户 ID |
| `sourceEntityType` / `sourceEntityId` | 源实体 |
| `createdAt` / `readAt` | 时间 |

### NotificationDeliveryTask（企微投递任务）

| 字段 | 说明 |
|---|---|
| `id` | 任务 ID |
| `notificationId` | 关联通知 ID |
| `recipientUserId` | 接收人 |
| `businessKey` | `notificationId:recipientUserId:type`，用于去重 |
| `status` | `PENDING / SENT / FAILED / DEAD_LETTER` |

## 关系图

```
Tender (1) --proceedToBid--> Project (1)
                                    |
                                    v
                    ProjectInitiationDetails (ownerUserId)
                                    |
                                    v
                    User (employeeNumber) <-- 站内通知 --> 企微推送
```
