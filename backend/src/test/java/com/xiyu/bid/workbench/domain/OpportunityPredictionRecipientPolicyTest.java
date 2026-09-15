// Input: OSS 实时岗位/角色回查单条响应
// Output: 通知可接收状态与目标角色命中判定
// Pos: workbench/domain - 商机预测接收人纯规则测试
package com.xiyu.bid.workbench.domain;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpportunityPredictionRecipientPolicy - OSS 实时接收人规则")
class OpportunityPredictionRecipientPolicyTest {

    private static final Set<String> TARGET_ROLES = Set.of(
            RoleProfileCatalog.BID_ADMIN_CODE,
            RoleProfileCatalog.BID_LEAD_CODE);

    @Test
    @DisplayName("status=1 是最高优先级，即使 employeeStatus 是历史离职值也可接收")
    void isActive_statusOneWins() {
        OssUserJobAndRoleDto live = lookup("1", "8", List.of());

        assertThat(OpportunityPredictionRecipientPolicy.isActive(live)).isTrue();
    }

    @Test
    @DisplayName("status=0 是离职，即使 employeeStatus 是在职值也不可接收")
    void isActive_statusZeroWins() {
        OssUserJobAndRoleDto departed = lookup("0", "3", List.of());

        assertThat(OpportunityPredictionRecipientPolicy.isActive(departed)).isFalse();
    }

    @Test
    @DisplayName("缺少明确实时状态时失败关闭")
    void isActive_missingStatusFailsClosed() {
        OssUserJobAndRoleDto unknown = lookup("", "", List.of());

        assertThat(OpportunityPredictionRecipientPolicy.isActive(unknown)).isFalse();
    }

    @Test
    @DisplayName("规范角色码和大小写变体均命中任一目标角色")
    void hasAnyTargetRole_matchesCanonicalCodesCaseInsensitively() {
        OssUserJobAndRoleDto admin = lookup("1", "3", List.of("/BIDADMIN", "bid-teamleader"));

        assertThat(OpportunityPredictionRecipientPolicy.hasAnyTargetRole(admin, TARGET_ROLES)).isTrue();
    }

    @Test
    @DisplayName("中文角色名可命中，其他系统角色不能命中")
    void hasAnyTargetRole_mapsNamesAndRejectsOtherSystems() {
        OssUserJobAndRoleDto leader = lookup("1", "3", List.of("投标组长", "CRM_ADMIN"));
        OssUserJobAndRoleDto unrelated = lookup("1", "3", List.of("管理员", "CRM_ADMIN"));

        assertThat(OpportunityPredictionRecipientPolicy.hasAnyTargetRole(leader, TARGET_ROLES)).isTrue();
        assertThat(OpportunityPredictionRecipientPolicy.hasAnyTargetRole(unrelated, TARGET_ROLES)).isFalse();
    }

    private static OssUserJobAndRoleDto lookup(String status, String employeeStatus, List<String> roles) {
        return new OssUserJobAndRoleDto("08402", "项目经理", roles,
                employeeStatus, status, "测试用户");
    }
}
