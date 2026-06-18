package com.xiyu.bid.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.RoleDTO;
import com.xiyu.bid.integration.organization.application.OrganizationRoleMenuSyncAppService;
import com.xiyu.bid.integration.organization.dto.SyncRoleMenuPermissionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleOssMenuSyncController {

    private final OrganizationRoleMenuSyncAppService syncAppService;

    @PostMapping("/{id}/sync-oss-menu-permissions")
    public ResponseEntity<ApiResponse<RoleDTO>> syncRoleMenuPermissions(
            @PathVariable Long id,
            @Valid @RequestBody SyncRoleMenuPermissionRequest request
    ) {
        log.info("POST /api/admin/roles/{}/sync-oss-menu-permissions - jobNumber={}", id, request.jobNumber());
        RoleDTO role = syncAppService.syncRoleMenuPermissions(id, request.jobNumber());
        return ResponseEntity.ok(ApiResponse.success("Role menu permissions synchronized from OSS", role));
    }
}
