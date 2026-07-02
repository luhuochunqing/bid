-- V1130__personnel_education_start_date_nullable.sql
-- 知识库 / 人员证书 / 教育经历：入学时间（start_date）改为选填
-- 业务原因：蓝图 4.3 "新增证书" Tab 2 教育经历，入学时间不再强制必填，
--          仅毕业时间为必填项（毕业时间用于标识学历完成）。
-- Flyway migration MySQL 8.0

ALTER TABLE personnel_education
    MODIFY COLUMN start_date DATE NULL COMMENT '入学时间（年-月，选填）';
