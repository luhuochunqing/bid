-- V1092: 旧角色码迁移到 OSS 文档对齐的新角色码
-- 背景：P0-1 角色 code 统一，将系统内部角色码对齐 OSS 文档定义。
-- 旧 code（下划线风格）→ 新 code（camelCase/hyphen 风格）映射：
--   bid_admin       → bidAdmin
--   bid_lead        → bid-TeamLeader
--   sales           → bid-projectLeader
--   bid_specialist  → bid-Team
--   admin_staff     → bid-administration
--   bid_other_dept  → bid-otherDept
-- 注意：admin 保持不变，staff 已在 V1091 移除。
-- 安全策略：
--   1. 若新 code 角色已存在，将 users.role_id 从旧角色迁移到新角色，再删除旧角色
--   2. 若新 code 角色不存在，直接 UPDATE roles.code
--   3. 幂等：旧 code 不存在时 no-op

-- 1. bid_admin → bidAdmin
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bidAdmin' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_admin')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bidAdmin');
DELETE FROM roles WHERE code = 'bid_admin'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bidAdmin');
UPDATE roles SET code = 'bidAdmin', updated_at = NOW() WHERE code = 'bid_admin';

-- 2. bid_lead → bid-TeamLeader
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-TeamLeader' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_lead')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid-TeamLeader');
DELETE FROM roles WHERE code = 'bid_lead'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid-TeamLeader');
UPDATE roles SET code = 'bid-TeamLeader', updated_at = NOW() WHERE code = 'bid_lead';

-- 3. sales → bid-projectLeader
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-projectLeader' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'sales')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid-projectLeader');
DELETE FROM roles WHERE code = 'sales'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid-projectLeader');
UPDATE roles SET code = 'bid-projectLeader', updated_at = NOW() WHERE code = 'sales';

-- 4. bid_specialist → bid-Team
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-Team' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_specialist')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid-Team');
DELETE FROM roles WHERE code = 'bid_specialist'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid-Team');
UPDATE roles SET code = 'bid-Team', updated_at = NOW() WHERE code = 'bid_specialist';

-- 5. admin_staff → bid-administration
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-administration' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'admin_staff')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid-administration');
DELETE FROM roles WHERE code = 'admin_staff'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid-administration');
UPDATE roles SET code = 'bid-administration', updated_at = NOW() WHERE code = 'admin_staff';

-- 6. bid_other_dept → bid-otherDept
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-otherDept' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_other_dept')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid-otherDept');
DELETE FROM roles WHERE code = 'bid_other_dept'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid-otherDept');
UPDATE roles SET code = 'bid-otherDept', updated_at = NOW() WHERE code = 'bid_other_dept';
