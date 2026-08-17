-- V1188: 业绩记录表新增合同金额列（spec 041）
-- 需求：AI 评分标准解析 — 业绩类评分项匹配需要合同金额门槛比对（research R7）
-- 存量行 NULL 视为"金额未知"，匹配时跳过金额门槛比对（不因 NULL 失配）。
--
-- 幂等设计（2026-08-17 修复）：
--   根因：V145__performance_library.sql 早已定义 performance_record.contract_amount
--   列（DECIMAL(15,2) COMMENT '合同金额(万元)' + 索引 idx_perf_amount）。
--   spec 041 调研 R7 未察觉 V145 已有该列，V1188 原始版本直接 ADD COLUMN 会导致
--   "Duplicate column name 'contract_amount'" 失败，阻塞全部后续迁移（V1189/V1190）。
--   修复方案：用 information_schema 检查列存在性，幂等处理：
--     - V145 已有列：跳过（保留 V145 的"万元"注释与索引）
--     - V145 无列（新装环境或 B73 baseline 未含 V145 场景）：ADD COLUMN + 索引
--   MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS（Flyway 9.22.3 会记 success=0），
--   改用存储过程 + information_schema 检查。
--
-- 单位语义：保留 V145 的"万元"约定（V145 注释），不改变存量数据语义。
-- spec 041 实体 PerformanceRecordEntity.contractAmount 直接使用 DECIMAL 比对，
-- 不做单位换算，故"万元"或"元"在代码层不产生差异（仅注释差异）。

DELIMITER $$
DROP PROCEDURE IF EXISTS add_contract_amount_if_missing$$
CREATE PROCEDURE add_contract_amount_if_missing()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'performance_record'
                     AND column_name = 'contract_amount') THEN
        ALTER TABLE performance_record
            ADD COLUMN contract_amount DECIMAL(15,2) NULL COMMENT '合同金额(万元)；NULL 视为金额未知' AFTER contract_name;
        ALTER TABLE performance_record
            ADD INDEX idx_perf_amount (contract_amount);
    END IF;
END$$
DELIMITER ;

CALL add_contract_amount_if_missing();
DROP PROCEDURE IF EXISTS add_contract_amount_if_missing;
