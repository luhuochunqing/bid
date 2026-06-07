-- Input: migration-mysql/V130__tender_evaluation_three_sections.sql
-- Output: rollback script for mysql environments
-- U130: 回滚标讯评估表三段式重构
-- 恢复为旧结构：删除3张新表，移除tender_evaluations的4个新列
--
-- 警告：本操作会删除评估表的基础信息、客户信息EAV和投标负责人建议数据。
-- 生产环境执行前请确认已备份。

DROP TABLE IF EXISTS tender_evaluation_recommendation;
DROP TABLE IF EXISTS tender_evaluation_customer_info;
DROP TABLE IF EXISTS tender_evaluation_basics;

ALTER TABLE tender_evaluations
  DROP COLUMN IF EXISTS requires_review,
  DROP COLUMN IF EXISTS last_reviewed_by,
  DROP COLUMN IF EXISTS last_reviewed_at,
  DROP COLUMN IF EXISTS evaluation_round;
