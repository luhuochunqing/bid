# 商机预测提醒 - 数据模型 (054)

## 新增表：opportunity_prediction_reminder_log

商机预测提醒日志：分别记录"站内提醒已创建"与"CRM 事件库已受理"，保证通知不重复且事件失败可补偿。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| project_id | BIGINT | NOT NULL, UNIQUE (uk_opportunity_prediction_reminder_project) | 已提醒的项目 ID |
| predicted_bid_date | DATE | NOT NULL | 触发提醒时的预测招标时间 |
| purchaser_name | VARCHAR(500) | NULL | 招标主体名称（通知文案快照） |
| notified_at | DATETIME | NOT NULL | 提醒发送时间 |
| event_published_at | DATETIME | NULL | CRM 事件库受理时间；NULL 表示后续扫描只重推事件 |
| event_publish_claim_token | VARCHAR(36) | NULL | 当前投递实例的领取令牌 |
| event_publish_claimed_at | DATETIME | NULL | 领取时间；超过 10 分钟可被其他实例恢复 |
| created_at | DATETIME | NOT NULL | 创建时间（PrePersist） |

- 唯一键 `project_id`：新提醒事务先插入并 flush，再创建通知，从数据库层保证同一项目只创建一次通知。
- 事件投递使用条件 UPDATE 原子领取；令牌匹配才能成功确认或释放，避免多个 Pod 同时调用 SDK及旧实例覆盖新状态。
- 历史行迁移时以 `notified_at` 回填 `event_published_at`，避免部署后批量重放；已知失败项目由运维按项目单独重置为 NULL。
- 迁移文件：`backend/src/main/resources/db/migration-mysql/V1200__add_opportunity_prediction_reminder_log.sql`
  （回滚：`backend/src/main/resources/db/rollback/migration-mysql/U1200__add_opportunity_prediction_reminder_log.sql`）
- 事件状态迁移：`backend/src/main/resources/db/migration-mysql/V1214__track_opportunity_event_publish.sql`
  （回滚：`backend/src/main/resources/db/rollback/migration-mysql/U1214__track_opportunity_event_publish.sql`）。

## 关联既有数据

| 数据 | 来源 | 用途 |
|------|------|------|
| Project | `project` 表 | 全量扫描对象；`managerId`（项目负责人）、`customerType`（预测输入）、`customer`（名称兜底） |
| ProjectResult | `project_result` 表 | `resultType`（流标判定）、`servicePeriodYears` / `servicePeriodEndDate`（预测输入） |
| Tender | `tender` 表 | `bidOpeningTime`（预测输入）、`purchaserName`（招标主体名称） |
| User | `user` 表 | 项目负责人启用状态过滤 |
