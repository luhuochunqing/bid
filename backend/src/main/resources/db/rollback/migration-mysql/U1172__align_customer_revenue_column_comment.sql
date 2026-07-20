-- Input: migration-mysql/V1172__align_customer_revenue_column_comment.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1172：把 customer_revenue 列 COMMENT 从"客户营收（亿）"改回 V147 原值"客户营收（万）"。
-- 仅回滚 COMMENT，不改动数据类型/精度/nullable；不影响数据，无数据迁移。
-- No-op rollback: COMMENT 修改不影响应用功能，回滚仅为 schema 元数据复原，
--                  生产环境可选择不回滚（COMMENT 单位语义已通过代码侧统一为"亿"）。
--                  若确需回滚，执行下方 ALTER 即可。
ALTER TABLE tender_evaluation_basics
    MODIFY COLUMN customer_revenue DECIMAL(15,2) DEFAULT NULL COMMENT '客户营收（万）';
