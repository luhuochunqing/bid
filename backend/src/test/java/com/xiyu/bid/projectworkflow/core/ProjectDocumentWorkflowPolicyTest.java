// Input: 角色 code
// Output: 验证 ProjectDocumentWorkflowPolicy.canDeleteProjectDocument 授权决策
// Pos: Test/核心策略测试
package com.xiyu.bid.projectworkflow.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目文档工作流授权策略单元测试。
 * <p>覆盖 {@link ProjectDocumentWorkflowPolicy#canDeleteProjectDocument(String)} 的 permit/deny 路径。</p>
 */
class ProjectDocumentWorkflowPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"admin", "/bidAdmin", "ADMIN", "/BidAdmin"})
    void canDeleteProjectDocument_whenAdminOrBidAdmin_shouldPermit(String roleCode) {
        var result = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument(roleCode);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bid-TeamLeader", "bid-projectLeader", "bid-Team", "bid-administration", "bid-otherDept", "unknown"})
    void canDeleteProjectDocument_whenNonAdminRole_shouldDeny(String roleCode) {
        var result = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument(roleCode);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("仅管理员允许删除文档");
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
        assertThat(result.reason()).contains("仅管理员允许删除文档");
    }
}
