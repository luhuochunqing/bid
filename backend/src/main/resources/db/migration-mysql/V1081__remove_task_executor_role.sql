-- CO-XXX: 删除 task_executor（任务执行人）角色
-- 该角色功能已由 bid_specialist 覆盖，不再需要独立角色
-- 兼容历史 schema：仅在 role_menu_permissions 表存在时清理权限关联
SET @rm_table := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'role_menu_permissions'
);

SET @rm_sql := IF(@rm_table = 1, 'DELETE FROM role_menu_permissions WHERE role_code = ''task_executor''', 'SELECT 1');
PREPARE stmt FROM @rm_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE FROM roles WHERE code = 'task_executor';
