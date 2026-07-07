-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功删除 tasks.last_reminded_at 与 tasks.last_overdue_reminded_at 字段
-- Pos: 与 V1146 配对，作为 CO-533 任务到期/逾期提醒去重字段的回滚
-- 维护声明: 维护者按项目SOP；与 V1146 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1146__task_reminder_dedup_columns.sql

-- U1146 rollback for V1146__task_reminder_dedup_columns.sql (CO-533 任务到期/逾期提醒去重字段)

ALTER TABLE tasks
    DROP COLUMN last_reminded_at,
    DROP COLUMN last_overdue_reminded_at;
