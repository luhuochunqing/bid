package com.xiyu.bid.user.core;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssignmentCandidatePolicy 过滤逻辑单元测试（TDD Red 阶段）。
 *
 * <p>被测类 {@link AssignmentCandidatePolicy} 尚未实现，本测试编译失败属于预期行为。
 */
@DisplayName("AssignmentCandidatePolicy 过滤逻辑")
class AssignmentCandidatePolicyTest {

    private final AssignmentCandidatePolicy policy = new AssignmentCandidatePolicy();

    @Test
    @DisplayName("hasGlobalAccess=true 时返回所有候选人（不过滤部门）")
    void filter_GlobalAccess_ReturnsAllCandidates() {
        List<User> candidates = List.of(
                user(1L, "张三", "D1", "一部", "bid_admin", "投标管理员"),
                user(2L, "李四", "D2", "二部", "sales", "销售"),
                user(3L, "王五", "D3", "三部", "bid_specialist", "投标专员")
        );

        List<AssignmentCandidateDTO> result = policy.filter(
                candidates, true, List.of(), AssignmentContext.of("task", null, null), null, null);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(AssignmentCandidateDTO::userId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("hasGlobalAccess=false 时仅返回 allowedDeptCodes 内的候选人")
    void filter_LimitedAccess_ReturnsOnlyAllowedDeptCandidates() {
        List<User> candidates = List.of(
                user(1L, "张三", "D1", "一部", "bid_admin", "投标管理员"),
                user(2L, "李四", "D2", "二部", "sales", "销售"),
                user(3L, "王五", "D3", "三部", "bid_specialist", "投标专员")
        );

        List<AssignmentCandidateDTO> result = policy.filter(
                candidates, false, List.of("D1"), AssignmentContext.of("task", null, null), null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).deptCode()).isEqualTo("D1");
    }

    @Test
    @DisplayName("deptCode 参数过滤（大小写不敏感）")
    void filter_DeptCodeFilter_CaseInsensitive() {
        List<User> candidates = List.of(
                user(1L, "张三", "BID_DEPT", "投标部", "bid_admin", "投标管理员"),
                user(2L, "李四", "SALES_DEPT", "销售部", "sales", "销售")
        );

        List<AssignmentCandidateDTO> result = policy.filter(
                candidates, true, List.of(), AssignmentContext.of("task", null, null), "bid_dept", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deptCode()).isEqualTo("BID_DEPT");
    }

    @Test
    @DisplayName("roleCode 参数过滤（大小写不敏感）")
    void filter_RoleCodeFilter_CaseInsensitive() {
        List<User> candidates = List.of(
                user(1L, "张三", "D1", "一部", "bid_admin", "投标管理员"),
                user(2L, "李四", "D2", "二部", "sales", "销售")
        );

        List<AssignmentCandidateDTO> result = policy.filter(
                candidates, true, List.of(), AssignmentContext.of("task", null, null), null, "BID_ADMIN");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roleCode()).isEqualTo("bid_admin");
    }

    @Test
    @DisplayName("排序：departmentCode → roleName → fullName（nullsLast, CASE_INSENSITIVE）")
    void filter_SortsByDeptCodeThenRoleNameThenFullName() {
        List<User> candidates = List.of(
                user(3L, "王五", "D2", "二部", "sales", "销售"),
                user(1L, "张三", "D1", "一部", "bid_admin", "投标管理员"),
                user(2L, "李四", "D1", "一部", "bid_lead", "投标组长"),
                user(4L, "赵六", null, null, "bid_specialist", "投标专员")
        );

        List<AssignmentCandidateDTO> result = policy.filter(
                candidates, true, List.of(), AssignmentContext.of("task", null, null), null, null);

        assertThat(result).extracting(AssignmentCandidateDTO::userId)
                .containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("空候选人列表返回空列表")
    void filter_EmptyCandidates_ReturnsEmptyList() {
        List<AssignmentCandidateDTO> result = policy.filter(
                List.of(), true, List.of(), AssignmentContext.of("task", null, null), null, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("所有过滤条件组合（全局权限 + deptCode + roleCode）")
    void filter_CombinedFilters_GlobalAccessWithDeptAndRole() {
        List<User> candidates = List.of(
                user(1L, "张三", "BID_DEPT", "投标部", "bid_admin", "投标管理员"),
                user(2L, "李四", "BID_DEPT", "投标部", "sales", "销售"),
                user(3L, "王五", "SALES_DEPT", "销售部", "bid_admin", "投标管理员")
        );

        List<AssignmentCandidateDTO> result = policy.filter(
                candidates, true, List.of(), AssignmentContext.of("task", null, null), "bid_dept", "bid_admin");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).deptCode()).isEqualTo("BID_DEPT");
        assertThat(result.get(0).roleCode()).isEqualTo("bid_admin");
    }

    @Test
    @DisplayName("P1.2: task 场景排除 staff 角色")
    void filter_TaskContext_ExcludesStaffRole() {
        List<User> candidates = List.of(
                user(1L, "张三", "D1", "一部", "bid_admin", "投标管理员"),
                user(2L, "李四", "D1", "一部", "staff", "普通员工")
        );

        List<AssignmentCandidateDTO> result = policy.filter(
                candidates, true, List.of(), AssignmentContext.of("task", null, null), null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roleCode()).isEqualTo("bid_admin");
    }

    @Test
    @DisplayName("P1.2: tender 场景排除 staff + admin_staff 角色")
    void filter_TenderContext_ExcludesStaffAndAdminStaffRoles() {
        List<User> candidates = List.of(
                user(1L, "张三", "D1", "一部", "bid_admin", "投标管理员"),
                user(2L, "李四", "D1", "一部", "staff", "普通员工"),
                user(3L, "王五", "D1", "一部", "admin_staff", "行政人员")
        );

        List<AssignmentCandidateDTO> result = policy.filter(
                candidates, true, List.of(), AssignmentContext.of("tender", null, null), null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roleCode()).isEqualTo("bid_admin");
    }

    private User user(Long id, String fullName, String deptCode, String deptName,
                      String roleCode, String roleName) {
        RoleProfile roleProfile = RoleProfile.builder().code(roleCode).name(roleName).build();
        return User.builder()
                .id(id)
                .fullName(fullName)
                .departmentCode(deptCode)
                .departmentName(deptName)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
    }
}
