-- V1088: 修复 projects.status 与 stage + bid_result_status 不一致的历史数据
--
-- 根因：ProjectStageService.syncProjectStatus() 之前调用
-- ProjectStatusPolicy.compute(targetStage, null, true)
-- bidResult 传了 null，导致 RETROSPECTIVE/CLOSED 阶段的项目 status
-- 永远算成 BIDDING/INITIATED，而不是 WON/LOST/FAILED/ABANDONED。
--
-- 代码修复：requestTransition 新增 bidResult 参数重载。
-- 本脚本修复历史数据（idempotent，可重复执行）。

UPDATE projects p
JOIN project_initiation_details pid ON pid.project_id = p.id
SET p.status = CASE pid.bid_result_status
    WHEN 'WON' THEN 'WON'
    WHEN 'LOST' THEN 'LOST'
    WHEN 'FAILED' THEN 'FAILED'
    WHEN 'ABANDONED' THEN 'ABANDONED'
    ELSE 'BIDDING'
END
WHERE p.stage = 'RETROSPECTIVE'
  AND p.status NOT IN ('WON', 'LOST', 'FAILED', 'ABANDONED');

UPDATE projects p
JOIN project_initiation_details pid ON pid.project_id = p.id
SET p.status = CASE pid.bid_result_status
    WHEN 'WON' THEN 'WON'
    WHEN 'LOST' THEN 'LOST'
    WHEN 'FAILED' THEN 'FAILED'
    WHEN 'ABANDONED' THEN 'ABANDONED'
    ELSE 'INITIATED'
END
WHERE p.stage = 'CLOSED'
  AND p.status NOT IN ('WON', 'LOST', 'FAILED', 'ABANDONED');
