-- Input: V1075__migrate_valid_to_in_stock_for_business_qualifications.sql
-- Output: U1075 rollback script for business_qualifications status migration
-- Pos: db/rollback/migration-mysql/
--
-- Backout strategy: only restore rows that look like they were touched by V1075.
-- A row "looks migrated" when:
--   1. its current status is IN_STOCK (V1075's target state)
--   2. it has never had a real expiry window set (expiry_date IS NULL
--      AND validity_period_start IS NULL), which strongly suggests it was a
--      legacy VALID row that just got renamed
--
-- This narrow WHERE protects brand-new IN_STOCK records (which are supposed to
-- have validity_period_start set by the create flow) from being clobbered.
--
-- PR: #<pending>

-- Pre-flight: count candidates for backout, for the rollback log.
SELECT COUNT(*) AS rows_to_rollback
  FROM business_qualifications
 WHERE status = 'IN_STOCK'
   AND expiry_date IS NULL
   AND validity_period_start IS NULL;

-- Rollback: IN_STOCK -> VALID, narrowed to "looks like a migrated legacy row".
UPDATE business_qualifications
   SET status = 'VALID'
 WHERE status = 'IN_STOCK'
   AND expiry_date IS NULL
   AND validity_period_start IS NULL;

-- Post-flight: assert the candidate set is empty.
SELECT COUNT(*) AS remaining_candidates
  FROM business_qualifications
 WHERE status = 'IN_STOCK'
   AND expiry_date IS NULL
   AND validity_period_start IS NULL;
