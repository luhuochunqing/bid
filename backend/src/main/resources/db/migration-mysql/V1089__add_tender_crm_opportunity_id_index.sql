-- CO-297: 把 crm_opportunity_id 的普通索引升级为 UNIQUE 索引。
-- 历史：V1006 已创建同名普通索引（CREATE INDEX），
-- 现在用 UNIQUE 约束替换，作为应用层去重后的数据库最终防线。
-- 注意：MySQL 没有 DROP INDEX IF EXISTS，用 V1006 同款动态模式。

-- 先确保旧索引已清除
DELIMITER //
SET @exists = (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tenders'
      AND INDEX_NAME = 'idx_tender_crm_opportunity_id'
    LIMIT 1
)//
DELIMITER ;

SET @drop_sql = IF(@exists IS NOT NULL,
    'DROP INDEX idx_tender_crm_opportunity_id ON tenders', 'SELECT 1');
PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 再创建 UNIQUE 索引
CREATE UNIQUE INDEX idx_tender_crm_opportunity_id ON tenders (crm_opportunity_id);
