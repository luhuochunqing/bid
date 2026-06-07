-- V1030: warehouse 子表 ENUM 列类型修复
-- preferred_enum_type=jdbc 要求 @Enumerated(EnumType.STRING) 字段使用 MySQL ENUM 类型
-- 与 V1014 (brand_auth_attachment) 同类修复
-- 幂等：仅当表存在时才执行 MODIFY COLUMN

DELIMITER $$

DROP PROCEDURE IF EXISTS v1030_modify_column_if_table_exists$$
CREATE PROCEDURE v1030_modify_column_if_table_exists(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_col_def VARCHAR(500)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table, ' MODIFY COLUMN ', p_column, ' ', p_col_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL v1030_modify_column_if_table_exists('warehouse_attachment', 'type',
    'ENUM(''PROPERTY_CERTIFICATE'',''INVOICE'',''PHOTOS'') NOT NULL');
CALL v1030_modify_column_if_table_exists('warehouse_import_task', 'status',
    'ENUM(''PENDING'',''VALIDATING'',''VALIDATED'',''IMPORTING'',''COMPLETED'',''FAILED'') NOT NULL');
CALL v1030_modify_column_if_table_exists('warehouse_export_task', 'status',
    'ENUM(''PENDING'',''PROCESSING'',''COMPLETED'',''FAILED'') NOT NULL');
CALL v1030_modify_column_if_table_exists('warehouse_operation_log', 'action_type',
    'ENUM(''CREATE'',''EDIT'',''CLOSE'',''RESTORE'',''ATTACH_UPLOAD'',''ATTACH_DELETE'') NOT NULL');

DROP PROCEDURE IF EXISTS v1030_modify_column_if_table_exists;
