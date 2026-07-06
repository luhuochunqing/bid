-- Sentry XIYU-P: tasks.extended_fields_json 存在空字符串 ''，
-- MySQL JSON_EXTRACT('', '$.x') 会抛 "Invalid JSON text: The document is empty"。
-- 将历史空字符串统一规范化为 NULL，避免保证金列表等查询 500。
UPDATE tasks SET extended_fields_json = NULL WHERE extended_fields_json = '';
