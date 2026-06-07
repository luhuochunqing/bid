-- U1051: 回滚 knowledge_case 客户类型枚举值
UPDATE knowledge_case SET customer_type = 'STATE_OWNED' WHERE customer_type = 'CENTRAL_SOE';
UPDATE knowledge_case SET customer_type = 'FOREIGN' WHERE customer_type = 'FOREIGN_ENTERPRISE';
