-- Rollback: V117__tender_status_expansion.sql
-- Input: migration-mysql/V117__tender_status_expansion.sql
-- Output: rollback for mysql environments; review data-loss comments before production use.

-- 警告：数据丢失风险
-- 1) 回滚会 DROP score_analyses 新增的 tender_id 列及其索引（与标讯关联的评分分析记录会失去指向）。
-- 2) 会把 tenders.status 的目标 7 值枚举收回到旧版 4 值，并把 PENDING_ASSIGNMENT/BIDDING 数据反向映射回 PENDING/BIDDED；
--    EVALUATED/WON/LOST 没有对应旧值，回滚会抛 data-truncated 错；如库中存在这些状态的数据，必须先手动迁移后再跑本脚本。

-- 1. score_analyses 关联回滚
ALTER TABLE score_analyses DROP INDEX idx_score_analysis_tender;
ALTER TABLE score_analyses DROP COLUMN IF EXISTS tender_id;
-- project_id 可空改回 NOT NULL 需要先确保所有行 project_id 非空，否则会报错
ALTER TABLE score_analyses MODIFY project_id BIGINT NOT NULL;

-- 2. tenders.status 枚举回滚
-- 2.1 先扩到包含新旧值的并集，便于数据反向映射
ALTER TABLE tenders MODIFY status
  ENUM('PENDING','TRACKING','BIDDED','ABANDONED',
       'PENDING_ASSIGNMENT','EVALUATED','BIDDING','WON','LOST')
  NOT NULL;

-- 2.2 反向数据迁移
UPDATE tenders SET status = 'PENDING' WHERE status = 'PENDING_ASSIGNMENT';
UPDATE tenders SET status = 'BIDDED' WHERE status = 'BIDDING';

-- 2.3 收口回旧版枚举集合（如有 EVALUATED/WON/LOST 数据，需先人工处理，否则下一条 ALTER 会报错）
ALTER TABLE tenders MODIFY status
  ENUM('PENDING','TRACKING','BIDDED','ABANDONED')
  NOT NULL;
