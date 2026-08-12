-- Input: migration-mysql/V1185__create_tender_event_logs.sql
-- 回滚：删除标讯事件推送流水表
DROP TABLE IF EXISTS tender_event_logs;