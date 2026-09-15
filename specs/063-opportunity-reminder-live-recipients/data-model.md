# Data Model: 商机预测实时收件人与历史补发

本功能不新增或修改数据库结构。以下是运行时值对象及既有数据关系。

## LiveRecipientSnapshot

一次扫描内不可变的实时收件人快照。

| 字段 | 类型 | 规则 |
|------|------|------|
| activeUserIds | Set<Long> | OSS 明确返回且在职，并能映射到本地用户 ID |
| roleRecipientIds | List<Long> | activeUserIds 中拥有 `/bidAdmin` 或 `bid-TeamLeader` 的用户，去重保序 |
| requestedJobNumbers | int | 本轮提交给批量接口的去重工号数 |
| returnedJobNumbers | int | OSS 实际返回并能按工号索引的数量 |

## 既有关系

| 实体 | 关键字段 | 本次用途 |
|------|----------|----------|
| User | id, employeeNumber | 本地通知身份与 OSS 工号映射；本地 enabled/role 不作最终事实 |
| Project | id, managerId | 查找项目经理用户 ID |
| OpportunityPredictionReminderLog | projectId, predictedBidDate, eventPublishedAt | 区分从未提醒和已有提醒；不删除、不重置 |
| Notification | type, sourceEntityType, sourceEntityId, title, body, payloadJson | 复用已有商机预测提醒的内容快照 |
| UserNotification | notificationId, userId | 判断一个项目对一个用户是否已有提醒 |

## HistoricalReplayNotification

一次性历史补发继续复用既有 `Notification` / `UserNotification`，不新增表。

| 字段 | 规则 |
|------|------|
| type | `OPPORTUNITY_PREDICTION` |
| sourceEntityType | 固定历史补发来源，与正常 `Project` 来源隔离 |
| sourceEntityId | 项目 ID |
| recipients | 当前 OSS 实时在职项目经理与两个目标角色的去重并集 |
| payload | 项目 ID、名称、招标主体、预测日期、商机中心路径、历史补发标识 |

同一 `sourceEntityType + sourceEntityId + type` 已存在时视为该项目补发完成。当前单实例串行执行是无数据库迁移方案的运行前提。

## 状态转换

```text
到期项目
  ├─ 执行日零点前无 reminder log → 正常 runScan → 创建完整通知 + reminder log + 事件投递
  ├─ reminder log 当天新建 → 历史补发跳过，避免重复
  └─ reminder log 早于执行日零点
       ├─ 已有 HistoricalReplayNotification → 幂等跳过
       └─ 尚无 HistoricalReplayNotification → 当前完整接收人创建独立补发通知
```

历史补发不得改变 `eventPublishedAt`、领取令牌、原通知关系或原提醒日志任何字段。
