package com.xiyu.bid.dashboard.controller;

import com.xiyu.bid.dashboard.dto.DashboardLayoutDTO;
import com.xiyu.bid.dashboard.service.DashboardLayoutService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/layout")
@RequiredArgsConstructor
@Slf4j
public class DashboardLayoutController {

    private final DashboardLayoutService dashboardLayoutService;

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DashboardLayoutDTO>> getMyLayout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String roleCode = "STAFF"; // default
        
        if (auth != null && auth.getAuthorities() != null) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                String role = authority.getAuthority();
                if (role.startsWith("ROLE_")) {
                    roleCode = role.substring(5);
                    break;
                }
            }
        }
        
        log.info("GET /api/dashboard/layout/my - Fetching layout for role {}", roleCode);
        DashboardLayoutDTO layout = dashboardLayoutService.getLayoutByRole(roleCode);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved layout", layout));
    }
}
