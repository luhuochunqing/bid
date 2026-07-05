-- V1138: Expand brand_auth_attachment.attachment_type ENUM to support agent authorization types
-- 代理商授权有两个阶段的附件（授权1、授权2），原 ENUM 只支持 AUTH_DOC/SUPPLEMENTARY

ALTER TABLE brand_auth_attachment
    MODIFY COLUMN attachment_type ENUM('AUTH_DOC', 'SUPPLEMENTARY', 'AGENT_AUTH_1', 'AGENT_AUTH_2') NOT NULL;
