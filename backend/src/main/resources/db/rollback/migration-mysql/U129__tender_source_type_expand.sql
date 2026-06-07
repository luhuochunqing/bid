-- Input: migration-mysql/V129__tender_source_type_expand.sql
-- Output: rollback script for mysql environments
-- U129: 回滚标讯来源类型为2个值
-- 恢复为旧枚举: MANUAL / EXTERNAL

-- 回填为新旧映射
-- MANUAL_SINGLE + BULK_IMPORT → MANUAL
-- EXTERNAL_PLATFORM + CRM_OPPORTUNITY → EXTERNAL
UPDATE tenders
SET source_type = 'MANUAL'
WHERE source_type IN ('MANUAL_SINGLE', 'BULK_IMPORT');

UPDATE tenders
SET source_type = 'EXTERNAL'
WHERE source_type IN ('EXTERNAL_PLATFORM', 'CRM_OPPORTUNITY');

-- 删除乐观锁版本号列
ALTER TABLE tenders DROP COLUMN version;

-- 修改 ENUM 定义回旧值
ALTER TABLE tenders MODIFY COLUMN source_type ENUM(
  'MANUAL',
  'EXTERNAL'
) NOT NULL DEFAULT 'MANUAL';
