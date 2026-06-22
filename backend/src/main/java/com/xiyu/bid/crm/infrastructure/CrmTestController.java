package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.application.CrmAuthService;
import com.xiyu.bid.crm.application.CrmPermissionService;
import com.xiyu.bid.crm.application.CrmUserPermission;
import com.xiyu.bid.crm.application.OssLoginFlowService;
import com.xiyu.bid.crm.application.OssLoginResult;
import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRM 接口测试 Controller。
 * <p>
 * 提供独立的测试端点，无需 JWT 认证，直接使用系统级 OSS token 调用泊冉接口。
 * 适用于接口调试和验证。
 * <p>
 * 注意：此类为测试用途，已配置为 permitAll，生产环境应移除或添加适当的安全控制。
 */
@RestController
@RequestMapping("/api/crm/test")
@PreAuthorize("permitAll()")
public class CrmTestController {

    private final OssLoginFlowService loginFlowService;
    private final CrmPermissionService permissionService;
    private final CrmAuthService authService;

    public CrmTestController(OssLoginFlowService loginFlowService,
                             CrmPermissionService permissionService,
                             CrmAuthService authService) {
        this.loginFlowService = loginFlowService;
        this.permissionService = permissionService;
        this.authService = authService;
    }

    /**
     * 测试完整登录流程（泊冉接口1+2+3）。
     * POST /api/crm/test/login?username=xxx&password=xxx
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<OssLoginResult>> testLogin(
            @RequestParam String username,
            @RequestParam String password) {
        OssLoginResult result = loginFlowService.authenticateDirect(username, password);
        if (result.isAuthenticated()) {
            return ResponseEntity.ok(ApiResponse.success("OSS login success", result));
        }
        return ResponseEntity.ok(ApiResponse.error("OSS login failed"));
    }

    /**
     * 测试获取用户权限（泊冉接口3）。
     * GET /api/crm/test/permissions?systemName=xxx
     */
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<CrmUserPermission>> testPermissions(
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) String token) {
        String accessToken;
        if (token != null && !token.isBlank()) {
            accessToken = token;
        } else {
            // 使用系统级 OSS token
            accessToken = authService.getValidOssToken();
        }
        CrmUserPermission permission = permissionService.getUserPermission(accessToken, systemName);
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved", permission));
    }

    /**
     * 获取系统级 OSS token（无需用户认证）。
     * GET /api/crm/test/system-token
     */
    @GetMapping("/system-token")
    public ResponseEntity<ApiResponse<String>> getSystemToken() {
        String token = authService.getValidOssToken();
        return ResponseEntity.ok(ApiResponse.success("System OSS token", token));
    }
}
