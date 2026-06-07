-- Rollback: V114__tender_source_type.sql
-- Input: migration-mysql/V114__tender_source_type.sql
-- Output: rollback for mysql environments; review data-loss comments before production use.

-- 警告：数据丢失风险
-- 此回滚会将 source_type 字段的值迁移回 source 字段，然后删除 source_type 列
-- 迁移逻辑：source_type = 'MANUAL' -> source = 'manual'，source_type = 'EXTERNAL' -> source = 'external'

-- 1. 先将数据迁移回 source 字段
UPDATE tenders SET source = 'manual' WHERE source_type = 'MANUAL';
UPDATE tenders SET source = 'external' WHERE source_type = 'EXTERNAL';

-- 2. 删除索引
DROP INDEX idx_tender_source_type ON tenders;

-- 3. 删除列
ALTER TABLE tenders DROP COLUMN source_type;
