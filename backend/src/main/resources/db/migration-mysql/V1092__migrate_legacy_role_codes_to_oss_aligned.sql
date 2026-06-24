-- V1092: 旧角色码迁移到 OSS 文档对齐的新角色码
-- 背景：P0-1 角色 code 统一，将系统内部角色码对齐 OSS 文档定义。
-- 旧 code（下划线风格）→ 新 code（OSS 规范）映射：
--   bid_admin       → /bidAdmin          （注意：OSS 投标管理员带前导斜杠）
--   bid_lead        → bid-TeamLeader
--   sales           → bid-projectLeader
--   bid_specialist  → bid-Team
--   admin_staff     → bid-administration
--   bid_other_dept  → bid-otherDept
-- 注意：admin 保持不变，staff 已在 V1091 移除。
--
-- 安全策略（顺序至关重要，避免 users.role_id 被置 NULL）：
--   步骤 1: 若新 code 不存在，直接 UPDATE roles.code（users.role_id 不变，仍指向同一行）
--   步骤 2: 若新 code 已存在（新旧两条记录），将 users.role_id 从旧角色迁移到新角色，再删除旧角色
--   幂等：旧 code 不存在时 no-op
--
-- MySQL 兼容性：MySQL 1093 禁止 UPDATE 目标表出现在子查询 FROM 中。
--   用派生表包裹子查询：(SELECT 1 FROM (SELECT 1 FROM roles WHERE ...) AS tmp)
--
-- 事务：使用显式事务保证原子性，中途失败回滚避免半迁移状态

START TRANSACTION;

-- 0. bidAdmin（camelCase，来自 V1074 等历史迁移）→ /bidAdmin（OSS 规范带斜杠）
-- 0a. 若 /bidAdmin 不存在，直接更新 roles.code
UPDATE roles SET code = '/bidAdmin', updated_at = NOW()
WHERE code = 'bidAdmin'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM roles WHERE code = '/bidAdmin') AS tmp);
-- 0b. 若 /bidAdmin 已存在（新旧两条记录），迁移 users 并删除旧角色
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = '/bidAdmin' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bidAdmin');
DELETE FROM roles WHERE code = 'bidAdmin';

-- 1. bid_admin → /bidAdmin
-- 1a. 若 /bidAdmin 不存在，直接更新 roles.code
UPDATE roles SET code = '/bidAdmin', updated_at = NOW()
WHERE code = 'bid_admin'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM roles WHERE code = '/bidAdmin') AS tmp);
-- 1b. 若 /bidAdmin 已存在（新旧两条记录），迁移 users 并删除旧角色
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = '/bidAdmin' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_admin');
DELETE FROM roles WHERE code = 'bid_admin';

-- 2. bid_lead → bid-TeamLeader
UPDATE roles SET code = 'bid-TeamLeader', updated_at = NOW()
WHERE code = 'bid_lead'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM roles WHERE code = 'bid-TeamLeader') AS tmp);
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-TeamLeader' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_lead');
DELETE FROM roles WHERE code = 'bid_lead';

-- 3. sales → bid-projectLeader
UPDATE roles SET code = 'bid-projectLeader', updated_at = NOW()
WHERE code = 'sales'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM roles WHERE code = 'bid-projectLeader') AS tmp);
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-projectLeader' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'sales');
DELETE FROM roles WHERE code = 'sales';

-- 4. bid_specialist → bid-Team
UPDATE roles SET code = 'bid-Team', updated_at = NOW()
WHERE code = 'bid_specialist'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM roles WHERE code = 'bid-Team') AS tmp);
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-Team' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_specialist');
DELETE FROM roles WHERE code = 'bid_specialist';

-- 5. admin_staff → bid-administration
UPDATE roles SET code = 'bid-administration', updated_at = NOW()
WHERE code = 'admin_staff'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM roles WHERE code = 'bid-administration') AS tmp);
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-administration' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'admin_staff');
DELETE FROM roles WHERE code = 'admin_staff';

-- 6. bid_other_dept → bid-otherDept
UPDATE roles SET code = 'bid-otherDept', updated_at = NOW()
WHERE code = 'bid_other_dept'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM roles WHERE code = 'bid-otherDept') AS tmp);
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-otherDept' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_other_dept');
DELETE FROM roles WHERE code = 'bid_other_dept';

COMMIT;
