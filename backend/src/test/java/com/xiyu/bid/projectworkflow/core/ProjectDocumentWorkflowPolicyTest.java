// Input: 角色 code、用户 ID、项目负责人 ID
// Output: 验证 ProjectDocumentWorkflowPolicy 各授权决策
// Pos: Test/核心策略测试
package com.xiyu.bid.projectworkflow.core;

import com.xiyu.bid.entity.RoleProfileCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目文档工作流授权策略单元测试。
 * <p>覆盖查看、下载、上传、删除四类操作的 permit/deny 路径。</p>
 */
class ProjectDocumentWorkflowPolicyTest {

    private static final Long CURRENT_USER_ID = 100L;
    private static final Long PRIMARY_LEAD_ID = 100L;
    private static final Long SECONDARY_LEAD_ID = 200L;
    private static final Long OTHER_USER_ID = 999L;

    // ==================== canViewProjectDocuments ====================

    @ParameterizedTest
    @ValueSource(strings = {RoleProfileCatalog.ADMIN_CODE, RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE})
    void canViewProjectDocuments_whenAdminOrBidAdminOrTeamLeader_shouldPermit(String roleCode) {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                roleCode, CURRENT_USER_ID, OTHER_USER_ID, OTHER_USER_ID);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canViewProjectDocuments_whenProjectLeaderMatchingPrimary_shouldPermit() {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                RoleProfileCatalog.SALES_CODE, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canViewProjectDocuments_whenProjectLeaderNotMatching_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                RoleProfileCatalog.SALES_CODE, CURRENT_USER_ID, OTHER_USER_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canViewProjectDocuments_whenTeamMatchingPrimary_shouldPermit() {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                RoleProfileCatalog.BID_SPECIALIST_CODE, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canViewProjectDocuments_whenTeamMatchingSecondary_shouldPermit() {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                RoleProfileCatalog.BID_SPECIALIST_CODE, SECONDARY_LEAD_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canViewProjectDocuments_whenTeamNotMatching_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                RoleProfileCatalog.BID_SPECIALIST_CODE, OTHER_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canViewProjectDocuments_whenOtherDept_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                RoleProfileCatalog.BID_OTHER_DEPT_CODE, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canViewProjectDocuments_whenAdministration_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                RoleProfileCatalog.ADMIN_STAFF_CODE, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canViewProjectDocuments_whenNullRole_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                null, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("未分配角色");
    }

    // ==================== canDownloadProjectDocument ====================

    @ParameterizedTest
    @ValueSource(strings = {RoleProfileCatalog.ADMIN_CODE, RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE})
    void canDownloadProjectDocument_whenAdminOrBidAdminOrTeamLeader_shouldPermit(String roleCode) {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                roleCode, CURRENT_USER_ID, OTHER_USER_ID, OTHER_USER_ID);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canDownloadProjectDocument_whenProjectLeaderMatchingPrimary_shouldPermit() {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                RoleProfileCatalog.SALES_CODE, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canDownloadProjectDocument_whenProjectLeaderNotMatching_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                RoleProfileCatalog.SALES_CODE, CURRENT_USER_ID, OTHER_USER_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canDownloadProjectDocument_whenTeamMatchingPrimary_shouldPermit() {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                RoleProfileCatalog.BID_SPECIALIST_CODE, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canDownloadProjectDocument_whenTeamMatchingSecondary_shouldPermit() {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                RoleProfileCatalog.BID_SPECIALIST_CODE, SECONDARY_LEAD_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canDownloadProjectDocument_whenTeamNotMatching_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                RoleProfileCatalog.BID_SPECIALIST_CODE, OTHER_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canDownloadProjectDocument_whenOtherDept_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                RoleProfileCatalog.BID_OTHER_DEPT_CODE, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canDownloadProjectDocument_whenAdministration_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                RoleProfileCatalog.ADMIN_STAFF_CODE, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canDownloadProjectDocument_whenNullRole_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                null, CURRENT_USER_ID, PRIMARY_LEAD_ID, SECONDARY_LEAD_ID);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("未分配角色");
    }

    // ==================== canUploadProjectDocument ====================

    @ParameterizedTest
    @ValueSource(strings = {
            RoleProfileCatalog.ADMIN_CODE,
            RoleProfileCatalog.BID_ADMIN_CODE,
            RoleProfileCatalog.BID_LEAD_CODE,
            RoleProfileCatalog.SALES_CODE,
            RoleProfileCatalog.BID_SPECIALIST_CODE,
            RoleProfileCatalog.BID_OTHER_DEPT_CODE
    })
    void canUploadProjectDocument_whenProjectAccessibleRole_shouldPermit(String roleCode) {
        var result = ProjectDocumentWorkflowPolicy.canUploadProjectDocument(roleCode);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void canUploadProjectDocument_whenAdministration_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canUploadProjectDocument(RoleProfileCatalog.ADMIN_STAFF_CODE);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void canUploadProjectDocument_whenNullRole_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canUploadProjectDocument(null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("未分配角色");
    }

    @Test
    void canUploadProjectDocument_whenBlankRole_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canUploadProjectDocument("   ");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotNull();
    }

    // ==================== canDeleteProjectDocument ====================

    @ParameterizedTest
    @ValueSource(strings = {
            RoleProfileCatalog.ADMIN_CODE,
            RoleProfileCatalog.BID_ADMIN_CODE,
            RoleProfileCatalog.BID_LEAD_CODE
    })
    void canDeleteProjectDocument_whenAdminBidAdminOrBidLead_shouldPermit(String roleCode) {
        // CO-382: 对齐蓝图 §3.3.1.2「删除文档」权限矩阵——admin/bidAdmin/bid-TeamLeader 允许
        var result = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument(roleCode);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            RoleProfileCatalog.SALES_CODE,
            RoleProfileCatalog.BID_SPECIALIST_CODE,
            RoleProfileCatalog.BID_OTHER_DEPT_CODE,
            RoleProfileCatalog.ADMIN_STAFF_CODE,
            "unknown"
    })
    void canDeleteProjectDocument_whenNonAdminRole_shouldDeny(String roleCode) {
        var result = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument(roleCode);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("仅投标管理员/组长允许删除文档");
    }

    @Test
    void canDeleteProjectDocument_whenNullRole_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument(null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("未分配角色");
    }

    @Test
    void canDeleteProjectDocument_whenBlankRole_shouldDeny() {
        var result = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument("   ");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("仅投标管理员/组长允许删除文档");
    }
}
