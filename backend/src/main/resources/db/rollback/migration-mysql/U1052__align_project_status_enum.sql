-- U1052: 回滚 projects.status 枚举至旧 6 值定义
ALTER TABLE projects MODIFY COLUMN status enum (
    'INITIATED',
    'PREPARING',
    'REVIEWING',
    'SEALING',
    'BIDDING',
    'ARCHIVED'
) NOT NULL;
