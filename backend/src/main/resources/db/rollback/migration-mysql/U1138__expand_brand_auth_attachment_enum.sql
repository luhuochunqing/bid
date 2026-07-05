-- U1138: 回滚 brand_auth_attachment 枚举扩展（恢复为 AUTH_DOC/SUPPLEMENTARY 两值）
-- Source: V1138__expand_brand_auth_attachment_enum.sql
-- Input: V1138__expand_brand_auth_attachment_enum.sql
-- Data rollback required: 将 AGENT_AUTH_1/AGENT_AUTH_2 数据转换为 AUTH_DOC，再收窄 ENUM

-- 1. 先将代理商附件类型数据合并到 AUTH_DOC，避免收窄时数据丢失
UPDATE brand_auth_attachment
   SET attachment_type = 'AUTH_DOC'
 WHERE attachment_type IN ('AGENT_AUTH_1', 'AGENT_AUTH_2');

-- 2. 收窄 ENUM 回到原 2 值
ALTER TABLE brand_auth_attachment
    MODIFY COLUMN attachment_type ENUM('AUTH_DOC', 'SUPPLEMENTARY') NOT NULL;
