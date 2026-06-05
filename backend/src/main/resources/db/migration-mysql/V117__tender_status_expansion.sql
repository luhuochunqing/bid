-- 标讯状态扩展与评分分析关联增强

-- 1. 扩展 tenders.status 枚举：先把新值并入旧值，便于后续 UPDATE 不被 ENUM 截断
ALTER TABLE tenders MODIFY status
  ENUM('PENDING','TRACKING','BIDDED','ABANDONED',
       'PENDING_ASSIGNMENT','EVALUATED','BIDDING','WON','LOST')
  NOT NULL;

-- 2. 更新已有的标讯状态（PENDING -> PENDING_ASSIGNMENT, BIDDED -> BIDDING）
UPDATE tenders SET status = 'PENDING_ASSIGNMENT' WHERE status = 'PENDING';
UPDATE tenders SET status = 'BIDDING' WHERE status = 'BIDDED';

-- 3. 收口 tenders.status 枚举为目标集合（去掉 PENDING、BIDDED 两个旧值）
ALTER TABLE tenders MODIFY status
  ENUM('PENDING_ASSIGNMENT','TRACKING','EVALUATED','BIDDING','WON','LOST','ABANDONED')
  NOT NULL;

-- 4. 增强评分分析表以支持标讯
ALTER TABLE score_analyses MODIFY project_id BIGINT NULL;
ALTER TABLE score_analyses ADD COLUMN tender_id BIGINT NULL AFTER project_id;
ALTER TABLE score_analyses ADD INDEX idx_score_analysis_tender (tender_id);
