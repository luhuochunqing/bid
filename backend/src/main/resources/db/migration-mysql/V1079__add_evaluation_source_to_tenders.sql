-- CO-232: 评估表数据来源标记，区分 CRM 推送 vs 投标系统主动关联
-- 功能：tenders 表新增 evaluation_source 列，标记评估数据来源入口
ALTER TABLE tenders
    ADD COLUMN evaluation_source VARCHAR(20) DEFAULT NULL COMMENT '评估表数据来源: CRM_PUSH/BID_SYSTEM_LINK'
    AFTER crm_opportunity_id;
