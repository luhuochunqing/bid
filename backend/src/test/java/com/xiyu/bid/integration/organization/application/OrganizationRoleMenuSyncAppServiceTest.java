package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.dto.RoleDTO;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import com.xiyu.bid.service.RoleProfileService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationRoleMenuSyncAppService - 从 OSS 同步角色菜单权限")
class OrganizationRoleMenuSyncAppServiceTest {

    @Mock
    private OrganizationDirectoryGateway gateway;

    @Mock
    private RoleProfileService roleProfileService;

    private OrganizationIntegrationProperties properties;
    private OrganizationRoleMenuSyncAppService service;

    @BeforeEach
    void setUp() {
        properties = new OrganizationIntegrationProperties();
        properties.getDirectory().setMenuCodeToPermissionKeyMappings(
                Map.of("projectmanager", "project.manager", "bidding", "bidding"));
        service = new OrganizationRoleMenuSyncAppService(gateway, roleProfileService, properties);
    }

    @Test
    @DisplayName("syncRoleMenuPermissions maps OSS menu tree and updates role")
    void syncRoleMenuPermissions_mapsAndUpdatesRole() {
        when(gateway.fetchUserMenuTree("08402", OrganizationDirectoryLookupContext.empty()))
                .thenReturn(Optional.of(List.of(
                        node("projectmanager", List.of(node("bidding", List.of())))
                )));
        RoleDTO expected = RoleDTO.builder().id(1L).code("bid_lead").build();
        when(roleProfileService.updateMenuPermissions(eq(1L), any())).thenReturn(expected);

        RoleDTO result = service.syncRoleMenuPermissions(1L, "08402");

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(roleProfileService).updateMenuPermissions(eq(1L), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("project.manager", "bidding");
    }

    @Test
    @DisplayName("syncRoleMenuPermissions clears permissions when OSS returns empty tree")
    void syncRoleMenuPermissions_emptyTree_clearsPermissions() {
        when(gateway.fetchUserMenuTree("08402", OrganizationDirectoryLookupContext.empty()))
                .thenReturn(Optional.of(List.of()));
        RoleDTO expected = RoleDTO.builder().id(1L).code("bid_lead").build();
        when(roleProfileService.updateMenuPermissions(eq(1L), any())).thenReturn(expected);

        service.syncRoleMenuPermissions(1L, "08402");

        verify(roleProfileService).updateMenuPermissions(eq(1L), eq(List.of()));
    }

    @Test
    @DisplayName("syncRoleMenuPermissions rejects blank job number")
    void syncRoleMenuPermissions_blankJobNumber_throws() {
        assertThatThrownBy(() -> service.syncRoleMenuPermissions(1L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job number");
    }

    private OssMenuTreeNode node(String menuCode, List<OssMenuTreeNode> children) {
        return new OssMenuTreeNode(
                null, menuCode, null, null, null, null,
                null, null, null, null, null, children,
                null, null, null, null, null, null,
                null, null, null, null
        );
    }
}
