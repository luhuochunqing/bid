// Input: 当前操作者角色 code、当前用户 ID、项目主负责人 ID、项目副负责人 ID
// Output: boolean / AuthorizationDecision — 是否允许对项目文档执行查看/下载/上传/删除操作
// Pos: projectworkflow/core/ - pure core policy, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.core;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.entity.RoleProfileCatalog;

/**
 * 项目文档工作流授权策略。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。判断当前角色是否允许对项目文档执行查看、下载、上传、删除等操作。</p>
 */
public final class ProjectDocumentWorkflowPolicy {

    private ProjectDocumentWorkflowPolicy() {
    }

    /**
     * 校验指定角色是否有权查看项目文档列表。
     * <ul>
     *   <li>admin / bidAdmin / bid-TeamLeader：直接放行</li>
     *   <li>bid-projectLeader：需匹配 primaryLeadId</li>
     *   <li>bid-Team：需匹配 primaryLeadId 或 secondaryLeadId</li>
     *   <li>其他角色：拒绝</li>
     * </ul>
     *
     * @param roleCode       当前操作者角色 code（可为 null）
     * @param currentUserId  当前用户 ID
     * @param primaryLeadId  项目主负责人 ID
     * @param secondaryLeadId 项目副负责人 ID
     * @return 授权决策结果
     */
    public static AuthorizationDecision canViewProjectDocuments(String roleCode, Long currentUserId,
                                                                Long primaryLeadId, Long secondaryLeadId) {
        if (roleCode == null) {
            return AuthorizationDecision.deny("当前用户未分配角色，无权查看项目文档");
        }
        String normalized = roleCode.trim();
        if (isGlobalDocumentAccessRole(normalized)) {
            return AuthorizationDecision.permit();
        }
        if (RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(normalized)) {
            if (currentUserId != null && currentUserId.equals(primaryLeadId)) {
                return AuthorizationDecision.permit();
            }
            return AuthorizationDecision.deny("权限不足，仅项目主负责人可查看项目文档");
        }
        if (RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(normalized)) {
            if (currentUserId != null
                    && (currentUserId.equals(primaryLeadId) || currentUserId.equals(secondaryLeadId))) {
                return AuthorizationDecision.permit();
            }
            return AuthorizationDecision.deny("权限不足，仅项目负责人可查看项目文档");
        }
        return AuthorizationDecision.deny("权限不足，无权查看项目文档");
    }

    /**
     * 校验指定角色是否有权下载项目文档。
     * <p>权限规则与 {@link #canViewProjectDocuments(String, Long, Long, Long)} 完全一致。</p>
     *
     * @param roleCode        当前操作者角色 code（可为 null）
     * @param currentUserId   当前用户 ID
     * @param primaryLeadId   项目主负责人 ID
     * @param secondaryLeadId 项目副负责人 ID
     * @return 授权决策结果
     */
    public static AuthorizationDecision canDownloadProjectDocument(String roleCode, Long currentUserId,
                                                                   Long primaryLeadId, Long secondaryLeadId) {
        AuthorizationDecision viewDecision = canViewProjectDocuments(roleCode, currentUserId, primaryLeadId, secondaryLeadId);
        if (viewDecision.allowed()) {
            return AuthorizationDecision.permit();
        }
        return AuthorizationDecision.deny("权限不足，无权下载项目文档");
    }

    /**
     * 校验指定角色是否有权上传项目文档。
     * <p>所有能访问项目的角色都能上传：admin / bidAdmin / bid-TeamLeader / bid-projectLeader / bid-Team / bid-otherDept。
     * bid-administration 及其他未知角色拒绝。</p>
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
     * <p>系统管理员（admin）、投标部门管理员（bidAdmin）和投标组长（bid-TeamLeader）允许删除。</p>
     *
     * @param roleCode 当前操作者角色 code（可为 null）
     * @return 授权决策结果
     */
    public static AuthorizationDecision canDeleteProjectDocument(String roleCode) {
        if (roleCode == null) {
            return AuthorizationDecision.deny("当前用户未分配角色，无权删除文档");
        }
        String normalized = roleCode.trim();
        if (isGlobalDocumentAccessRole(normalized)) {
            return AuthorizationDecision.permit();
        }
        return AuthorizationDecision.deny("权限不足，仅管理员允许删除文档");
    }

    private static boolean isGlobalDocumentAccessRole(String roleCode) {
        for (String globalRole : RoleProfileCatalog.GLOBAL_ACCESS_ROLES) {
            if (globalRole.equalsIgnoreCase(roleCode)) {
                return true;
            }
        }
        return false;
    }
}
