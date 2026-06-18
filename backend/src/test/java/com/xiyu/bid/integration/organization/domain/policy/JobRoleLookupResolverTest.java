package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobRoleLookupResolver - role resolution priority")
class JobRoleLookupResolverTest {

    private JobRoleLookupResolver resolver;

    @BeforeEach
    void setUp() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        OrganizationIntegrationProperties.PositionToRoleMapping positionMapping = new OrganizationIntegrationProperties.PositionToRoleMapping();
        positionMapping.setPositionPattern("^项目经理$");
        positionMapping.setRoleCode("bid-projectLeader");
        OrganizationIntegrationProperties.PositionToRoleMapping sysRoleMapping = new OrganizationIntegrationProperties.PositionToRoleMapping();
        sysRoleMapping.setPositionPattern("^投标项目负责人$");
        sysRoleMapping.setRoleCode("bid-projectLeader");
        properties.setPositionToRoleMappings(List.of(positionMapping, sysRoleMapping));
        OrganizationIntegrationProperties.DepartmentToRoleMapping deptMapping = new OrganizationIntegrationProperties.DepartmentToRoleMapping();
        deptMapping.setDepartmentPattern("投标管理部");
        deptMapping.setRoleCode("bid_specialist");
        properties.setDepartmentToRoleMappings(List.of(deptMapping));
        OrganizationIntegrationProperties.PersonToRoleMapping personMapping = new OrganizationIntegrationProperties.PersonToRoleMapping();
        personMapping.setPersonIdentifier("vip@example.com");
        personMapping.setRoleCode("admin");
        properties.setPersonToRoleMappings(List.of(personMapping));

        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
    }

    @Test
    @DisplayName("person mapping has highest priority")
    void resolve_personHasHighestPriority() {
        OrganizationUserSnapshot snapshot = snapshot("vip@example.com", "投标管理部", "项目经理");

        JobRoleLookupResolver.ResolvedRole result = resolver.resolve(snapshot, Map.of());

        assertThat(result.roleCode()).isEqualTo("admin");
        assertThat(result.source()).isEqualTo(JobRoleLookupResolver.RoleMappingSource.PERSON);
    }

    @Test
    @DisplayName("department mapping has priority over job mapping")
    void resolve_departmentOverJob() {
        OrganizationUserSnapshot snapshot = snapshot("user@example.com", "投标管理部", "项目经理");

        JobRoleLookupResolver.ResolvedRole result = resolver.resolve(snapshot, Map.of());

        assertThat(result.roleCode()).isEqualTo("bid_specialist");
        assertThat(result.source()).isEqualTo(JobRoleLookupResolver.RoleMappingSource.DEPARTMENT);
    }

    @Test
    @DisplayName("job mapping has priority over sysRoleList")
    void resolve_jobOverSysRoleList() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "100", "u001", "用户", "user@example.com", "13800000000", "", "未知部", "", "", true);
        Map<String, OssUserJobAndRoleDto> lookupMap = Map.of(
                "u001", new OssUserJobAndRoleDto("u001", "项目经理", List.of("投标项目负责人"), "在职", "启用", "用户")
        );

        JobRoleLookupResolver.ResolvedRole result = resolver.resolve(snapshot, lookupMap);

        assertThat(result.roleCode()).isEqualTo("sales");
        assertThat(result.source()).isEqualTo(JobRoleLookupResolver.RoleMappingSource.JOB);
    }

    @Test
    @DisplayName("sysRoleList is used when no higher priority source matches")
    void resolve_sysRoleListFallback() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "100", "u002", "用户", "user@example.com", "13800000000", "", "未知部", "", "", true);
        Map<String, OssUserJobAndRoleDto> lookupMap = Map.of(
                "u002", new OssUserJobAndRoleDto("u002", "主管", List.of("投标项目负责人"), "在职", "启用", "用户")
        );

        JobRoleLookupResolver.ResolvedRole result = resolver.resolve(snapshot, lookupMap);

        assertThat(result.roleCode()).isEqualTo("sales");
        assertThat(result.source()).isEqualTo(JobRoleLookupResolver.RoleMappingSource.SYS_ROLE_LIST);
    }

    @Test
    @DisplayName("returns NONE when nothing matches")
    void resolve_noMatch_returnsNone() {
        OrganizationUserSnapshot snapshot = snapshot("user@example.com", "未知部", "未知岗位");

        JobRoleLookupResolver.ResolvedRole result = resolver.resolve(snapshot, Map.of());

        assertThat(result.roleCode()).isNull();
        assertThat(result.source()).isEqualTo(JobRoleLookupResolver.RoleMappingSource.NONE);
    }

    @Test
    @DisplayName("maps department when snapshot departmentName is present")
    void resolve_departmentNamePresent() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "100", "u100", "用户", "user@example.com", "13800000000", "3730158", "投标管理部", "", "", true);

        JobRoleLookupResolver.ResolvedRole result = resolver.resolve(snapshot, Map.of());

        assertThat(result.roleCode()).isEqualTo("bid_specialist");
        assertThat(result.source()).isEqualTo(JobRoleLookupResolver.RoleMappingSource.DEPARTMENT);
    }

    private OrganizationUserSnapshot snapshot(String email, String deptName, String externalRoleCode) {
        return new OrganizationUserSnapshot(
                "100", "u100", "用户", email, "13800000000", "", deptName, "", externalRoleCode, true);
    }
}
