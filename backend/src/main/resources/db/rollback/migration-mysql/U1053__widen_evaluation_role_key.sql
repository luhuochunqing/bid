-- Input: V1053__widen_evaluation_role_key.sql
-- Rollback for V1053__widen_evaluation_role_key.sql

-- V1027: 扩展 tender_evaluation_customer_info.role_key 列宽以容纳超长枚举值
-- ELECTRONICS_COMPANY_GENERAL_MANAGER (35 chars)
-- ELECTRONICS_COMPANY_DEPUTY_GENERAL_MANAGER (42 chars)
-- ELECTRONICS_COMPANY_OPERATIONS_LEADER (37 chars)
-- MATERIALS_COMPANY_ELECTRONICS_LEADER (36 chars)
ALTER TABLE tender_evaluation_customer_info
    MODIFY COLUMN role_key VARCHAR(50) NOT NULL COMMENT '角色枚举键';
