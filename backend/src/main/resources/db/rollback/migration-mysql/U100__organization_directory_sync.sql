-- Input: migration-mysql/V100__organization_directory_sync.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP TABLE IF EXISTS organization_sync_items;
DROP TABLE IF EXISTS organization_sync_runs;
DROP INDEX idx_users_external_org_user ON users;
ALTER TABLE users DROP COLUMN last_org_synced_at;
ALTER TABLE users DROP COLUMN last_org_event_key;
ALTER TABLE users DROP COLUMN external_org_source_app;
ALTER TABLE users DROP COLUMN external_org_user_id;
DROP INDEX idx_org_departments_parent_external ON organization_departments;
DROP INDEX idx_org_departments_external ON organization_departments;
ALTER TABLE organization_departments DROP COLUMN last_synced_at;
ALTER TABLE organization_departments DROP COLUMN last_event_key;
ALTER TABLE organization_departments DROP COLUMN source_app;
ALTER TABLE organization_departments DROP COLUMN parent_external_dept_id;
ALTER TABLE organization_departments DROP COLUMN external_dept_id;
DROP INDEX idx_org_event_logs_next_retry ON organization_event_logs;
DROP INDEX idx_org_event_logs_external_dept ON organization_event_logs;
DROP INDEX idx_org_event_logs_external_user ON organization_event_logs;
DROP INDEX idx_org_event_logs_upstream_key ON organization_event_logs;
ALTER TABLE organization_event_logs DROP COLUMN last_error_code;
ALTER TABLE organization_event_logs DROP COLUMN next_retry_at;
ALTER TABLE organization_event_logs DROP COLUMN retry_count;
ALTER TABLE organization_event_logs DROP COLUMN raw_payload;
ALTER TABLE organization_event_logs DROP COLUMN external_dept_id;
ALTER TABLE organization_event_logs DROP COLUMN external_user_id;
ALTER TABLE organization_event_logs DROP COLUMN entity_type;
ALTER TABLE organization_event_logs DROP COLUMN event_time;
ALTER TABLE organization_event_logs DROP COLUMN parent_id;
ALTER TABLE organization_event_logs DROP COLUMN span_id;
ALTER TABLE organization_event_logs DROP COLUMN upstream_event_key;
