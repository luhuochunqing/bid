-- V1026: 评估表字段重构

-- 安全 DROP COLUMN（跳过不存在的列）
SET @stmt = NULL;
SELECT IF(count(*) > 0, 'ALTER TABLE tender_evaluations DROP COLUMN project_background', 'SELECT 1') INTO @stmt FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tender_evaluations' AND column_name='project_background';
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(count(*) > 0, 'ALTER TABLE tender_evaluations DROP COLUMN competitor_analysis', 'SELECT 1') INTO @stmt FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tender_evaluations' AND column_name='competitor_analysis';
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(count(*) > 0, 'ALTER TABLE tender_evaluations DROP COLUMN contract_period_start', 'SELECT 1') INTO @stmt FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tender_evaluations' AND column_name='contract_period_start';
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(count(*) > 0, 'ALTER TABLE tender_evaluations DROP COLUMN contract_period_end', 'SELECT 1') INTO @stmt FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tender_evaluations' AND column_name='contract_period_end';
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(count(*) > 0, 'ALTER TABLE tender_evaluations DROP COLUMN platform_service_fee', 'SELECT 1') INTO @stmt FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tender_evaluations' AND column_name='platform_service_fee';
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(count(*) > 0, 'ALTER TABLE tender_evaluations DROP COLUMN previous_quotation', 'SELECT 1') INTO @stmt FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tender_evaluations' AND column_name='previous_quotation';
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(count(*) > 0, 'ALTER TABLE tender_evaluations DROP COLUMN shortlisted_count', 'SELECT 1') INTO @stmt FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tender_evaluations' AND column_name='shortlisted_count';
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 重命名 tender_evaluation_basics 列
ALTER TABLE tender_evaluation_basics
    CHANGE COLUMN shortlisted_count planned_shortlisted_count INT NULL COMMENT '计划入围供应商数量',
    CHANGE COLUMN annual_procurement_amount mro_office_flow_amount DECIMAL(15,2) NULL COMMENT '电商MRO+办公流水金额（万）',
    CHANGE COLUMN risk_mitigation_plan contingency_plan TEXT NULL COMMENT '项目经理综合评估是否有兜底方案';