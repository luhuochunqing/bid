-- U1129: 回滚 V1129 — CA证书印章类型字段长度回退到 VARCHAR(30)
-- 注意：回退会截断已存储的多选值（逗号分隔），仅用于 V1129 失败时应急回滚
--
-- Input: V1129__ca_seal_type_multiselect.sql
ALTER TABLE ca_certificates
  MODIFY COLUMN seal_type VARCHAR(30) NOT NULL
  COMMENT '印章类型: OFFICIAL_SEAL(公章) / LEGAL_PERSON_SEAL(法人章) / LEGAL_SIGN(法人签字) / CONTACT_SIGN(联系人签字)';
