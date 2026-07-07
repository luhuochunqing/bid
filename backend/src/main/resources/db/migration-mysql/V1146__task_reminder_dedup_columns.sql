-- V1146: CO-533 任务到期/逾期提醒去重字段
-- 为 tasks 表新增 last_reminded_at 和 last_overdue_reminded_at 两个 nullable 字段
-- 用于 24 小时内同任务最多提醒 1 次的去重控制（null 表示从未提醒）

ALTER TABLE tasks
    ADD COLUMN last_reminded_at DATETIME NULL COMMENT 'CO-533: 即将到期提醒最后发送时间（24h 去重）',
    ADD COLUMN last_overdue_reminded_at DATETIME NULL COMMENT 'CO-533: 逾期/超期提醒最后发送时间（24h 去重）';
