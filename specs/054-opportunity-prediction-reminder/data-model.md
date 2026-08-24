# 商机预测提醒 - 数据模型 (054)

## 新增表：opportunity_prediction_reminder_log

商机预测提醒日志：记录"已提醒过的项目"，保证每项目只提醒一次（幂等去重）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| project_id | BIGINT | NOT NULL, UNIQUE (uk_opportunity_prediction_reminder_project) | 已提醒的项目 ID |
| predicted_bid_date | DATE | NOT NULL | 触发提醒时的预测招标时间 |
| purchaser_name | VARCHAR(500) | NULL | 招标主体名称（通知文案快照） |
| notified_at | DATETIME | NOT NULL | 提醒发送时间 |
| created_at | DATETIME | NOT NULL | 创建时间（PrePersist） |

- 唯一键 `project_id`：同一项目至多一条记录，从数据库层兜底去重（防并发双发）。
- 迁移文件：`backend/src/main/resources/db/migration-mysql/V1200__add_opportunity_prediction_reminder_log.sql`
  （回滚：`backend/src/main/resources/db/rollback/migration-mysql/U1200__add_opportunity_prediction_reminder_log.sql`）

## 关联既有数据

| 数据 | 来源 | 用途 |
|------|------|------|
| Project | `project` 表 | 全量扫描对象；`managerId`（项目负责人）、`customerType`（预测输入）、`customer`（名称兜底） |
| ProjectResult | `project_result` 表 | `resultType`（流标判定）、`servicePeriodYears` / `servicePeriodEndDate`（预测输入） |
| Tender | `tender` 表 | `bidOpeningTime`（预测输入）、`purchaserName`（招标主体名称） |
| User | `user` 表 | 项目负责人启用状态过滤 |
