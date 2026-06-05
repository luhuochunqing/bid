-- V126: 补齐 tenders 表中 Tender 实体预期的剩余缺失字段
-- V75/V78 已补齐大部分基础字段（region, industry, purchaser_hash, contact_name/phone,
-- 归一化字段等），V125 已补齐 bid_notice。本迁移补齐从 H2 V110 中尚未
-- 在 MySQL 侧覆盖的字段：联系人2信息、项目/标讯负责人员信息。
-- 幂等：仅当列不存在时才添加

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
CREATE PROCEDURE add_column_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_def VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_column_if_not_exists('tenders', 'contact_tel',     'VARCHAR(50) NULL COMMENT ''联系人1座机''');
CALL add_column_if_not_exists('tenders', 'contact_mail',    'VARCHAR(100) NULL COMMENT ''联系人1邮箱''');
CALL add_column_if_not_exists('tenders', 'contact_name2',   'VARCHAR(100) NULL COMMENT ''联系人2姓名''');
CALL add_column_if_not_exists('tenders', 'contact_phone2',  'VARCHAR(50) NULL COMMENT ''联系人2手机号''');
CALL add_column_if_not_exists('tenders', 'contact_tel2',    'VARCHAR(50) NULL COMMENT ''联系人2座机''');
CALL add_column_if_not_exists('tenders', 'contact_mail2',  'VARCHAR(100) NULL COMMENT ''联系人2邮箱''');
CALL add_column_if_not_exists('tenders', 'project_type',   'VARCHAR(20) NULL COMMENT ''项目类型''');
CALL add_column_if_not_exists('tenders', 'project_manager_id',   'BIGINT NULL COMMENT ''项目负责人ID''');
CALL add_column_if_not_exists('tenders', 'project_manager_name', 'VARCHAR(100) NULL COMMENT ''项目负责人姓名''');
CALL add_column_if_not_exists('tenders', 'bidding_person_id',    'BIGINT NULL COMMENT ''投标负责人ID''');
CALL add_column_if_not_exists('tenders', 'bidding_person_name',  'VARCHAR(100) NULL COMMENT ''投标负责人姓名''');
CALL add_column_if_not_exists('tenders', 'department',     'VARCHAR(100) NULL COMMENT ''部门''');
CALL add_column_if_not_exists('tenders', 'distributor_id', 'BIGINT NULL COMMENT ''分配人ID''');
CALL add_column_if_not_exists('tenders', 'distributor_name', 'VARCHAR(100) NULL COMMENT ''分配人姓名''');
CALL add_column_if_not_exists('tenders', 'creator_id',     'BIGINT NULL COMMENT ''创建人ID''');
CALL add_column_if_not_exists('tenders', 'creator_name',  'VARCHAR(100) NULL COMMENT ''创建人姓名''');

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
