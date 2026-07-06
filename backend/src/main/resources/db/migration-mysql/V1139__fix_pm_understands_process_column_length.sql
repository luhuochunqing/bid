-- V1139: 修复 pm_understands_process 字段长度不匹配
-- 问题：tender_evaluation_basics.process_knowledge (TEXT/5000) 映射到
--       project_initiation_details.pm_understands_process (VARCHAR(16))
-- 当评估表 process_knowledge 内容超过 16 字符时，触发 Data truncation 异常，
-- 导致 InitiationPrefillService.prefillFromEvaluation 抛异常，
-- 事务被标记 rollback-only，最终导致整个 proceedToBid 事务回滚（项目创建失败）。
-- 修复：将 pm_understands_process 从 VARCHAR(16) 改为 TEXT，与源字段对齐。
ALTER TABLE project_initiation_details MODIFY COLUMN pm_understands_process TEXT;
