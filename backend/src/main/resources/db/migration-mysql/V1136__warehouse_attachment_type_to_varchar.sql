-- V1136: Alter warehouse_attachment.type from MySQL ENUM to VARCHAR(30)
-- 根因：warehouse_attachment 表的 type 列定义为 enum('PROPERTY_CERTIFICATE','INVOICE','PHOTOS')，
-- 但 Java 枚举 WarehouseAttachmentType 有 4 个值（多了 LEASE_CONTRACT）。
-- 上传租赁合同附件时写入 LEASE_CONTRACT 被 MySQL enum 列拒绝（"Data truncated for column 'type'"）→ 500。
--
-- 修复：将列类型改为 VARCHAR(30) NOT NULL，对齐实体 @Column(length=30) 与 JPA @Enumerated(STRING)。
-- VARCHAR(30) 容纳所有现有及未来枚举值，Java 枚举仍是唯一源，DB 不再用 enum 重复约束。
-- 数据无损：现有 PROPERTY_CERTIFICATE/INVOICE/PHOTOS 字符串值在 VARCHAR 列中保持不变。
--
-- 幂等性：使用 information_schema 判断当前列类型，若已是 VARCHAR 则跳过。
-- Backout: see db/rollback/migration-mysql/U1136__warehouse_attachment_type_to_varchar.sql

SET @col_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse_attachment' AND COLUMN_NAME = 'type');
SET @sql = IF(@col_type = 'enum', 'ALTER TABLE warehouse_attachment MODIFY COLUMN type VARCHAR(30) NOT NULL COMMENT ''附件类型''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
