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
-- MySQL 兼容性：使用临时表 tmp_existing_new_codes 缓存"新 code 是否已存在"的判断结果，
--   避免 UPDATE roles ... WHERE NOT EXISTS (SELECT 1 FROM roles ...) 触发
--   ERROR 1093 (HY000): You can't specify target table 'roles' for update in FROM clause。
--   每完成一组迁移后同步刷新临时表，保证后续步骤看到最新状态。
--
-- 事务：使用显式事务保证原子性，中途失败回滚避免半迁移状态

START TRANSACTION;

-- 创建临时表存放"已存在的新 code"，避免 UPDATE roles 时 self-reference
DROP TEMPORARY TABLE IF EXISTS tmp_existing_new_codes;
CREATE TEMPORARY TABLE tmp_existing_new_codes (
  code VARCHAR(100) PRIMARY KEY
);
INSERT IGNORE INTO tmp_existing_new_codes (code)
SELECT code FROM roles
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-projectLeader', 'bid-Team', 'bid-administration', 'bid-otherDept');

-- 0. bidAdmin（camelCase，来自 V1074 等历史迁移）→ /bidAdmin（OSS 规范带斜杠）
-- 0a. 若 /bidAdmin 不存在，直接更新 roles.code（users.role_id 不变，仍指向同一行）
UPDATE roles SET code = '/bidAdmin', updated_at = NOW()
WHERE code = 'bidAdmin'
  AND NOT EXISTS (SELECT 1 FROM tmp_existing_new_codes WHERE code = '/bidAdmin');
-- 0b. 若 /bidAdmin 已存在（新旧两条记录），迁移 users 并删除旧角色
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = '/bidAdmin' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bidAdmin');
DELETE FROM roles WHERE code = 'bidAdmin';
-- 无论 0a 还是 0b 执行，/bidAdmin 现在都已存在，刷新临时表供后续步骤判断
INSERT IGNORE INTO tmp_existing_new_codes (code) VALUES ('/bidAdmin');

-- 1. bid_admin → /bidAdmin
-- 1a. 若 /bidAdmin 不存在，直接更新 roles.code
UPDATE roles SET code = '/bidAdmin', updated_at = NOW()
WHERE code = 'bid_admin'
  AND NOT EXISTS (SELECT 1 FROM tmp_existing_new_codes WHERE code = '/bidAdmin');
-- 1b. 若 /bidAdmin 已存在（新旧两条记录），迁移 users 并删除旧角色
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = '/bidAdmin' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_admin');
DELETE FROM roles WHERE code = 'bid_admin';
INSERT IGNORE INTO tmp_existing_new_codes (code) VALUES ('/bidAdmin');

-- 2. bid_lead → bid-TeamLeader
UPDATE roles SET code = 'bid-TeamLeader', updated_at = NOW()
WHERE code = 'bid_lead'
  AND NOT EXISTS (SELECT 1 FROM tmp_existing_new_codes WHERE code = 'bid-TeamLeader');
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-TeamLeader' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_lead');
DELETE FROM roles WHERE code = 'bid_lead';
INSERT IGNORE INTO tmp_existing_new_codes (code) VALUES ('bid-TeamLeader');

-- 3. sales → bid-projectLeader
UPDATE roles SET code = 'bid-projectLeader', updated_at = NOW()
WHERE code = 'sales'
  AND NOT EXISTS (SELECT 1 FROM tmp_existing_new_codes WHERE code = 'bid-projectLeader');
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-projectLeader' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'sales');
DELETE FROM roles WHERE code = 'sales';
INSERT IGNORE INTO tmp_existing_new_codes (code) VALUES ('bid-projectLeader');

-- 4. bid_specialist → bid-Team
UPDATE roles SET code = 'bid-Team', updated_at = NOW()
WHERE code = 'bid_specialist'
  AND NOT EXISTS (SELECT 1 FROM tmp_existing_new_codes WHERE code = 'bid-Team');
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-Team' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_specialist');
DELETE FROM roles WHERE code = 'bid_specialist';
INSERT IGNORE INTO tmp_existing_new_codes (code) VALUES ('bid-Team');

-- 5. admin_staff → bid-administration
UPDATE roles SET code = 'bid-administration', updated_at = NOW()
WHERE code = 'admin_staff'
  AND NOT EXISTS (SELECT 1 FROM tmp_existing_new_codes WHERE code = 'bid-administration');
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-administration' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'admin_staff');
DELETE FROM roles WHERE code = 'admin_staff';
INSERT IGNORE INTO tmp_existing_new_codes (code) VALUES ('bid-administration');

-- 6. bid_other_dept → bid-otherDept
UPDATE roles SET code = 'bid-otherDept', updated_at = NOW()
WHERE code = 'bid_other_dept'
  AND NOT EXISTS (SELECT 1 FROM tmp_existing_new_codes WHERE code = 'bid-otherDept');
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid-otherDept' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid_other_dept');
DELETE FROM roles WHERE code = 'bid_other_dept';
INSERT IGNORE INTO tmp_existing_new_codes (code) VALUES ('bid-otherDept');

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS tmp_existing_new_codes;

COMMIT;
