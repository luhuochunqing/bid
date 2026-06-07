-- Input: V1051__align_knowledge_case_customer_type.sql
-- Rollback for V1051__align_knowledge_case_customer_type.sql

-- U1051: 回滚 knowledge_case 客户类型枚举值
UPDATE knowledge_case SET customer_type = 'STATE_OWNED' WHERE customer_type = 'CENTRAL_SOE';
UPDATE knowledge_case SET customer_type = 'FOREIGN' WHERE customer_type = 'FOREIGN_ENTERPRISE';
