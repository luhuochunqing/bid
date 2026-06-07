package com.xiyu.bid.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.CreateRoleRequest;
import com.xiyu.bid.dto.RoleDTO;
import com.xiyu.bid.dto.UpdateRoleRequest;
import com.xiyu.bid.dto.UpdateRoleStatusRequest;
import com.xiyu.bid.service.RoleProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private static final String ADMIN_ONLY = "hasRole('ADMIN')";

    private final RoleProfileService roleProfileService;

    @GetMapping
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<RoleDTO>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", roleProfileService.listRoles()));
    }

    @PostMapping
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<RoleDTO>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        log.info("POST /api/admin/roles - creating role {}", request.getCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", roleProfileService.createRole(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<RoleDTO>> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        log.info("PUT /api/admin/roles/{} - updating role", id);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", roleProfileService.updateRole(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<RoleDTO>> updateRoleStatus(@PathVariable Long id, @Valid @RequestBody UpdateRoleStatusRequest request) {
        log.info("PATCH /api/admin/roles/{}/status - updating role status", id);
        return ResponseEntity.ok(ApiResponse.success("Role status updated successfully", roleProfileService.updateRoleStatus(id, request)));
    }

    @PostMapping("/{id}/reset-default")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<RoleDTO>> resetRole(@PathVariable Long id) {
        log.info("POST /api/admin/roles/{}/reset-default - resetting role", id);
        return ResponseEntity.ok(ApiResponse.success("Role reset successfully", roleProfileService.resetRole(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        log.info("DELETE /api/admin/roles/{} - deleting role", id);
        roleProfileService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully", null));
    }
}
