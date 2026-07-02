-- U1128: 回滚 V1128 — 删除 ca_borrow_applications.applicant_employee_number 列
-- 注意：
--   1) applicant_name 不回退到 username（旧值本来就是错误数据，回退会重新触发 CO-465 bug）
--   2) 删列前会丢失回填的工号信息，但不会影响借用流程的核心功能
--
-- Input: V1128__add_applicant_employee_number_to_ca_borrow_applications.sql
ALTER TABLE ca_borrow_applications
    DROP COLUMN applicant_employee_number;
