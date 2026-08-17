-- Input: migration-mysql/V1191__eval_form_field_label_update_and_gap_text_deprecation.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1191: 回滚 V1191（评估表字段文案调整 + GAP 文本字段下线）
-- 仅恢复列 COMMENT 至 V1191 之前的状态，不改动数据类型、精度、nullable。
-- 注意：pm_understands_process 在 V1191 之前无 COMMENT（V1139 MODIFY 时已清空），
-- 回滚时同样不带 COMMENT 以保持原状。

ALTER TABLE tender_evaluation_basics
    MODIFY COLUMN mro_office_flow_amount DECIMAL(15,2) DEFAULT NULL COMMENT '电商MRO+办公流水金额（万）',
    MODIFY COLUMN customer_revenue DECIMAL(15,2) DEFAULT NULL COMMENT '客户营收（亿）',
    MODIFY COLUMN process_knowledge TEXT DEFAULT NULL COMMENT '项目经理是否了解评标全流程',
    MODIFY COLUMN project_plan_gap TEXT DEFAULT NULL COMMENT '项目计划GAP';

ALTER TABLE project_initiation_details
    MODIFY COLUMN annual_ecommerce_amount DECIMAL(20,2) DEFAULT NULL COMMENT '年度电商采购额(万)',
    MODIFY COLUMN annual_revenue DECIMAL(20,2) DEFAULT NULL,
    MODIFY COLUMN pm_understands_process TEXT DEFAULT NULL,
    MODIFY COLUMN project_plan_gap TEXT DEFAULT NULL COMMENT '项目计划GAP说明';
