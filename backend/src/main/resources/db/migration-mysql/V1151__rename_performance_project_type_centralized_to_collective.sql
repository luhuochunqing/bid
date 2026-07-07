-- V1151__rename_performance_project_type_centralized_to_collective.sql
-- 背景：Sentry XIYU-Y InvalidDataAccessApiUsageException: 无效的项目类型: COLLECTIVE
-- 根因：performance 模块 ProjectType 枚举"集采"名为 CENTRALIZED，与立项模块
--       InitiationFieldPolicy.ProjectType 的 COLLECTIVE 不一致；前端按立项模块
--       统一传 COLLECTIVE，导致后端 Enum.valueOf 失败抛 IllegalArgumentException。
-- 修复：performance.ProjectType 枚举值 CENTRALIZED → COLLECTIVE（已改代码），
--       存量数据 project_type='CENTRALIZED' 必须同步更新为 'COLLECTIVE'，
--       否则旧数据查询筛选失效。
-- 影响范围：performance_record.project_type 字段。

UPDATE performance_record
SET project_type = 'COLLECTIVE'
WHERE project_type = 'CENTRALIZED';
