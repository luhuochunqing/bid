package com.xiyu.bid.admin.settings.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class DataScopePolicyTest {

    @Test
    void resolveAccessProfile_ShouldPreferUserRuleOverDepartmentAndRoleRules() {
        DepartmentGraph graph = DepartmentGraphPolicy.buildGraph(List.of(
                new DepartmentNode("SALES", "销售部", null, 1),
                new DepartmentNode("TECH", "技术部", "SALES", 2)
        ));

        CoreAccessProfile profile = DataScopePolicy.resolveAccessProfile(
                new UserAccessSubject(1L, "SALES"),
                List.of(new UserScopeRule(1L, "self", List.of(9L), List.of("TECH"))),
                List.of(new DepartmentScopeRule("SALES", "deptAndSub", List.of())),
                new RoleAccessRule("dept", List.of(), List.of()),
                graph
        );

        assertThat(profile.dataScope()).isEqualTo("self");
        assertThat(profile.explicitProjectIds()).containsExactly(9L);
        assertThat(profile.allowedDepartmentCodes()).isEmpty();
    }

    @Test
    void resolveAccessProfile_ShouldExpandDepartmentAndSubDepartments() {
        DepartmentGraph graph = DepartmentGraphPolicy.buildGraph(List.of(
                new DepartmentNode("SALES", "销售部", null, 1),
                new DepartmentNode("TECH", "技术部", "SALES", 2)
        ));

        CoreAccessProfile profile = DataScopePolicy.resolveAccessProfile(
                new UserAccessSubject(2L, "SALES"),
                List.of(),
                List.of(new DepartmentScopeRule("SALES", "deptAndSub", List.of("FIN"))),
                new RoleAccessRule("self", List.of(), List.of()),
                graph
        );

        assertThat(profile.allowedDepartmentCodes()).containsExactly("SALES", "TECH", "FIN");
    }

    @Test
    void coreScopeRecords_ShouldDefensivelyCopyMutableLists() {
        ArrayList<Long> projectIds = new ArrayList<>(List.of(1L, 2L));
        ArrayList<String> deptCodes = new ArrayList<>(List.of("SALES", "TECH"));

        CoreAccessProfile profile = new CoreAccessProfile("self", projectIds, deptCodes);
        RoleAccessRule roleRule = new RoleAccessRule("dept", projectIds, deptCodes);
        UserScopeRule userRule = new UserScopeRule(1L, "dept", projectIds, deptCodes);
        DepartmentScopeRule deptRule = new DepartmentScopeRule("SALES", "dept", deptCodes);

        projectIds.add(99L);
        deptCodes.add("FIN");

        assertThat(profile.explicitProjectIds()).containsExactly(1L, 2L);
        assertThat(profile.allowedDepartmentCodes()).containsExactly("SALES", "TECH");
        assertThat(roleRule.allowedProjectIds()).containsExactly(1L, 2L);
        assertThat(roleRule.allowedDeptCodes()).containsExactly("SALES", "TECH");
        assertThat(userRule.allowedProjectIds()).containsExactly(1L, 2L);
        assertThat(userRule.allowedDeptCodes()).containsExactly("SALES", "TECH");
        assertThat(deptRule.allowedDeptCodes()).containsExactly("SALES", "TECH");

        assertThatThrownBy(() -> profile.explicitProjectIds().add(3L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> roleRule.allowedDeptCodes().add("OPS")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> userRule.allowedProjectIds().add(3L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> deptRule.allowedDeptCodes().add("OPS")).isInstanceOf(UnsupportedOperationException.class);
    }
}
