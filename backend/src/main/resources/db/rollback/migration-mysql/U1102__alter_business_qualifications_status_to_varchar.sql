-- Input: V1102__alter_business_qualifications_status_to_varchar.sql
-- Output: U1102 rollback script for business_qualifications status column type
-- Pos: db/rollback/migration-mysql/
--
-- Backout strategy: V1102 把 status 列从 enum('VALID','EXPIRING','EXPIRED')
-- 改成了 VARCHAR(32)，以容纳 IN_STOCK / RETIRED 等新枚举值。
-- 回退需要把列改回 enum，但 enum 不接受 IN_STOCK/RETIRED，所以必须先把
-- 这些行映射回 enum 允许的值：
--   IN_STOCK -> VALID   （语义上 IN_STOCK 是 VALID 的替代，回退时还原）
--   RETIRED  -> EXPIRED （回退时没有精确对应值，选 EXPIRED 作为最接近的"非在库"状态）
--
-- 注意：回退会丢失 IN_STOCK/RETIRED 的精确语义，这是回退的固有代价。
-- 仅在紧急回滚 V1102 时使用，回滚后 retire 接口将重新报 500（恢复到 V1102 前的状态）。
--
-- PR: #<pending>

-- Pre-flight: count rows that carry non-enum values, for the rollback log.
SELECT COUNT(*) AS rows_to_remap
  FROM business_qualifications
 WHERE status IN ('IN_STOCK', 'RETIRED');

-- Remap: IN_STOCK -> VALID, RETIRED -> EXPIRED（收紧到 enum 允许的值集）。
UPDATE business_qualifications
   SET status = CASE
     WHEN status = 'IN_STOCK' THEN 'VALID'
     WHEN status = 'RETIRED'  THEN 'EXPIRED'
     ELSE status
   END
 WHERE status IN ('IN_STOCK', 'RETIRED');

-- Post-flight: assert no non-enum values remain（否则下面的 MODIFY 会失败）。
SELECT COUNT(*) AS remaining_non_enum_rows
  FROM business_qualifications
 WHERE status NOT IN ('VALID', 'EXPIRING', 'EXPIRED');

-- Restore the original enum type from B73 baseline.
ALTER TABLE business_qualifications
    MODIFY COLUMN status ENUM('VALID', 'EXPIRING', 'EXPIRED') NOT NULL;
