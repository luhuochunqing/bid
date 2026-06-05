-- Input: migration-mysql/V120__organization_retry_statuses.sql
-- Output: rollback script for mysql environments; downgrades retry-only states before enum shrink.
-- Pos: Flyway down migration coverage for 西域数智化投标管理平台.

UPDATE organization_event_logs
SET status = 'FAILED',
    next_retry_at = NULL,
    last_error_code = COALESCE(NULLIF(last_error_code, ''), 'ROLLBACK_RETRY_STATUS_DOWNGRADE'),
    message = COALESCE(NULLIF(message, ''), 'Retry status downgraded before rollback')
WHERE status IN ('PENDING_RETRY', 'DEAD_LETTER');

ALTER TABLE organization_event_logs
  MODIFY status ENUM('PROCESSING','PROCESSED','DUPLICATE','REJECTED','FAILED') NOT NULL;
