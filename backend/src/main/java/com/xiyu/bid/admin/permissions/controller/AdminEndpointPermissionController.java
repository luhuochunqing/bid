// Input: endpoint permission catalog application service
// Output: admin-only endpoint permission matrix API
// Pos: admin permissions controller
package com.xiyu.bid.admin.permissions.controller;

import com.xiyu.bid.admin.permissions.application.EndpointPermissionCatalogAppService;
import com.xiyu.bid.admin.permissions.dto.EndpointPermissionItem;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
public class AdminEndpointPermissionController {
    private final EndpointPermissionCatalogAppService catalogAppService;

    @GetMapping("/endpoints")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<EndpointPermissionItem>>> listEndpointPermissions() {
        return ResponseEntity.ok(ApiResponse.success(
                "Endpoint permissions retrieved successfully",
                catalogAppService.listEndpointPermissions()
        ));
    }
}
