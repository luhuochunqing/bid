-- V1019: Add agent authorization fields to manufacturer_authorization
-- §4.6 #1/8 新增授权 - agent authorization two-tier support

ALTER TABLE manufacturer_authorization
  ADD COLUMN authorization_type VARCHAR(20) NOT NULL DEFAULT 'MANUFACTURER' COMMENT 'MANUFACTURER|AGENT' AFTER id,
  ADD COLUMN agent_name VARCHAR(200) DEFAULT NULL COMMENT '代理商名称' AFTER manufacturer_name,
  ADD COLUMN auth1_start_date DATE DEFAULT NULL COMMENT '授权1开始时间（原厂→代理商）' AFTER auth_end_date,
  ADD COLUMN auth1_end_date DATE DEFAULT NULL COMMENT '授权1结束时间' AFTER auth1_start_date,
  ADD COLUMN auth1_remarks VARCHAR(1000) DEFAULT NULL COMMENT '授权1备注' AFTER auth1_end_date,
  ADD COLUMN auth2_start_date DATE DEFAULT NULL COMMENT '授权2开始时间（代理商→西域）' AFTER auth1_remarks,
  ADD COLUMN auth2_end_date DATE DEFAULT NULL COMMENT '授权2结束时间' AFTER auth2_start_date,
  ADD COLUMN auth2_remarks VARCHAR(1000) DEFAULT NULL COMMENT '授权2备注' AFTER auth2_end_date;
