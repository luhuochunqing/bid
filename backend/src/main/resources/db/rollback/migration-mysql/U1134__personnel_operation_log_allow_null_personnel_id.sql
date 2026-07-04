-- Input: V1134__personnel_operation_log_allow_null_personnel_id.sql
-- 回滚 V1134：恢复 personnel_id 非空约束。
-- 注意：如果表中已存在 personnel_id 为 NULL 的批量操作日志，回滚前需先清理或填充这些记录。
ALTER TABLE personnel_operation_log MODIFY COLUMN personnel_id BIGINT NOT NULL;
