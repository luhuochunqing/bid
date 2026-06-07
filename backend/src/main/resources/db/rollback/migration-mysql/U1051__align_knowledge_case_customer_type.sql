-- Input: migration-mysql/V1051__align_knowledge_case_customer_type.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- U1051: 回滚 knowledge_case 客户类型枚举值
UPDATE knowledge_case SET customer_type = 'STATE_OWNED' WHERE customer_type = 'CENTRAL_SOE';