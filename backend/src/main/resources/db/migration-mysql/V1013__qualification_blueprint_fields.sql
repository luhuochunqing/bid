-- V1013: Align business_qualifications with blueprint §4.2 资质证书 — 新增资质
-- Add blueprint-required fields: 等级, 认证机构, 代理机构, 代理联系方式, 认证范围, 证书审核提醒
-- Add certificate_no unique index; rename issuer semantics

ALTER TABLE business_qualifications
    ADD COLUMN level VARCHAR(50) COMMENT '等级' AFTER name,
    ADD COLUMN agency VARCHAR(200) COMMENT '代理机构' AFTER issuer,
    ADD COLUMN agency_contact VARCHAR(200) COMMENT '代理联系方式' AFTER agency,
    ADD COLUMN cert_scope TEXT COMMENT '认证范围' AFTER agency_contact,
    ADD COLUMN cert_review_note VARCHAR(200) COMMENT '证书审核提醒' AFTER cert_scope;

-- Fill NULL/empty certificate_no to avoid unique index creation failure
UPDATE business_qualifications SET certificate_no = CONCAT('LEGACY_', id)
    WHERE certificate_no IS NULL OR certificate_no = '';

CREATE UNIQUE INDEX idx_cert_no_unique ON business_qualifications (certificate_no);
