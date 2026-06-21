-- Input: V1088__fix_project_status_from_bid_result.sql
-- 回滚说明：V1088 是数据修复脚本，根据 project_initiation_details.bid_result_status
-- 修正 projects.status 的历史数据。此操作不可逆回滚（修复前 status 是错误值）。
-- 如需回滚，请从 DB 备份恢复 projects 表到 V1088 执行前的快照。
Manual rollback required: restore projects table from pre-V1088 backup snapshot.