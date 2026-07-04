-- Migration V1134
-- CO-469 第五轮根因（§23 全链路日志排查 SOP / Layer 2+3）
-- 批量导入/导出操作日志不绑定单一人员，personnel_id 必须为 NULL。
-- V1065 建表时 personnel_id 为 NOT NULL，导致 BATCH_EXPORT_PERSONNEL / BATCH_IMPORT_PERSONNEL
-- 写入时触发 DataIntegrityViolationException。
-- 业务语义：单条人员操作日志 personnel_id 必填；批量操作日志 personnel_id 为空。
ALTER TABLE personnel_operation_log MODIFY COLUMN personnel_id BIGINT NULL;
