-- Input: migration-mysql/V1147__task_reminder_dedup_columns.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1147: 回滚 CO-533 任务到期/逾期提醒去重字段
-- （原 U1146，与 V1147 同步重命名以解决与 CO-464 V1146__add_tender_purchaser_id 的撞号）
-- 数据丢失说明：last_reminded_at / last_overdue_reminded_at 列中已记录的提醒时间戳会丢失，回滚前请确认。

ALTER TABLE tasks
    DROP COLUMN IF EXISTS last_reminded_at,
    DROP COLUMN IF EXISTS last_overdue_reminded_at;
