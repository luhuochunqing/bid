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
        deptMapping.setRoleCode("bid-Team");
        properties.setDepartmentToRoleMappings(List.of(deptMapping));
        // 白名单（person-to-role-mappings）已废弃删除——OSS 端角色码为唯一真相来源
        properties.setPersonToRoleMappings(List.of());

        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
    }

    @Test
    @DisplayName("department mapping has highest priority after person mapping removal")
    void resolve_departmentHasHighestPriority() {
        // 白名单已删除，部门映射现在是最高优先级
        OrganizationUserSnapshot snapshot = snapshot("vip@example.com", "投标管理部", "项目经理");

        JobRoleLookupResolver.ResolvedRole result = resolver.resolve(snapshot, Map.of());

        assertThat(result.roleCode()).isEqualTo("bid-Team");
        assertThat(result.source()).isEqualTo(JobRoleLookupResolver.RoleMappingSource.DEPARTMENT);
    }

    @Test
    @DisplayName("department mapping has priority over job mapping")
    void resolve_departmentOverJob() {
        OrganizationUserSnapshot snapshot = snapshot("user@example.com", "投标管理部", "项目经理");

        JobRoleLookupResolver.ResolvedRole result = resolver.resolve(snapshot, Map.of());

        assertThat(result.roleCode()).isEqualTo("bid-Team");
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

        assertThat(result.roleCode()).isEqualTo("bid-projectLeader");
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

        assertThat(result.roleCode()).isEqualTo("bid-projectLeader");
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

        assertThat(result.roleCode()).isEqualTo("bid-Team");
        assertThat(result.source()).isEqualTo(JobRoleLookupResolver.RoleMappingSource.DEPARTMENT);
    }

    private OrganizationUserSnapshot snapshot(String email, String deptName, String externalRoleCode) {
        return new OrganizationUserSnapshot(
                "100", "u100", "用户", email, "13800000000", "", deptName, "", externalRoleCode, true);
    }

    // ——— mapOssRoleCodeToInternal 单元测试 ———

    @Test
    @DisplayName("bid-SystemAdmin 作为独立角色码直接返回规范码（不再映射到 admin）")
    void mapOssRoleCodeToInternal_bidSystemAdmin_returnsCanonicalCode() {
        // 白名单已删除，bid-SystemAdmin 是独立角色码，权限等同 /bidAdmin，但不映射为 admin
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-SystemAdmin"))
                .isEqualTo("bid-SystemAdmin");
    }

    @Test
    @DisplayName("bid-SystemAdmin 大小写不敏感，返回规范码")
    void mapOssRoleCodeToInternal_bidSystemAdmin_caseInsensitive() {
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-systemadmin"))
                .isEqualTo("bid-SystemAdmin");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("BID-SYSTEMADMIN"))
                .isEqualTo("bid-SystemAdmin");
    }

    @Test
    @DisplayName("BUG #2: /bidAdmin（带斜杠前缀）是 OSS 规范角色码，直接返回规范码")
    void mapOssRoleCodeToInternal_leadingSlash_preserved() {
        // OSS 投标管理员角色码本身带前导斜杠（/bidAdmin），这是 OSS 规范
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("/bidAdmin"))
                .isEqualTo("/bidAdmin");
        // 其他角色码不带斜杠
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-TeamLeader"))
                .isEqualTo("bid-TeamLeader");
    }

    @Test
    @DisplayName("BUG #3: 大小写不一致输入返回规范码")
    void mapOssRoleCodeToInternal_mixedCase_returnsCanonicalCode() {
        // /bidAdmin 的大小写变体
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("/BidAdmin"))
                .isEqualTo("/bidAdmin");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("/BIDADMIN"))
                .isEqualTo("/bidAdmin");
        // 不带斜杠的角色码大小写变体
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-teamleader"))
                .isEqualTo("bid-TeamLeader");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("BID-TEAMLEADER"))
                .isEqualTo("bid-TeamLeader");
    }

    @Test
    @DisplayName("未注册的 roleCode 返回 null")
    void mapOssRoleCodeToInternal_unregisteredCode_returnsNull() {
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("unknown-role")).isNull();
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("")).isNull();
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal(null)).isNull();
    }

    // ——— lessons-learned.md §78 修复：OSS 返回的 admin 是其他系统的，不应被识别 ———

    @Test
    @DisplayName("§78: OSS 返回的 admin 是其他系统的（Home/CRM/SCM），返回 null 不识别")
    void mapOssRoleCodeToInternal_admin_returnsNull() {
        // admin 是本地独有的超级管理员，与 OSS 无关
        // OSS 返回的 admin 是其他系统的 admin，不应被识别为我们系统的 admin 写入缓存
        // 详见 lessons-learned.md §78（覃超颖 bidding/60 403 案例根因）
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("admin")).isNull();
    }

    @Test
    @DisplayName("§78: admin 大小写不敏感，全部返回 null")
    void mapOssRoleCodeToInternal_admin_caseInsensitive_returnsNull() {
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("ADMIN")).isNull();
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("Admin")).isNull();
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("AdMiN")).isNull();
    }

    @Test
    @DisplayName("§78: OSS 其他系统角色码（SE/PE/HomeReadonly 等）返回 null")
    void mapOssRoleCodeToInternal_otherSystemRoles_returnNull() {
        // OSS 是多系统共用平台，sysRoleList 中可能包含 Home/CRM/SCM 等系统的角色码
        // 这些角色码不属于本系统，应返回 null
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("SE")).isNull();
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("PE")).isNull();
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("HomeReadonly")).isNull();
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("CRM_ADMIN")).isNull();
    }

    @Test
    @DisplayName("§78: 7 个 bid-* 角色码全部可识别（回归测试）")
    void mapOssRoleCodeToInternal_allSevenBidRoles_returnCanonicalCode() {
        // 属于投标系统的 7 个角色码应全部可识别，确保排除 admin 时不影响 bid-* 角色码
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("/bidAdmin")).isEqualTo("/bidAdmin");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-TeamLeader")).isEqualTo("bid-TeamLeader");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-SystemAdmin")).isEqualTo("bid-SystemAdmin");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-Team")).isEqualTo("bid-Team");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-projectLeader")).isEqualTo("bid-projectLeader");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-administration")).isEqualTo("bid-administration");
        assertThat(JobRoleLookupResolver.mapOssRoleCodeToInternal("bid-otherDept")).isEqualTo("bid-otherDept");
    }
}
