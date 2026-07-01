-- CO-152: CRM token 按用户维度管理正式上线
-- 为 users 表新增 crm_sales_no 字段，存储用户的 CRM 工号。
-- 配置了 crm_sales_no 的用户将使用专属 CRM JWT token（按用户隔离），
-- 未配置的用户回退到全局共享 token（兼容存量行为）。
ALTER TABLE users ADD COLUMN crm_sales_no VARCHAR(64) NULL COMMENT 'CRM 工号，用于按用户维度换取 CRM JWT token（CO-152）';
