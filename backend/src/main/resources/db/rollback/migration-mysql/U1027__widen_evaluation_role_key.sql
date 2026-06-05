-- Input: V1027__widen_evaluation_role_key.sql
-- Rollback for V1027__widen_evaluation_role_key.sql
ALTER TABLE tender_evaluation_customer_info
    MODIFY COLUMN role_key VARCHAR(32) NOT NULL COMMENT '角色枚举键';
