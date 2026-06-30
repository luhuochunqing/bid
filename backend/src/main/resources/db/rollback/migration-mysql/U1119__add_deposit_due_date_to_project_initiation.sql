-- Input: V1119__add_deposit_due_date_to_project_initiation.sql
-- 回滚 V1119：删除 project_initiation_details.deposit_due_date 列
-- 注意：回滚前请确认没有任务依赖此字段作为 dueDate 来源

ALTER TABLE project_initiation_details
    DROP COLUMN deposit_due_date;
