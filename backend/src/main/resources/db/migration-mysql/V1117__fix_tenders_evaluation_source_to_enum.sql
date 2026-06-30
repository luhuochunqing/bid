-- V1117: 修复 tenders.evaluation_source 列类型漂移
--
-- 背景：
-- - V1079 创建 evaluation_source VARCHAR(20)，但实体 Tender.evaluationSource 用
--   @Enumerated(EnumType.STRING) + enum EvaluationSource {CRM_PUSH, BID_SYSTEM_LINK}。
-- - Hibernate 6 + @Enumerated(EnumType.STRING) 在 ddl-auto=validate 时期望 MySQL ENUM 类型
--   （参考 V1113 修复模式与注释）。
-- - 当前 VARCHAR(20) 导致 Schema-validation 失败：found [varchar], expecting [enum]。
--
-- 修复策略：先清洗数据，再将 DB VARCHAR(20) → ENUM('CRM_PUSH','BID_SYSTEM_LINK')。
--
-- 数据清洗步骤（生产安全）：
--   1) 空字符串 → NULL（避免空字符串在 ENUM 中成为非法值）
--   2) 大小写归一化 → UPPER（防止 crm_push / Crm_Push 等变体）
--   3) 非法值 → NULL（无法识别的脏数据设空，不阻断迁移）
--   4) 再 ALTER TABLE 改列类型
--
-- 为什么是 3 步 UPDATE 而不是单条 SQL：
--   - Flyway 迁移必须是幂等的 DDL/DML 组合
--   - 数据已经正确时，每条 UPDATE 都是 no-op（0 rows affected）
--   - 严格 STRICT_TRANS_TABLES 模式下，ALTER 遇到非法值会直接失败
--
-- 风险评估：
-- - 非法值会被置为 NULL，不会导致迁移失败；业务侧可后续按 NULL 重刷
-- - MySQL 8.0 ENUM 修改是 INSTANT 元数据操作，不锁表

-- Step 1: 空字符串 → NULL
UPDATE tenders
    SET evaluation_source = NULL
    WHERE evaluation_source = '';

-- Step 2: 大小写归一化（UPPER）
UPDATE tenders
    SET evaluation_source = UPPER(evaluation_source)
    WHERE evaluation_source IS NOT NULL;

-- Step 3: 非法值 → NULL（只保留枚举定义中的合法值）
UPDATE tenders
    SET evaluation_source = NULL
    WHERE evaluation_source IS NOT NULL
      AND evaluation_source NOT IN ('CRM_PUSH', 'BID_SYSTEM_LINK');

-- Step 4: 改列类型为 ENUM
ALTER TABLE tenders
    MODIFY COLUMN evaluation_source
    ENUM('CRM_PUSH','BID_SYSTEM_LINK') DEFAULT NULL COMMENT '评估表数据来源: CRM_PUSH/BID_SYSTEM_LINK';
