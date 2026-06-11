-- PR: 当前分支
-- 回滚：删除补充的角色（仅当无关联用户时）

DELETE FROM roles WHERE code IN ('auditor', 'bid_admin', 'bid_lead', 'sales', 'task_executor', 'bid_specialist', 'admin_staff')
  AND code NOT IN (
      SELECT r.code FROM users u JOIN roles r ON u.role_id = r.id
      WHERE r.code IN ('auditor', 'bid_admin', 'bid_lead', 'sales', 'task_executor', 'bid_specialist', 'admin_staff')
  );
