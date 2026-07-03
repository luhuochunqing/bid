-- V1129: CA证书印章类型改为多选
-- §4.5 #1/1 CA证书管理 — 扩展seal_type字段支持多选印章类型

ALTER TABLE ca_certificates
  MODIFY COLUMN seal_type VARCHAR(100) NOT NULL
  COMMENT '印章类型(多选,英文逗号分隔): OFFICIAL_SEAL(公章),LEGAL_PERSON_SEAL(法人章),LEGAL_SIGN(法人签字),CONTACT_SIGN(联系人签字)';
