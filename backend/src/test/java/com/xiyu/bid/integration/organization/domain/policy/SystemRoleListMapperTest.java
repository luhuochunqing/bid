package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SystemRoleListMapper - sysRoleList to internal role")
class SystemRoleListMapperTest {

    @Test
    @DisplayName("maps first matching sysRole name to internal role code")
    void map_firstMatch_returnsRoleCode() {
        OrganizationIntegrationProperties properties = propertiesWithMapping("^投标项目负责人$", "bid-projectLeader");
        SystemRoleListMapper mapper = new SystemRoleListMapper(new PositionToRoleMapper(properties));

        String roleCode = mapper.map(List.of("管理员", "投标项目负责人"));

        assertThat(roleCode).isEqualTo("bid-projectLeader");
    }

    @Test
    @DisplayName("returns null when no sysRole matches")
    void map_noMatch_returnsNull() {
        OrganizationIntegrationProperties properties = propertiesWithMapping("^投标项目负责人$", "bid-projectLeader");
        SystemRoleListMapper mapper = new SystemRoleListMapper(new PositionToRoleMapper(properties));

        String roleCode = mapper.map(List.of("普通员工", "访客"));

        assertThat(roleCode).isNull();
    }

    @Test
    @DisplayName("handles null or empty list")
    void map_emptyList_returnsNull() {
        SystemRoleListMapper mapper = new SystemRoleListMapper(new PositionToRoleMapper(new OrganizationIntegrationProperties()));

        assertThat(mapper.map(null)).isNull();
        assertThat(mapper.map(List.of())).isNull();
    }

    @Test
    @DisplayName("preserves original case of role code")
    void map_caseInsensitive_preservesOriginalCaseRoleCode() {
        OrganizationIntegrationProperties properties = propertiesWithMapping("^投标项目负责人$", "bid-projectLeader");
        SystemRoleListMapper mapper = new SystemRoleListMapper(new PositionToRoleMapper(properties));

        String roleCode = mapper.map(List.of("投标项目负责人"));

        assertThat(roleCode).isEqualTo("bid-projectLeader");
    }

    private OrganizationIntegrationProperties propertiesWithMapping(String pattern, String roleCode) {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        OrganizationIntegrationProperties.PositionToRoleMapping mapping = new OrganizationIntegrationProperties.PositionToRoleMapping();
        mapping.setPositionPattern(pattern);
        mapping.setRoleCode(roleCode);
        properties.setPositionToRoleMappings(List.of(mapping));
        return properties;
    }
}
