-- Input: migration-mysql/V80__tender_upload_queue.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_tender_task_dlq_failed_at ON tender_task_dlq;
DROP TABLE IF EXISTS tender_task_dlq;
DROP INDEX idx_tender_task_locked_status ON tender_task;
DROP INDEX idx_tender_task_status_available_priority ON tender_task;
DROP TABLE IF EXISTS tender_task;
DROP INDEX idx_tender_file_user_status_created ON tender_file;
DROP TABLE IF EXISTS tender_file;
