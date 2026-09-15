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

## 状态转换

```text
历史候选项目
  ├─ 无 reminder log → 正常 runScan → 创建完整通知 + reminder log + 事件投递
  └─ 有 reminder log
       ├─ 目标角色已全部收到 → 幂等跳过
       └─ 存在缺失目标角色 → 复制既有通知 → 仅创建缺失角色通知关系/企微任务
```

角色补发不得改变 `eventPublishedAt`、领取令牌或原通知的经理关系。
