-- V1016: Add blueprint fields to platform_accounts table
-- §5 #2/13 新增账号 - adds URL, contact info, CA flag, custodian, remarks

ALTER TABLE platform_accounts
  ADD COLUMN url VARCHAR(500) DEFAULT NULL COMMENT '平台官网或登录入口' AFTER platform_type,
  ADD COLUMN contact_person VARCHAR(200) DEFAULT NULL COMMENT '绑定联系人 建议格式: 姓名(工号)' AFTER account_name,
  ADD COLUMN contact_phone VARCHAR(20) DEFAULT NULL COMMENT '绑定手机' AFTER contact_person,
  ADD COLUMN contact_email VARCHAR(200) DEFAULT NULL COMMENT '绑定邮箱' AFTER contact_phone,
  ADD COLUMN has_ca TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有CA 0=否 1=是' AFTER contact_email,
  ADD COLUMN ca_custodian BIGINT DEFAULT NULL COMMENT 'CA保管人用户ID' AFTER has_ca,
  ADD COLUMN remarks VARCHAR(500) DEFAULT NULL COMMENT '备注' AFTER ca_custodian;
