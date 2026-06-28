-- V122: 历史数据修复 — 项目已进入终态但标讯仍停在 BIDDING 的脏数据
-- 修复标讯转项目后状态断链问题（代码层同步逻辑由 TenderStatusSyncService 实现）
--
-- 背景：Tender.projectId 注释承诺"项目状态变更时回填标讯状态"，但该逻辑此前未实现，
-- 导致项目结果登记为 WON/LOST/FAILED/ABANDONED 后，标讯状态永久停留在 BIDDING。
-- 本迁移一次性修复已存在的脏数据；后续新数据由 TenderStatusSyncService 自动同步。
--
-- 映射规则（与 TenderStatusSyncService.mapToTenderStatus 一致）：
--   Project.WON       → Tender.WON
--   Project.LOST      → Tender.LOST
--   Project.FAILED    → Tender.LOST（Tender 无 FAILED 状态，流标归一到 LOST）
--   Project.ABANDONED → Tender.ABANDONED

UPDATE tenders t
JOIN projects p ON p.tender_id = t.id
SET t.status = CASE p.status
    WHEN 'WON' THEN 'WON'
    WHEN 'LOST' THEN 'LOST'
    WHEN 'FAILED' THEN 'LOST'
    WHEN 'ABANDONED' THEN 'ABANDONED'
    ELSE t.status
END
WHERE t.status = 'BIDDING'
  AND p.status IN ('WON', 'LOST', 'FAILED', 'ABANDONED');
