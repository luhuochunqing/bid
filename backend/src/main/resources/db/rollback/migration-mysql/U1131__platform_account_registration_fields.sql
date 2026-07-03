-- U1131: 回滚 V1131 — 恢复绑定手机号/绑定邮箱，删除注册人/注册手机/注册邮箱
-- Input: V1131__platform_account_registration_fields.sql

ALTER TABLE platform_accounts
  ADD COLUMN contact_phone VARCHAR(20) DEFAULT NULL COMMENT '绑定手机' AFTER contact_person,
  ADD COLUMN contact_email VARCHAR(200) DEFAULT NULL COMMENT '绑定邮箱' AFTER contact_phone;

ALTER TABLE platform_accounts
  DROP COLUMN registrant,
  DROP COLUMN register_phone,
  DROP COLUMN register_email;
