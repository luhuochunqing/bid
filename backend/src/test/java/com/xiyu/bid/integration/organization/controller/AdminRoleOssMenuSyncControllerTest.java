package com.xiyu.bid.integration.organization.controller;

import com.xiyu.bid.integration.organization.controller.AdminRoleOssMenuSyncController;
import com.xiyu.bid.dto.RoleDTO;
import com.xiyu.bid.integration.organization.application.OrganizationRoleMenuSyncAppService;
import com.xiyu.bid.integration.organization.dto.SyncRoleMenuPermissionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRoleOssMenuSyncController - OSS 菜单权限同步接口")
class AdminRoleOssMenuSyncControllerTest {

    @Mock
    private OrganizationRoleMenuSyncAppService syncAppService;

    @InjectMocks
    private AdminRoleOssMenuSyncController controller;

    @Test
    @DisplayName("sync endpoint delegates to app service and returns role DTO")
    void syncRoleMenuPermissions_delegatesAndReturnsRole() {
        RoleDTO role = RoleDTO.builder().id(42L).code("bid-TeamLeader").build();
        when(syncAppService.syncRoleMenuPermissions(42L, "08402")).thenReturn(role);

        ResponseEntity<com.xiyu.bid.dto.ApiResponse<RoleDTO>> response =
                controller.syncRoleMenuPermissions(42L, new SyncRoleMenuPermissionRequest("08402"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(role);
        verify(syncAppService).syncRoleMenuPermissions(42L, "08402");
    }
}
