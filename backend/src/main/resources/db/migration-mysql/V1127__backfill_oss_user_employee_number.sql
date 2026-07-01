-- V1127: 回填 OSS 同步用户的 employee_number 字段
--
-- 根因（CO-441）：OrganizationUserSyncWriter 同步 OSS 用户时，将工号写入 username 字段，
-- 但从未填充 employee_number 字段。导致 TenderAutoAssignmentService.resolveManagerNameByEmployeeNumber
-- 按 employee_number 查询时永远返回 null，CRM 自动分配失败，标讯状态被推进到 TRACKING
-- 但 project_manager_id/project_manager_name 为 NULL（列表显示"待分配"但状态已是 TRACKING）。
--
-- 修复策略（方案 C: A + B 双修）：
--   A. TenderAutoAssignmentService 增加 fallback 到 username 查询（止血）
--   B. OrganizationUserSyncWriter 同步时填充 employee_number = username（根治）
--   本迁移：回填历史 OSS 用户的 employee_number 字段
--
-- 回填条件：
--   - external_org_source_app 非空（OSS 同步用户）
--   - employee_number 为空（未填充）
--   - username 非空（有工号可回填）
--
-- 幂等性：仅更新 employee_number 为 NULL 的行，重复执行无副作用。
-- 回滚：U1127 将 OSS 用户的 employee_number 置回 NULL（不推荐，会重新触发 bug）。

UPDATE users
SET employee_number = username
WHERE external_org_source_app IS NOT NULL
  AND external_org_source_app <> ''
  AND employee_number IS NULL
  AND username IS NOT NULL
  AND username <> '';
