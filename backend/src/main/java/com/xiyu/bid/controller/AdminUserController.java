package com.xiyu.bid.controller;

import com.xiyu.bid.dto.AdminUserCreateRequest;
import com.xiyu.bid.dto.AdminUserDTO;
import com.xiyu.bid.dto.AdminUserStatusUpdateRequest;
import com.xiyu.bid.dto.AdminUserUpdateRequest;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.service.PaginatedResult;
import com.xiyu.bid.dto.UserOrganizationUpdateRequest;
import com.xiyu.bid.service.AdminUserQueryService;
import com.xiyu.bid.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final String ADMIN_ONLY = "hasRole('ADMIN')";

    private final AdminUserService adminUserService;
    private final AdminUserQueryService adminUserQueryService;

    @GetMapping
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<AdminUserDTO>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", adminUserService.listUsers()));
    }

    @GetMapping("/page")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<PaginatedResult<AdminUserDTO>>> listUsersPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String departmentCode) {
        PaginatedResult<AdminUserDTO> result = adminUserQueryService.listUsersPage(
                page, size, keyword, enabled, departmentCode);
        return ResponseEntity.ok(ApiResponse.success("查询成功", result));
    }

    @PostMapping
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<AdminUserDTO>> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        log.info("POST /api/admin/users - creating user {}", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", adminUserService.createUser(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<AdminUserDTO>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request,
            Authentication authentication
    ) {
        log.info("PUT /api/admin/users/{} - updating user", id);
        return ResponseEntity.ok(ApiResponse.success(
                "User updated successfully",
                adminUserService.updateUser(id, request, authentication.getName())
        ));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<AdminUserDTO>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusUpdateRequest request,
            Authentication authentication
    ) {
        log.info("PATCH /api/admin/users/{}/status - updating status to {}", id, request.getEnabled());
        return ResponseEntity.ok(ApiResponse.success(
                "User status updated successfully",
                adminUserService.updateStatus(id, request, authentication.getName())
        ));
    }

    @PutMapping("/{id}/organization")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<AdminUserDTO>> updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody UserOrganizationUpdateRequest request,
            Authentication authentication
    ) {
        log.info("PUT /api/admin/users/{}/organization - updating organization", id);
        return ResponseEntity.ok(ApiResponse.success(
                "User organization updated successfully",
                adminUserService.updateOrganization(id, request, authentication.getName())
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id,
            Authentication authentication
    ) {
        log.info("DELETE /api/admin/users/{} - deleting user", id);
        adminUserService.deleteUser(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }
}
