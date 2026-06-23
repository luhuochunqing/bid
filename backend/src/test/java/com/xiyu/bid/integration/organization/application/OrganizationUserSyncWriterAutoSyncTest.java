package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.domain.policy.SystemRoleListMapper;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationUserSyncWriter - OSS 菜单权限自动同步")
class OrganizationUserSyncWriterAutoSyncTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleProfileRepository roleProfileRepository;
    @Mock
    private OrganizationDepartmentRepository organizationDepartmentRepository;
    @Mock
    private OssRoleMenuPermissionAutoSync autoSync;

    private OrganizationUserSyncWriter writer;

    @BeforeEach
    void setUp() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.getDirectory().setAutoSyncMenuPermissions(true);
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        writer = new OrganizationUserSyncWriter(userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, autoSync);
    }

    @Test
    @DisplayName("auto sync enabled calls merge for saved user with role")
    void upsert_autoSyncEnabled_callsMerge() {
        RoleProfile role = role("bid-Team");
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "100")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "100", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "bid-Team", true));

        verify(autoSync).mergeUserMenuPermissionsIntoRole(eq("zhangsan"), eq(role));
    }

    @Test
    @DisplayName("auto sync disabled does not call merge")
    void upsert_autoSyncDisabled_skipsMerge() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.getDirectory().setAutoSyncMenuPermissions(false);
        PositionToRoleMapper positionToRoleMapper = new PositionToRoleMapper(properties);
        SystemRoleListMapper systemRoleListMapper = new SystemRoleListMapper(positionToRoleMapper);
        JobRoleLookupResolver resolver = new JobRoleLookupResolver(properties, positionToRoleMapper, systemRoleListMapper);
        OrganizationUserSyncWriter noSyncWriter = new OrganizationUserSyncWriter(
                userRepository, roleProfileRepository, organizationDepartmentRepository, properties, resolver, autoSync);

        RoleProfile role = role("bid-Team");
        when(userRepository.findByExternalOrgSourceAppAndExternalOrgUserId("oss", "100")).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("bid-Team")).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        noSyncWriter.upsert("oss", "event-key", new OrganizationUserSnapshot(
                "100", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "bid-Team", true));

        verify(autoSync, never()).mergeUserMenuPermissionsIntoRole(any(), any());
    }

    private RoleProfile role(String code) {
        RoleProfile role = new RoleProfile();
        role.setCode(code);
        role.setName(code);
        role.setEnabled(true);
        return role;
    }
}
