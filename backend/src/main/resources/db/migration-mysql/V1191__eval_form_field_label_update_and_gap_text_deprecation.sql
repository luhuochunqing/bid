-- V1191: 评估表字段文案调整 + GAP 文本字段下线（客户需求，2026-08-17）
--
-- 背景：
-- 1. 需求 1：「电商MRO+办公流水金额（万）」→「客户电商年采购金额（万）」（纯文案，列名/key 不变）
-- 2. 需求 2：「客户营收（亿）」→「客户年营收（亿）」（纯文案，列名/key 不变）
-- 3. 需求 3：项目计划 GAP 文本框下线——仅更新注释标注停写；列与存量数据保留，
--    GAP 附件链路不变（project_documents，linkedEntityType=EVALUATION_GAP，CO-262）
-- 4. 需求 4：「项目经理是否了解评标全流程」→「项目经理评标全流程概述」（纯文案，列名/key 不变）
--
-- 仅修改列 COMMENT，不改动数据类型、精度、nullable 等结构属性（参考 V1172 做法）。
-- 注意：pm_understands_process 的 COMMENT 在 V1139 MODIFY 时被清空，本次一并补齐。
-- 维护声明: source migration changes must update this rollback script in the same branch.

ALTER TABLE tender_evaluation_basics
    MODIFY COLUMN mro_office_flow_amount DECIMAL(15,2) DEFAULT NULL COMMENT '客户电商年采购金额（万）',
    MODIFY COLUMN customer_revenue DECIMAL(15,2) DEFAULT NULL COMMENT '客户年营收（亿）',
    MODIFY COLUMN process_knowledge TEXT DEFAULT NULL COMMENT '项目经理评标全流程概述',
    MODIFY COLUMN project_plan_gap TEXT DEFAULT NULL COMMENT '项目计划GAP（2026-08-17 起停写文本，附件见 project_documents/EVALUATION_GAP）';

ALTER TABLE project_initiation_details
    MODIFY COLUMN annual_ecommerce_amount DECIMAL(20,2) DEFAULT NULL COMMENT '客户电商年采购金额（万）',
    MODIFY COLUMN annual_revenue DECIMAL(20,2) DEFAULT NULL COMMENT '客户年营收（亿）',
    MODIFY COLUMN pm_understands_process TEXT DEFAULT NULL COMMENT '项目经理评标全流程概述',
    MODIFY COLUMN project_plan_gap TEXT DEFAULT NULL COMMENT '项目计划GAP（2026-08-17 起停写文本，附件见 project_documents/EVALUATION_GAP）';
