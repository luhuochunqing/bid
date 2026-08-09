// Input: 当前操作者角色 code、当前用户 ID、文档上传者 ID
// Output: AuthorizationDecision — 是否允许对项目文档执行上传/删除操作
// Pos: projectworkflow/core/ - pure core policy, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.core;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.entity.RoleProfileCatalog;



/**
 * 项目文档工作流授权策略。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。判断当前角色是否允许对项目文档执行上传、删除等操作。</p>
 *
 * <p><b>CO-481 治理：查看/下载权限已统一收口到 {@code ProjectAccessScopeService.assertCurrentUserCanAccessProject}。</b>
 * 该服务聚合 9 条权限路径（含任务执行人 {@code taskRepository.findDistinctProjectIdsByAssigneeId}），
 * 投标专员（bid-Team）作为任务指派人即可访问项目文档，无需在 Policy 层二次校验角色白名单。
 * CO-474 曾误用本类的 canView/canDownload 方法重新引入第二层闸门，导致 10208 投标专员
 * 被通知审核标书却 403 看不到文档。死代码已删除以防复发。</p>
 */
public final class ProjectDocumentWorkflowPolicy {

    private ProjectDocumentWorkflowPolicy() {
    }

    /**
     * 校验指定角色是否有权上传项目文档。
     * <p>所有能访问项目的角色都能上传：admin / bidAdmin / bid-SystemAdmin / bid-TeamLeader / bid-projectLeader / bid-Team / bid-otherDept。
     * bid-administration 及其他未知角色拒绝。</p>
     *
     * <p>CO-XXX：补上 bid-SystemAdmin。其权限基线等同 /bidAdmin（GLOBAL_ACCESS_ROLES / data_scope=all），
     * 但此前上传白名单遗漏，导致 OSS 投标系统管理员（如 06234）上传招标/投标文件被误拒为"权限不足"。</p>
     *
     * @param roleCode 当前操作者角色 code（可为 null）
     * @return 授权决策结果
     */
    public static AuthorizationDecision canUploadProjectDocument(String roleCode) {
        if (roleCode == null) {
            return AuthorizationDecision.deny("当前用户未分配角色，无权上传项目文档");
        }
        String normalized = roleCode.trim();
        if (normalized.isBlank()) {
            return AuthorizationDecision.deny("权限不足，无权上传项目文档");
        }
        if (RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.BID_ADMIN_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.BID_LEAD_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.BID_OTHER_DEPT_CODE.equalsIgnoreCase(normalized)) {
            return AuthorizationDecision.permit();
        }
        return AuthorizationDecision.deny("权限不足，无权上传项目文档");
    }

    /**
     * 校验指定角色是否有权删除项目文档。
     * <p>允许删除的主体：</p>
     * <ul>
     *   <li>系统管理员（admin）/ 投标部门管理员（bidAdmin）/ 投标系统管理员（bid-SystemAdmin）/ 投标组长（bid-TeamLeader）— 对齐蓝图 §3.3.1.2</li>
     *   <li>上传者本人（uploaderId == currentUserId）— CO-383：未提交前可删除自己上传的文件</li>
     * </ul>
     *
     * <p>CO-XXX：补上 bid-SystemAdmin。其权限基线等同 /bidAdmin（GLOBAL_ACCESS_ROLES / data_scope=all），
     * 与上传白名单一致，避免 OSS 投标系统管理员（如 06234）删除文档被误拒。</p>
     *
     * @param roleCode      当前操作者角色 code（可为 null）
     * @param currentUserId 当前用户 ID（可为 null）
     * @param uploaderId    文档上传者 ID（可为 null，历史数据）
     * @return 授权决策结果
     */
    public static AuthorizationDecision canDeleteProjectDocument(String roleCode, Long currentUserId, Long uploaderId) {
        if (roleCode == null) {
            return AuthorizationDecision.deny("当前用户未分配角色，无权删除文档");
        }
        String normalized = roleCode.trim();
        // CO-382: 对齐蓝图 §3.3.1.2「删除文档」权限矩阵——投标管理员/组长列
        if (RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.BID_ADMIN_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE.equalsIgnoreCase(normalized)
                || RoleProfileCatalog.BID_LEAD_CODE.equalsIgnoreCase(normalized)) {
            return AuthorizationDecision.permit();
        }
        // CO-383: 上传者本人在未提交前可删除自己上传的文件（可能传错需要重传）
        if (currentUserId != null && currentUserId.equals(uploaderId)) {
            return AuthorizationDecision.permit();
        }
        return AuthorizationDecision.deny("权限不足，仅投标管理员/组长或上传者本人允许删除文档");
    }

}
