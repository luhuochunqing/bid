package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
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
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock
    private ObjectProvider<OrganizationDirectoryGateway> directoryGatewayProvider;

    private OrganizationUserSyncWriter writer;

    @BeforeEach
    void setUp() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        writer = new OrganizationUserSyncWriter(userRepository, organizationDepartmentRepository, roleProfileRepository, properties, positionToRoleMapper, directoryGatewayProvider);
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
        OrganizationUserSyncWriter filteringWriter = new OrganizationUserSyncWriter(
                userRepository, organizationDepartmentRepository, roleProfileRepository, properties, positionToRoleMapper, null);

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
        OrganizationUserSyncWriter filteringWriter = new OrganizationUserSyncWriter(
                userRepository, organizationDepartmentRepository, roleProfileRepository, properties, positionToRoleMapper, null);

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
        OrganizationUserSyncWriter adminWriter = new OrganizationUserSyncWriter(
                userRepository, organizationDepartmentRepository, roleProfileRepository, properties, positionToRoleMapper, null);

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
        OrganizationUserSyncWriter nameMatchingWriter = new OrganizationUserSyncWriter(
                userRepository, organizationDepartmentRepository, roleProfileRepository, properties, positionToRoleMapper, null);

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

    private RoleProfile role(String code) {
        RoleProfile role = new RoleProfile();
        role.setCode(code);
        role.setName(code);
        role.setEnabled(true);
        return role;
    }
}
