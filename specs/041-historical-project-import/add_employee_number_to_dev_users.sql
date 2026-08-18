-- 给 dev 测试用户添加 employee_number，便于真实姓名测试
-- 执行前请确认：这是修改数据库的 DML 操作，仅在本地 dev 环境执行
-- 生产环境严禁执行
--
-- 执行命令：
--   mysql -h 127.0.0.1 -P 3306 -u root -p'XiyuRoot!2026' xiyu_bid_main < specs/041-historical-project-import/add_employee_number_to_dev_users.sql
--
-- 执行后可用的测试工号：
--   EMP001 → 李总（id=122, lizong, admin）
--   EMP002 → 小王（id=123, xiaowang, 投标专员）
--   EMP003 → 陈投标管理（id=124, xiaochen, 投标管理员）
--   EMP004 → 刘投标组长（id=125, xiaoliu, 投标组长）
--   EMP005 → 张销售（id=126, xiaozhang, 投标项目负责人）
--   EMP006 → 周投标专员（id=127, xiaozhou, 投标专员）
--   EMP007 → 郑行政（id=128, xiaozheng, 行政人员）
--   EMP101 → 陈投标管理（id=5, bid_admin, 投标管理员 dev）
--   EMP102 → 刘投标组长（id=6, bid_lead, 投标组长 dev）
--   EMP103 → 张销售（id=7, sales, 投标项目负责人 dev）
--   EMP104 → 周投标专员（id=9, bid_specialist, 投标专员 dev）
--   EMP105 → 郑行政（id=10, admin_staff, 行政人员 dev）

-- e2e 用户
UPDATE users SET employee_number = 'EMP001' WHERE id = 122 AND username = 'lizong' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP002' WHERE id = 123 AND username = 'xiaowang' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP003' WHERE id = 124 AND username = 'xiaochen' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP004' WHERE id = 125 AND username = 'xiaoliu' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP005' WHERE id = 126 AND username = 'xiaozhang' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP006' WHERE id = 127 AND username = 'xiaozhou' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP007' WHERE id = 128 AND username = 'xiaozheng' AND employee_number IS NULL;

-- dev 用户
UPDATE users SET employee_number = 'EMP101' WHERE id = 5 AND username = 'bid_admin' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP102' WHERE id = 6 AND username = 'bid_lead' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP103' WHERE id = 7 AND username = 'sales' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP104' WHERE id = 9 AND username = 'bid_specialist' AND employee_number IS NULL;
UPDATE users SET employee_number = 'EMP105' WHERE id = 10 AND username = 'admin_staff' AND employee_number IS NULL;

-- 验证
SELECT id, full_name, username, employee_number FROM users
WHERE employee_number LIKE 'EMP%'
ORDER BY id;
