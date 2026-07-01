-- U1127: 回滚 V1127 — 将 OSS 用户的 employee_number 置回 NULL
-- 注意：回滚会重新触发 CO-441 bug（CRM 自动分配按 employee_number 查询返回 null），
-- 仅在 V1127 导致严重问题时回滚，否则建议正向修复 OrganizationUserSyncWriter 代码。
--
-- Input: V1127__backfill_oss_user_employee_number.sql
UPDATE users
SET employee_number = NULL
WHERE external_org_source_app IS NOT NULL
  AND external_org_source_app <> ''
  AND employee_number = username;
