-- V1075: Migrate VALID to IN_STOCK for business qualifications (CO-155/CO-2401)
-- 蓝图 业务资质 — QualificationStatus.VALID (@Deprecated) has been replaced by
-- IN_STOCK in source code. Existing records with status='VALID' must be migrated
-- so they match the new IN_STOCK filter used by list/aggregation queries.
-- IN_STOCK is the canonical "在库" state going forward.
--
-- Idempotency: re-running is a no-op because we only touch rows that are still 'VALID'.
-- Backout: see db/rollback/migration-mysql/U1075__migrate_valid_to_in_stock_for_business_qualifications.sql
-- PR: #<pending>

-- Pre-flight: count rows that will be touched, for the migration log.
SELECT COUNT(*) AS valid_rows_to_migrate
  FROM business_qualifications
 WHERE status = 'VALID';

-- Migration: VALID -> IN_STOCK. Safe to re-run (WHERE is self-excluding).
UPDATE business_qualifications
   SET status = 'IN_STOCK'
 WHERE status = 'VALID';

-- Post-flight: assert no VALID rows remain. If non-zero, the migration failed
-- and the operator should investigate before continuing.
SELECT COUNT(*) AS remaining_valid_rows
  FROM business_qualifications
 WHERE status = 'VALID';
