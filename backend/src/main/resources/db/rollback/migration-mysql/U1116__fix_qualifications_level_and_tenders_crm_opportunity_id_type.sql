-- Input: migration-mysql/V1116__fix_qualifications_level_and_tenders_crm_opportunity_id_type.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1116: 回滚 V1116 — 恢复 qualifications.level ENUM 与 tenders.crm_opportunity_id BIGINT
--
-- 注意：回滚会丢失 V1116 之后写入的 VARCHAR 数据中无法转换为原类型的部分。
-- 仅在 V1116 上线后立即发现问题且数据未受污染时使用。
-- 生产回滚前必须先备份相关表。

-- 1. tenders.crm_opportunity_id: VARCHAR(64) → BIGINT
--    字符串非数字值会转为 0，需先确认数据可逆
ALTER TABLE tenders
    MODIFY COLUMN crm_opportunity_id BIGINT DEFAULT NULL COMMENT 'CRM商机ID（数值）';

-- 2. qualifications.level: VARCHAR(32) → ENUM
ALTER TABLE qualifications
    MODIFY COLUMN level
    ENUM('FIRST','SECOND','THIRD','OTHER') NOT NULL COMMENT '资质级别';
