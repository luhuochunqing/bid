-- U1146: 回滚 V1146 — 删除 tasks 表的提醒去重字段
-- CO-533 任务到期/逾期提醒去重字段回滚

ALTER TABLE tasks
    DROP COLUMN last_reminded_at,
    DROP COLUMN last_overdue_reminded_at;
