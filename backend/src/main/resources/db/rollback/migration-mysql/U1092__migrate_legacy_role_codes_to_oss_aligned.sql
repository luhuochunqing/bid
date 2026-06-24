-- Input: V1092__migrate_legacy_role_codes_to_oss_aligned.sql
-- Data rollback required: 回滚 V1092，将新角色码恢复为旧角色码。
--  - /bidAdmin → bid_admin          （注意：OSS 投标管理员带前导斜杠）
--  - bid-TeamLeader → bid_lead
--  - bid-projectLeader → sales
--  - bid-Team → bid_specialist
--  - bid-administration → admin_staff
--  - bid-otherDept → bid_other_dept
--  - admin 保持不变
-- 注意：回滚是幂等的，新 code 不存在时 no-op。
--
-- 重要：直接回滚到 V1092 之前的状态，跳过 V1074 的中间态（bidAdmin camelCase）。
--   原始 rollback 分两步（/bidAdmin → bidAdmin → bid_admin）会导致互相吞掉：
--   步骤 0 把 /bidAdmin 改成 bidAdmin 后，步骤 1 的 WHERE code='/bidAdmin' 永远不命中。
--   现在合并为单步：/bidAdmin → bid_admin，避免中间态问题。
--
-- MySQL 兼容性：使用临时表 tmp_existing_old_codes 缓存"旧 code 是否已存在"的判断结果，
--   避免 DELETE FROM roles ... WHERE EXISTS (SELECT 1 FROM roles ...) 触发
--   ERROR 1093 (HY000): You can't specify target table 'roles' for update in FROM clause。
--
-- 事务：使用显式事务保证原子性，中途失败回滚避免半回滚状态

START TRANSACTION;

-- 创建临时表存放"已存在的旧 code"，避免 DELETE/UPDATE roles 时 self-reference
DROP TEMPORARY TABLE IF EXISTS tmp_existing_old_codes;
CREATE TEMPORARY TABLE tmp_existing_old_codes (
  code VARCHAR(100) PRIMARY KEY
);
INSERT IGNORE INTO tmp_existing_old_codes (code)
SELECT code FROM roles
WHERE code IN ('bid_admin', 'bid_lead', 'sales', 'bid_specialist', 'admin_staff', 'bid_other_dept');

-- 1. /bidAdmin → bid_admin
-- 1a. 若 bid_admin 已存在（新旧两条记录），迁移 users 并删除新角色
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid_admin' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = '/bidAdmin')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid_admin');
DELETE FROM roles WHERE code = '/bidAdmin'
  AND EXISTS (SELECT 1 FROM tmp_existing_old_codes WHERE code = 'bid_admin');
-- 1b. 若 bid_admin 不存在，直接更新 roles.code
UPDATE roles SET code = 'bid_admin', updated_at = NOW() WHERE code = '/bidAdmin';
-- 刷新临时表：bid_admin 现在已存在
INSERT IGNORE INTO tmp_existing_old_codes (code) VALUES ('bid_admin');

-- 2. bid-TeamLeader → bid_lead
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid_lead' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-TeamLeader')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid_lead');
DELETE FROM roles WHERE code = 'bid-TeamLeader'
  AND EXISTS (SELECT 1 FROM tmp_existing_old_codes WHERE code = 'bid_lead');
UPDATE roles SET code = 'bid_lead', updated_at = NOW() WHERE code = 'bid-TeamLeader';
INSERT IGNORE INTO tmp_existing_old_codes (code) VALUES ('bid_lead');

-- 3. bid-projectLeader → sales
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'sales' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-projectLeader')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'sales');
DELETE FROM roles WHERE code = 'bid-projectLeader'
  AND EXISTS (SELECT 1 FROM tmp_existing_old_codes WHERE code = 'sales');
UPDATE roles SET code = 'sales', updated_at = NOW() WHERE code = 'bid-projectLeader';
INSERT IGNORE INTO tmp_existing_old_codes (code) VALUES ('sales');

-- 4. bid-Team → bid_specialist
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid_specialist' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-Team')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid_specialist');
DELETE FROM roles WHERE code = 'bid-Team'
  AND EXISTS (SELECT 1 FROM tmp_existing_old_codes WHERE code = 'bid_specialist');
UPDATE roles SET code = 'bid_specialist', updated_at = NOW() WHERE code = 'bid-Team';
INSERT IGNORE INTO tmp_existing_old_codes (code) VALUES ('bid_specialist');

-- 5. bid-administration → admin_staff
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'admin_staff' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-administration')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'admin_staff');
DELETE FROM roles WHERE code = 'bid-administration'
  AND EXISTS (SELECT 1 FROM tmp_existing_old_codes WHERE code = 'admin_staff');
UPDATE roles SET code = 'admin_staff', updated_at = NOW() WHERE code = 'bid-administration';
INSERT IGNORE INTO tmp_existing_old_codes (code) VALUES ('admin_staff');

-- 6. bid-otherDept → bid_other_dept
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid_other_dept' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-otherDept')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid_other_dept');
DELETE FROM roles WHERE code = 'bid-otherDept'
  AND EXISTS (SELECT 1 FROM tmp_existing_old_codes WHERE code = 'bid_other_dept');
UPDATE roles SET code = 'bid_other_dept', updated_at = NOW() WHERE code = 'bid-otherDept';
INSERT IGNORE INTO tmp_existing_old_codes (code) VALUES ('bid_other_dept');

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS tmp_existing_old_codes;

COMMIT;
