-- V1147: CO-533 任务到期/逾期提醒去重字段
-- （原 V1146，因与 V1146__add_tender_purchaser_id（CO-464）撞号，重命名为 V1147。
--  CO-464 的 V1146 已在生产数据库应用，checksum 已固化，不可改动。）
-- 为 tasks 表新增 last_reminded_at 和 last_overdue_reminded_at 两个 nullable 字段
-- 用于 24 小时内同任务最多提醒 1 次的去重控制（null 表示从未提醒）

ALTER TABLE tasks
    ADD COLUMN last_reminded_at DATETIME NULL COMMENT 'CO-533: 即将到期提醒最后发送时间（24h 去重）',
    ADD COLUMN last_overdue_reminded_at DATETIME NULL COMMENT 'CO-533: 逾期/超期提醒最后发送时间（24h 去重）';
