-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功删除 seed 的 24 个竞品、name 唯一约束、status 列
-- Pos: 与 V1202 配对，作为竞品字典治理迁移的回滚
-- 维护声明: 维护者按项目SOP；与 V1202 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1202__competitor_status_unique_seed.sql

-- U1202 rollback for V1202__competitor_status_unique_seed.sql（竞品 status/唯一约束/seed）
-- 注意：回滚会删除 seed 的 24 个竞品（若已被治理端改名/停用则按 name 可能无法删除），
--       并删除 name 唯一约束与 status 列，回滚前请确认业务允许。

-- 1. 删除 seed 的 24 个竞品
DELETE FROM competitors WHERE name IN (
  '震坤行','鑫方盛','浙江物产','欧菲斯','领先未来','浙江宏伟','咸亨国际','企事通',
  '一线达通','京东','苏宁','科力普','得力','史泰博','齐心','广博','一出科技',
  '怡亚通','申合信','大江科技','诚和致远','阳采','德致商成','全程速达');

-- 2. 删除 name 唯一约束
ALTER TABLE competitors DROP INDEX uk_competitor_name;

-- 3. 删除 status 列
ALTER TABLE competitors DROP COLUMN status;
