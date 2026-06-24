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

-- 0. /bidAdmin → bidAdmin（回滚到 V1074 历史状态）
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bidAdmin' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = '/bidAdmin')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bidAdmin');
DELETE FROM roles WHERE code = '/bidAdmin'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bidAdmin');
UPDATE roles SET code = 'bidAdmin', updated_at = NOW() WHERE code = '/bidAdmin';

-- 1. /bidAdmin → bid_admin（回滚到 V1092 之前的状态）
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid_admin' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = '/bidAdmin')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid_admin');
DELETE FROM roles WHERE code = '/bidAdmin'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid_admin');
UPDATE roles SET code = 'bid_admin', updated_at = NOW() WHERE code = '/bidAdmin';

-- 2. bid-TeamLeader → bid_lead
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid_lead' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-TeamLeader')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid_lead');
DELETE FROM roles WHERE code = 'bid-TeamLeader'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid_lead');
UPDATE roles SET code = 'bid_lead', updated_at = NOW() WHERE code = 'bid-TeamLeader';

-- 3. bid-projectLeader → sales
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'sales' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-projectLeader')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'sales');
DELETE FROM roles WHERE code = 'bid-projectLeader'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'sales');
UPDATE roles SET code = 'sales', updated_at = NOW() WHERE code = 'bid-projectLeader';

-- 4. bid-Team → bid_specialist
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid_specialist' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-Team')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid_specialist');
DELETE FROM roles WHERE code = 'bid-Team'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid_specialist');
UPDATE roles SET code = 'bid_specialist', updated_at = NOW() WHERE code = 'bid-Team';

-- 5. bid-administration → admin_staff
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'admin_staff' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-administration')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'admin_staff');
DELETE FROM roles WHERE code = 'bid-administration'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'admin_staff');
UPDATE roles SET code = 'admin_staff', updated_at = NOW() WHERE code = 'bid-administration';

-- 6. bid-otherDept → bid_other_dept
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'bid_other_dept' LIMIT 1)
WHERE role_id IN (SELECT id FROM roles WHERE code = 'bid-otherDept')
  AND role_id NOT IN (SELECT id FROM roles WHERE code = 'bid_other_dept');
DELETE FROM roles WHERE code = 'bid-otherDept'
  AND EXISTS (SELECT 1 FROM roles WHERE code = 'bid_other_dept');
UPDATE roles SET code = 'bid_other_dept', updated_at = NOW() WHERE code = 'bid-otherDept';
