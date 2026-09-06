-- Input: migration-mysql/V1212__expand_compliance_check_details_to_mediumtext.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: 维护者按项目SOP；与 V1212 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest

-- U1212: 回滚 expand_compliance_check_details_to_mediumtext
-- 注意: 若已有记录超过 64KB，回退为 TEXT 可能导致内容被截断
ALTER TABLE compliance_check_results
    MODIFY COLUMN check_details TEXT NULL COMMENT '检查详情（JSON格式）';

