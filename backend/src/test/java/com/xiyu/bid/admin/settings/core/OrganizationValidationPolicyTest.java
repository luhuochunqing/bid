package com.xiyu.bid.admin.settings.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationValidationPolicyTest {

    @Test
    void validateUserOrganization_ShouldRequireKnownDepartmentAndEnabledRole() {
        OrganizationValidationResult result = OrganizationValidationPolicy.validateUserOrganization(
                true,
                "SALES",
                10L,
                Set.of("TECH"),
                Set.of(10L)
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("部门");
    }

    @Test
    void validateRoleDeactivation_ShouldRejectAssignedRole() {
        OrganizationValidationResult result = OrganizationValidationPolicy.validateRoleDeactivation(false, 3);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("已分配");
    }
}
