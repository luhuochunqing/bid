-- V1116: 修复 qualifications.level 与 tenders.crm_opportunity_id 列类型漂移
--
-- 背景：
-- - PlatformAccountBorrowServiceMysqlIntegrationTest 临时覆盖 ddl-auto=validate 后，
--   Hibernate 报告两类列类型不匹配：
--     1) qualifications.level: DB enum vs 实体 String（期望 varchar(255)）
--     2) tenders.crm_opportunity_id: DB bigint vs 实体 String length=64（期望 varchar(64)）
-- - 根因都是早期 DB 列类型与后来演进的实体字段类型不一致。
-- - 生产 (application-prod.yml) 也用 ddl-auto=validate，重启会触发同样失败。
--
-- 修复策略：改 DB 列类型对齐实体（与 V1113 同向）。
--   - qualifications.level: ENUM → VARCHAR(32)
--     * 实体 Qualification.level 是 String（非 enum），DB 应为 VARCHAR
--     * 现有 enum 值 FIRST/SECOND/THIRD/OTHER 最长 6 字符，VARCHAR(32) 留足余量
--     * 与同表 type 列保持不同语义：type 是 @Enumerated(EnumType.STRING) 期望 ENUM，level 是普通 String 期望 VARCHAR
--   - tenders.crm_opportunity_id: BIGINT → VARCHAR(64)
--     * V1006 创建列时是 BIGINT（CRM 商机 ID 当时按数值设计）
--     * 后续实体改为 String crmOpportunityId length=64（CRM 商机 ID 形如 "CRM-OPP-001"）
--     * V1089 已升级为 UNIQUE INDEX，MODIFY COLUMN 时 MySQL 8.0 会自动维护索引
--
-- 风险评估：
-- - qualifications.level: ENUM → VARCHAR 是 MySQL 8.0 INSTANT 元数据操作，不锁表；
--   现有数据 FIRST/SECOND/THIRD/OTHER 字符串值在 VARCHAR 下完全兼容。
-- - tenders.crm_opportunity_id: BIGINT → VARCHAR(64) 会将数值自动转为字符串；
--   现有数据若为 NULL 或纯数字，转换无数据风险；UNIQUE INDEX 自动跟随重建。
-- - 两列均允许 Hibernate ddl-auto=validate 在测试与生产 profile 下通过。

-- 1. qualifications.level: ENUM → VARCHAR(32)
ALTER TABLE qualifications
    MODIFY COLUMN level VARCHAR(32) NOT NULL COMMENT '资质级别（FIRST/SECOND/THIRD/OTHER）';

-- 2. tenders.crm_opportunity_id: BIGINT → VARCHAR(64)
--    MySQL 8.0 会自动维护 UNIQUE INDEX idx_tender_crm_opportunity_id
ALTER TABLE tenders
    MODIFY COLUMN crm_opportunity_id VARCHAR(64) DEFAULT NULL COMMENT 'CRM商机ID（字符串，如 CRM-OPP-001）';
