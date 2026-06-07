-- V1052: 对齐 projects.status 列枚举与 Java Project.Status 8 值枚举
-- 原因: DB 枚举仅有 6 个旧值 (INITIATED, PREPARING, REVIEWING, SEALING, BIDDING, ARCHIVED)，
--       而 Java Project.Status 已演进为 8 值 (PENDING_INITIATION, INITIATED, BIDDING,
--       EVALUATING, WON, LOST, FAILED, ABANDONED)。
--       使用 PENDING_INITIATION 插入时触发 "Data truncated for column 'status'" 错误，
--       导致 proceedToBid 投标立项失败。
ALTER TABLE projects MODIFY COLUMN status enum (
    'PENDING_INITIATION',
    'INITIATED',
    'BIDDING',
    'EVALUATING',
    'WON',
    'LOST',
    'FAILED',
    'ABANDONED'
) NOT NULL;
