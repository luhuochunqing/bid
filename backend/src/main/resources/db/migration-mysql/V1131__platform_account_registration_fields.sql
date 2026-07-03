-- V1131: 账户表新增注册人/注册手机/注册邮箱字段，删除绑定手机/绑定邮箱字段
-- CO-474 #1/1 账户管理模块 — 字段新增与修改（Linear: CO-474）

ALTER TABLE platform_accounts
  ADD COLUMN registrant VARCHAR(100) DEFAULT NULL COMMENT '注册人' AFTER remarks,
  ADD COLUMN register_phone VARCHAR(20) DEFAULT NULL COMMENT '注册手机' AFTER registrant,
  ADD COLUMN register_email VARCHAR(200) DEFAULT NULL COMMENT '注册邮箱' AFTER register_phone;

ALTER TABLE platform_accounts
  DROP COLUMN contact_phone,
  DROP COLUMN contact_email;
