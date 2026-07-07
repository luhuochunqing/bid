-- Input: V1151__rename_performance_project_type_centralized_to_collective.sql
-- U1151__rename_performance_project_type_centralized_to_collective.sql
-- 回滚 V1151：将 performance_record.project_type='COLLECTIVE' 回退为 'CENTRALIZED'
-- 注意：回滚后需同步回退后端枚举代码（ProjectType.COLLECTIVE → CENTRALIZED），
--       否则会重新触发 Sentry XIYU-Y 异常。

UPDATE performance_record
SET project_type = 'CENTRALIZED'
WHERE project_type = 'COLLECTIVE';
