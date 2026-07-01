-- CO-152 回滚：删除 users.crm_sales_no 字段
-- Input: V1126__add_crm_sales_no_to_users.sql
ALTER TABLE users DROP COLUMN crm_sales_no;
