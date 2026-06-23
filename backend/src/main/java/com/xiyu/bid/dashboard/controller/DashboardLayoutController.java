package com.xiyu.bid.dashboard.controller;

import com.xiyu.bid.dashboard.dto.DashboardLayoutDTO;
import com.xiyu.bid.dashboard.service.DashboardLayoutService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
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

import java.util.Locale;

@RestController
@RequestMapping("/api/dashboard/layout")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class DashboardLayoutController {

    private final DashboardLayoutService dashboardLayoutService;

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DashboardLayoutDTO>> getMyLayout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String roleCode = resolveRoleCode(auth);

        log.info("GET /api/dashboard/layout/my - Fetching layout for role {}", roleCode);
        DashboardLayoutDTO layout = dashboardLayoutService.getLayoutByRole(roleCode);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved layout", layout));
    }

    /**
     * 从 Authentication 中解析角色码。
     * <p>
     * 优先匹配已注册的角色码（去掉 ROLE_ 前缀后用 {@link RoleProfileCatalog#isRegisteredCode}
     * 校验），避免取到 legacy 兼容层（如 ROLE_ADMIN）导致角色码错误。
     * 未匹配时回退到 {@link RoleProfileCatalog#BID_SPECIALIST_CODE}（投标专员，最小权限原则）。
     * </p>
     */
    private String resolveRoleCode(Authentication auth) {
        if (auth != null && auth.getAuthorities() != null) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                String name = authority.getAuthority();
                if (name.startsWith("ROLE_")) {
                    String candidate = name.substring(5).toLowerCase(Locale.ROOT);
                    if (RoleProfileCatalog.isRegisteredCode(candidate)) {
                        return candidate;
                    }
                } else if (RoleProfileCatalog.isRegisteredCode(name)) {
                    return name;
                }
            }
        }
        return RoleProfileCatalog.BID_SPECIALIST_CODE;
    }
}
