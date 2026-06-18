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
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        writer = new OrganizationUserSyncWriter(userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);
    }

    @Test
    @DisplayName("upsert maps external user id without using it as username")
    void upsert_mapsExternalUserIdSeparately() {
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("customer-org", "10001")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("staff")).thenReturn(Optional.of(role("staff")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("customer-org", "event-key", new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("zhangsan");
        assertThat(saved.getValue().getExternalOrgUserId()).isEqualTo("10001");
        assertThat(saved.getValue().getLastOrgEventKey()).isEqualTo("event-key");
    }

    @Test
    @DisplayName("upsert updates mutable email on the same immutable external user id")
    void upsert_updatesMutableEmailByExternalId() {
        User existing = new User();
        existing.setUsername("zhangsan");
        existing.setEmail("old@example.com");
        existing.setRole(User.Role.STAFF);
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "720518523"))
                .thenReturn(Optional.of(existing));
        when(roleProfileRepository.findByCodeIgnoreCase("staff")).thenReturn(Optional.of(role("staff")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "720518523", "zhangsan", "张三", "new@example.com",
                "13800000000", "3730158", "销售部", "", "", true
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
    @DisplayName("skipUnmappedUsers: new user without any mapping is not created")
    void skipUnmappedUsers_newUserWithoutMapping_notCreated() {
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
    @DisplayName("skipUnmappedUsers: existing user without any mapping is disabled")
    void skipUnmappedUsers_existingUserWithoutMapping_disabled() {
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
                "999", "unknown", "未知人员", "unknown@example.com",
                "13800000000", "9999", "未知部", "", "unknown", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEnabled()).isFalse();
        assertThat(saved.getValue().getLastOrgEventKey()).isEqualTo("event-key");
    }

    @Test
    @DisplayName("person mapping can grant admin role to new user")
    void mapPersonToRole_admin_canElevateNewUser() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        OrganizationIntegrationProperties.PersonToRoleMapping mapping = new OrganizationIntegrationProperties.PersonToRoleMapping();
        mapping.setPersonIdentifier("dean_zhang@ehsy.com");
        mapping.setRoleCode("admin");
        properties.setPersonToRoleMappings(List.of(mapping));
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter adminWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "03595")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("admin")).thenReturn(Optional.of(role("admin")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "03595", "dean_zhang", "张頔", "dean_zhang@ehsy.com",
                "13800000000", "1001", "投标管理部", "", "/bidAdmin", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoleCode()).isEqualTo("admin");
    }

    @Test
    @DisplayName("mapPersonToRole falls back to full name match")
    void mapPersonToRole_matchesByFullName() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        OrganizationIntegrationProperties.PersonToRoleMapping mapping = new OrganizationIntegrationProperties.PersonToRoleMapping();
        mapping.setPersonIdentifier("袁思琪");
        mapping.setRoleCode("bid_lead");
        properties.setPersonToRoleMappings(List.of(mapping));
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter nameMatchingWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "100")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid_lead")).thenReturn(Optional.of(role("bid_lead")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        nameMatchingWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "100", "yuan123", "袁思琪", "yuan@example.com",
                "13800000000", "1001", "投标管理部", "", "bid-Team", true
        ));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoleCode()).isEqualTo("bid_lead");
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
        when(roleProfileRepository.findByCodeIgnoreCase("sales")).thenReturn(Optional.of(role("sales")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectLeaderWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "1001", "pm001", "项目经理", "pm@example.com",
                "13800000000", "2001", "投标项目部", "", "项目经理", true));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoleCode()).isEqualTo("sales");
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
        when(roleProfileRepository.findByCodeIgnoreCase("sales")).thenReturn(Optional.of(role("sales")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, OssUserJobAndRoleDto> lookupMap = Map.of(
                "staff002", new OssUserJobAndRoleDto("staff002", "主管", List.of("投标项目负责人"), "在职", "启用", "主管用户")
        );

        sysRoleWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "1002", "staff002", "主管用户", "supervisor@example.com",
                "13800000000", "2002", "投标项目部", "", "", true), lookupMap);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoleCode()).isEqualTo("sales");
    }

    @Test
    @DisplayName("person mapping takes precedence over sysRoleList")
    void rolePriority_personOverSysRoleList() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        OrganizationIntegrationProperties.PersonToRoleMapping personMapping = new OrganizationIntegrationProperties.PersonToRoleMapping();
        personMapping.setPersonIdentifier("boss@example.com");
        personMapping.setRoleCode("admin");
        properties.setPersonToRoleMappings(List.of(personMapping));
        OrganizationIntegrationProperties.PositionToRoleMapping positionMapping = new OrganizationIntegrationProperties.PositionToRoleMapping();
        positionMapping.setPositionPattern("^投标项目负责人$");
        positionMapping.setRoleCode("bid-projectLeader");
        properties.setPositionToRoleMappings(List.of(positionMapping));
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter priorityWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, null);

        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "1003")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("admin")).thenReturn(Optional.of(role("admin")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, OssUserJobAndRoleDto> lookupMap = Map.of(
                "boss001", new OssUserJobAndRoleDto("boss001", "主管", List.of("投标项目负责人"), "在职", "启用", "老板")
        );

        priorityWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "1003", "boss001", "老板", "boss@example.com",
                "13800000000", "2003", "投标项目部", "", "", true), lookupMap);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoleCode()).isEqualTo("admin");
    }

    private RoleProfile role(String code) {
        RoleProfile role = new RoleProfile();
        role.setCode(code);
        role.setName(code);
        role.setEnabled(true);
        return role;
    }
}
