-- V1181: 清理 audit_logs.project_id 污染数据
-- 背景：CO-324 引入 audit_logs.project_id 后，AuditableAspect 原实现对所有
-- 第一参为 Long 的 @Auditable 方法都提取 projectId，导致非项目实体（Performance/Template/
-- Tender 等）的 id 被错写为 project_id，污染项目动态。
--
-- 修复：AuditableAspect 改为 projectScoped 注解驱动 + 只通过 getProjectId() 反射提取，
-- 移除 Long args-first 和 getId() fallback 两条 bug 路径。
--
-- 本迁移收窄清理范围：只清理"原本就是 bug 的记录"（project_id = entity_id 且 entityType 非项目核心）。
-- 保留条件：
--   1. 项目核心 entityType 的全部记录（Project/ProjectLeadAssignment/ProjectEvaluation 等）
--   2. Fee 实体的全部记录（FeeService 原本通过 FeeCreateRequest.getProjectId() 正确写入了 project_id）
--   3. project_id != entity_id 的记录（说明是正确提取的，不是 Long args-first bug）
--
-- 清理条件（同时满足）：
--   1. entityType NOT IN 项目核心白名单 + Fee
--   2. project_id = entity_id（Long args-first bug 的特征：实体 id 被错写为 project_id）
--
-- 回滚脚本：U1181__cleanup_audit_logs_project_id.sql（无法恢复原值，仅占位）

UPDATE audit_logs
SET project_id = NULL
WHERE entity_type NOT IN (
    'Project',
    'ProjectLeadAssignment',
    'ProjectEvaluation',
    'ProjectInitiationDetails',
    'ProjectResult',
    'ProjectRetrospective',
    'ProjectClosure',
    'BidDocumentReview',
    'Fee'
)
AND project_id IS NOT NULL
AND entity_id REGEXP '^[0-9]+$'
AND project_id = CAST(entity_id AS UNSIGNED);
