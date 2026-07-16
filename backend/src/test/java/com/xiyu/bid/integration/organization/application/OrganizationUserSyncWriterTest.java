package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.domain.policy.SystemRoleListMapper;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationUserSyncWriter - user persistence")
class OrganizationUserSyncWriterTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleProfileRepository roleProfileRepository;
    @Mock
    private OrganizationDepartmentRepository organizationDepartmentRepository;

    private OrganizationUserSyncWriter writer;

    @BeforeEach
    void setUp() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        // 测试 upsert 主流程时不启用白名单过滤（skipUnmappedUsers 默认 true 会跳过未匹配用户）
        properties.setSkipUnmappedUsers(false);
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        writer = new OrganizationUserSyncWriter(userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);
    }

    @Test
    @DisplayName("upsert maps external user id without using it as username")
    void upsert_mapsExternalUserIdSeparately() {
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("customer-org", "10001")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role("bid-Team")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("customer-org", "event-key", new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "bid-Team", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("zhangsan");
        assertThat(saved.getValue().getExternalOrgUserId()).isEqualTo("10001");
        assertThat(saved.getValue().getLastOrgEventKey()).isEqualTo("event-key");
    }

    @Test
    @DisplayName("upsert new OSS user must use locked password hash")
    void upsert_newOssUser_usesLockedPasswordHash() {
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "10002")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role("bid-Team")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "10002", "lisi", "李四", "lisi@example.com",
                "13800000001", "sales", "销售部", "", "bid-Team", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword())
                .isEqualTo(OrganizationUserSyncWriter.LOCKED_PASSWORD_HASH);
    }

    @Test
    @DisplayName("upsert updates mutable email on the same immutable external user id")
    void upsert_updatesMutableEmailByExternalId() {
        User existing = new User();
        existing.setUsername("zhangsan");
        existing.setEmail("old@example.com");
        existing.setRole(User.Role.MANAGER);
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "720518523"))
                .thenReturn(Optional.of(existing));
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role("bid-Team")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "720518523", "zhangsan", "张三", "new@example.com",
                "13800000000", "3730158", "销售部", "", "bid-Team", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
        verify(userRepository, never()).findByEmail("new@example.com");
    }

    @Test
    @DisplayName("upsert rejects missing required email instead of fabricating placeholder")
    void upsert_rejectsMissingEmail() {
        assertThatThrownBy(() -> writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "720518523", "zhangsan", "张三", "",
                "13800000000", "3730158", "销售部", "", "", true
        ))).hasMessageContaining("邮箱");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("upsert rejects missing required phone")
    void upsert_rejectsMissingPhone() {
        assertThatThrownBy(() -> writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "720518523", "zhangsan", "张三", "zhangsan@example.com",
                "", "3730158", "销售部", "", "", true
        ))).hasMessageContaining("手机号");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("disable marks existing user inactive by immutable external user id")
    void disable_marksExistingUserInactiveByExternalId() {
        User existing = new User();
        existing.setEnabled(true);
        existing.setExternalOrgUserId("720518523");
        existing.setExternalOrgSourceApp("oss");
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "720518523"))
                .thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.disableByExternalId("oss", "event-key", "720518523");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEnabled()).isFalse();
        assertThat(saved.getValue().getLastOrgEventKey()).isEqualTo("event-key");
        assertThat(saved.getValue().getLastOrgSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("未匹配角色 + 本地不存在的新用户：不创建（避免 users 表膨胀无角色记录）")
    void unmatchedRole_newUser_notCreated() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setSkipUnmappedUsers(true);
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter filteringWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "999")).thenReturn(Optional.empty());

        filteringWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "999", "unknown", "未知人员", "unknown@example.com",
                "13800000000", "9999", "未知部", "", "unknown", true
        ));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("未匹配角色 + 本地已存在 + OSS 在职：保持 enabled=true（不再强制禁用），刷新基础信息")
    void unmatchedRole_existingOssActiveUser_keepsEnabled_refreshesInfo() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setSkipUnmappedUsers(true);
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter filteringWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        User existing = new User();
        existing.setEnabled(false); // 历史脏数据：被旧逻辑禁用
        existing.setFullName("旧名字");
        existing.setExternalOrgUserId("999");
        existing.setExternalOrgSourceApp("oss");
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "999")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        filteringWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "999", "unknown", "新名字", "unknown@example.com",
                "13900000000", "9999", "投标部", "", "unknown", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEnabled()).isTrue();          // OSS 在职 → 启用
        assertThat(saved.getValue().getFullName()).isEqualTo("新名字");  // 基础信息刷新
        assertThat(saved.getValue().getPhone()).isEqualTo("13900000000");
        assertThat(saved.getValue().getLastOrgEventKey()).isEqualTo("event-key");
    }

    @Test
    @DisplayName("未匹配角色 + 本地已存在 + OSS 离职：enabled=false（离职仍正确禁用）")
    void unmatchedRole_existingOssResignedUser_disabled() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setSkipUnmappedUsers(true);
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter filteringWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        User existing = new User();
        existing.setEnabled(true);
        existing.setExternalOrgUserId("999");
        existing.setExternalOrgSourceApp("oss");
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "999")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        filteringWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "999", "unknown", "离职人员", "resigned@example.com",
                "13800000000", "9999", "未知部", "", "unknown", false
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEnabled()).isFalse();  // OSS 离职 → 禁用
        assertThat(saved.getValue().getLastOrgEventKey()).isEqualTo("event-key");
    }

    @Test
    @DisplayName("OSS 用户不会被提升为 admin（白名单已删除，allowAdminElevation=false）")
    void ossUser_cannotBeElevatedToAdmin() {
        // 白名单已删除，OSS 用户永远不会被映射为 admin（admin 是本地超级管理员，和 OSS 无关）
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setPersonToRoleMappings(List.of());
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter adminWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "03595")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("/bidAdmin")).thenReturn(Optional.of(role("/bidAdmin")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "03595", "dean_zhang", "张頔", "dean_zhang@ehsy.com",
                "13800000000", "1001", "投标管理部", "", "/bidAdmin", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        // OSS 传 /bidAdmin 就用 /bidAdmin，不提升为 admin
        assertThat(saved.getValue().getRoleCode()).isEqualTo("/bidAdmin");
    }

    @Test
    @DisplayName("OSS bid-SystemAdmin 角色码直接使用，不映射为 admin")
    void ossBidSystemAdmin_usedDirectlyNotMappedToAdmin() {
        // 白名单已删除，bid-SystemAdmin 是独立角色码，权限等同 /bidAdmin 但不映射为 admin
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setPersonToRoleMappings(List.of());
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter systemAdminWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "100")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-SystemAdmin")).thenReturn(Optional.of(role("bid-SystemAdmin")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        systemAdminWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "100", "yuan123", "袁思琪", "yuan@example.com",
                "13800000000", "1001", "非投标部门", "", "bid-SystemAdmin", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        // bid-SystemAdmin 直接使用，不映射为 admin
        assertThat(saved.getValue().getRoleCode()).isEqualTo("bid-SystemAdmin");
    }

    @Test
    @DisplayName("position mapping with camelCase OSS role code resolves to internal role ignoring case")
    void mapPositionToRole_ossRoleCodeCaseInsensitive_resolvesToInternalRole() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        OrganizationIntegrationProperties.PositionToRoleMapping mapping = new OrganizationIntegrationProperties.PositionToRoleMapping();
        mapping.setPositionPattern("^项目经理$");
        mapping.setRoleCode("bid-projectLeader");
        properties.setPositionToRoleMappings(List.of(mapping));
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter projectLeaderWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "1001")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-projectLeader")).thenReturn(Optional.of(role("bid-projectLeader")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectLeaderWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "1001", "pm001", "项目经理", "pm@example.com",
                "13800000000", "2001", "投标项目部", "", "项目经理", true));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoleCode()).isEqualTo("bid-projectLeader");
    }

    @Test
    @DisplayName("sysRoleList fallback maps to internal role when job name does not match")
    void mapSysRoleList_fallback_resolvesToInternalRole() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        OrganizationIntegrationProperties.PositionToRoleMapping mapping = new OrganizationIntegrationProperties.PositionToRoleMapping();
        mapping.setPositionPattern("^投标项目负责人$");
        mapping.setRoleCode("bid-projectLeader");
        properties.setPositionToRoleMappings(List.of(mapping));
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter sysRoleWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "1002")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-projectLeader")).thenReturn(Optional.of(role("bid-projectLeader")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, OssUserJobAndRoleDto> lookupMap = Map.of(
                "staff002", new OssUserJobAndRoleDto("staff002", "主管", List.of("投标项目负责人"), "在职", "启用", "主管用户")
        );

        sysRoleWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "1002", "staff002", "主管用户", "supervisor@example.com",
                "13800000000", "2002", "投标项目部", "", "", true), lookupMap);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoleCode()).isEqualTo("bid-projectLeader");
    }

    @Test
    @DisplayName("白名单已删除：部门映射优先于 sysRoleList（不再有 person mapping 优先级）")
    void rolePriority_departmentOverSysRoleList() {
        // 白名单已删除，优先级现在是：部门 > 岗位 > sysRoleList
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setPersonToRoleMappings(List.of());
        OrganizationIntegrationProperties.PositionToRoleMapping positionMapping = new OrganizationIntegrationProperties.PositionToRoleMapping();
        positionMapping.setPositionPattern("^投标项目负责人$");
        positionMapping.setRoleCode("bid-projectLeader");
        properties.setPositionToRoleMappings(List.of(positionMapping));
        OrganizationIntegrationProperties.DepartmentToRoleMapping deptMapping = new OrganizationIntegrationProperties.DepartmentToRoleMapping();
        deptMapping.setDepartmentPattern("投标管理部");
        deptMapping.setRoleCode("bid-Team");
        properties.setDepartmentToRoleMappings(List.of(deptMapping));
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter priorityWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "1003")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role("bid-Team")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, OssUserJobAndRoleDto> lookupMap = Map.of(
                "boss001", new OssUserJobAndRoleDto("boss001", "主管", List.of("投标项目负责人"), "在职", "启用", "老板")
        );

        priorityWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "1003", "boss001", "老板", "boss@example.com",
                "13800000000", "2003", "投标管理部", "", "", true), lookupMap);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        // 部门映射优先于 sysRoleList
        assertThat(saved.getValue().getRoleCode()).isEqualTo("bid-Team");
    }

    private RoleProfile role(String code) {
        RoleProfile role = new RoleProfile();
        role.setCode(code);
        role.setName(code);
        role.setEnabled(true);
        return role;
    }

    @Test
    @DisplayName("LOCKED_PASSWORD_HASH must be a valid BCrypt hash")
    void lockedPasswordHash_validBcrypt_format() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("any_password", OrganizationUserSyncWriter.LOCKED_PASSWORD_HASH))
                .isFalse();
        assertThat(OrganizationUserSyncWriter.LOCKED_PASSWORD_HASH)
                .startsWith("$2a$")
                .hasSize(60);
    }

    @Test
    @DisplayName("spec 037: OSS 同步时用 username 填充 crm_sales_no（generateToken 不再依赖 OSS token）")
    void upsert_shouldFillCrmSalesNoFromUsername() {
        // 生产 bug：users.crm_sales_no 全表 NULL，导致 CrmAuthService 无法用 salesNo 换 CRM JWT
        // 修复：OSS 同步时把 username（即 OSS 工号）写入 crm_sales_no
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "10003")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role("bid-Team")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "10003", "04503", "王旭州", "wang@example.com",
                "13800000000", "sales", "销售部", "", "bid-Team", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        // spec 037: OSS 工号即 CRM salesNo，填充后 generateToken 不再依赖 OSS token
        assertThat(saved.getValue().getCrmSalesNo()).isEqualTo("04503");
        assertThat(saved.getValue().getEmployeeNumber()).isEqualTo("04503");
        assertThat(saved.getValue().getUsername()).isEqualTo("04503");
    }

    @Test
    @DisplayName("spec 037 Review H1: username 不像工号（邮箱前缀）时跳过 employee_number/crm_sales_no 填充")
    void upsert_usernameNotLikeEmployeeNumber_skipsEmployeeFields() {
        // 防御场景：OSS 上游改推邮箱前缀（如 john.doe），不应污染 employee_number/crm_sales_no
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "10004")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role("bid-Team")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "10004", "john.doe", "约翰", "john@example.com",
                "13800000000", "sales", "销售部", "", "bid-Team", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        // username 仍写入（登录账号不因格式跳过）
        assertThat(saved.getValue().getUsername()).isEqualTo("john.doe");
        // 但 employee_number 和 crm_sales_no 不应被非工号污染
        assertThat(saved.getValue().getEmployeeNumber()).isNull();
        assertThat(saved.getValue().getCrmSalesNo()).isNull();
    }

    @Test
    @DisplayName("spec 037 Review H1: 纯数字工号（如 04503）正常填充 employeeNumber/crmSalesNo")
    void upsert_alphanumericEmployeeNumber_fillsNormally() {
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "10005")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role("bid-Team")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "10005", "04503", "王旭州", "wangxz@example.com",
                "13800000000", "sales", "销售部", "", "bid-Team", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("04503");
        assertThat(saved.getValue().getEmployeeNumber()).isEqualTo("04503");
        assertThat(saved.getValue().getCrmSalesNo()).isEqualTo("04503");
    }
}
