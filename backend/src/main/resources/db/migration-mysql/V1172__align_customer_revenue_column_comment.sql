-- V1172: 对齐 customer_revenue 列 COMMENT 与代码注释单位
--
-- 背景：!564 回归修复的一部分。V147 创建 customer_revenue 时 COMMENT 写的"客户营收（万）"，
-- 但前端评估表 BasicFieldsSection.vue 与项目列表 List.vue 列标签均为"客户营收（亿）"，
-- 单位歧义曾导致 d1994a3fa 误把 det.annualEcommerceAmount 当成客户营收赋给 dto.revenue。
-- 本次统一为"亿"，消除代码-数据库-前端三层的单位不一致。
-- 仅修改列 COMMENT，不改动数据类型、精度、nullable 等结构属性。
-- 维护声明: source migration changes must update this rollback script in the same branch.

ALTER TABLE tender_evaluation_basics
    MODIFY COLUMN customer_revenue DECIMAL(15,2) DEFAULT NULL COMMENT '客户营收（亿）';
