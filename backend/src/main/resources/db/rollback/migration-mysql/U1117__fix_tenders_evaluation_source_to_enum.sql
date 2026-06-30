-- Input: migration-mysql/V1117__fix_tenders_evaluation_source_to_enum.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1117: 回滚 V1117 — 恢复 tenders.evaluation_source VARCHAR(20)
--
-- 数据影响：
-- - ENUM('CRM_PUSH','BID_SYSTEM_LINK') → VARCHAR(20) 自动保留字符串值，无数据丢失
-- - V1117 Step 3 中被置为 NULL 的非法值不会恢复（信息已丢失）
-- - 回滚后列类型为 VARCHAR，不再强制枚举约束
--
-- 注意：V1117 包含 4 步（空字符串清理 + 大小写归一化 + 非法值置空 + 改列类型），
-- 本回滚仅恢复列类型，前 3 步的数据清洗不可逆。
ALTER TABLE tenders
    MODIFY COLUMN evaluation_source VARCHAR(20) DEFAULT NULL COMMENT '评估表数据来源: CRM_PUSH/BID_SYSTEM_LINK';
