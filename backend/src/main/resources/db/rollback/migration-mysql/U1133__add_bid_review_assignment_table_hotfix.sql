-- U1133: 回滚 V1133 add bid review assignment table hotfix
--
-- 回滚策略：DROP TABLE bid_review_assignment
-- 数据影响：会丢失所有审核人分配记录（决策、驳回原因等）
-- 回滚前提：如需保留数据，请先备份：mysqldump xiyu_bid_main bid_review_assignment > backup.sql
--
-- 注意：回滚后，CO-483/484 多人审核功能将不可用，所有审核相关接口会重新报 500
-- Input: V1133__add_bid_review_assignment_table_hotfix.sql

DROP TABLE IF EXISTS bid_review_assignment;
