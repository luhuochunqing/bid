-- U1130: 回滚 V1130 — 人员教育经历入学时间回退为非空
-- 注意：回退前需确保所有已存在记录的 start_date 均非空，否则 ALTER 将失败
--
-- Input: V1130__personnel_education_start_date_nullable.sql
UPDATE personnel_education SET start_date = '1970-01-01' WHERE start_date IS NULL;
ALTER TABLE personnel_education
    MODIFY COLUMN start_date DATE NOT NULL COMMENT '入学时间（年-月）';
