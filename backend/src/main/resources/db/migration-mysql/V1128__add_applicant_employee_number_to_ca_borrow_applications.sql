-- V1128: CA 借用申请表新增 applicant_employee_number 列 + 回填历史数据
--
-- 根因（CO-465）：CaBorrowService#borrow 历史把 user.getUsername()（登录账号）存进
-- applicant_name，前端"我的审批-申请人"列表显示登录账号而非中文姓名，更没有工号。
--
-- 修复策略：
--   A. 代码层（CaBorrowService）：
--      - applicant_name 改存 user.getFullName()
--      - 新增 applicant_employee_number 存 user.getDisplayEmployeeNumber()
--      （employee_number 为空时回退到 username，与统一人员格式化器契约一致）
--   B. 数据层（本迁移）：
--      1) 增加 applicant_employee_number 列
--      2) 回填存量数据：
--         - applicant_employee_number = 关联 users.employee_number（兜底 username）
--         - applicant_name = 关联 users.full_name（替换错误的 username）
--
-- 幂等性：
--   - ADD COLUMN IF NOT EXISTS 在 MySQL 8.0 不支持，使用信息_SCHEMA检查后追加；
--     简化做法：直接 ADD COLUMN，重复执行会报错（Flyway 已防重复执行）。
--   - UPDATE 使用 LEFT JOIN users 按 applicant_id 关联，重复执行结果相同。
--
-- 回滚：U1128 删除新增列（applicant_name 不回退到 username，因为旧值本来就是错误数据）。

-- Step 1: 新增列
ALTER TABLE ca_borrow_applications
    ADD COLUMN applicant_employee_number VARCHAR(100) NULL AFTER applicant_name;

-- Step 2: 回填 applicant_employee_number（按 applicant_id 关联 users）
-- 用户 employee_number 为空时回退到 username（与 User#getDisplayEmployeeNumber 契约一致）
UPDATE ca_borrow_applications a
JOIN users u ON a.applicant_id = u.id
SET a.applicant_employee_number = COALESCE(NULLIF(u.employee_number, ''), u.username)
WHERE a.applicant_employee_number IS NULL;

-- Step 3: 回填 applicant_name（从 username 改为 full_name）
-- 仅当 applicant_name 当前值等于 users.username（即原错误值）时才更新
UPDATE ca_borrow_applications a
JOIN users u ON a.applicant_id = u.id
SET a.applicant_name = u.full_name
WHERE a.applicant_name = u.username
  AND u.full_name IS NOT NULL
  AND u.full_name <> '';
