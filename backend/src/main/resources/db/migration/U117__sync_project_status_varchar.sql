-- U117: 回滚 — 恢复 projects.status 为旧 enum 定义
-- 注意：如果数据中有 V117 之后写入的新枚举值，回滚可能导致数据丢失
-- PR: N/A (standalone fix)
UPDATE projects SET status = 'INITIATED' WHERE status NOT IN (
  'INITIATED','PREPARING','REVIEWING','SEALING','BIDDING','ARCHIVED'
);
ALTER TABLE projects MODIFY COLUMN status ENUM(
  'INITIATED','PREPARING','REVIEWING','SEALING','BIDDING','ARCHIVED'
) NOT NULL;
