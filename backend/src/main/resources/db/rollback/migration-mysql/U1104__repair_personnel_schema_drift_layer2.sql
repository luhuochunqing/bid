-- U1104: 回滚 V1104 personnel schema 漂移第二层修复
-- V1104 是幂等修复脚本（补齐缺失列/表），回滚为空操作——删除列会破坏数据。
-- 如果需要完全回滚，请手动 DROP TABLE personnel_operation_log 并 ALTER TABLE ... DROP COLUMN。
-- 但强烈不建议回滚，因为这些列/表是业务正常运行所必需的。

SELECT 1;
