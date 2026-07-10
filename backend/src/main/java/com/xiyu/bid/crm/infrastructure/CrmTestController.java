package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.application.CrmJobListResponse;
import com.xiyu.bid.crm.application.CrmPermissionService;
import com.xiyu.bid.crm.application.CrmRoleService;
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

import java.util.List;

/**
 * CRM 接口测试 Controller。
 * <p>
 * 提供独立的测试端点，无需 JWT 认证。CO-152 后不再提供系统级 03595 token；
 * 权限测试须传入用户 OSS token 查询参数。
 * <p>
 * 注意：此类为测试用途，已配置为 permitAll，生产环境应移除或添加适当的安全控制。
 */
@RestController
@RequestMapping("/api/crm/test")
@PreAuthorize("permitAll()")
public class CrmTestController {

    private final OssLoginFlowService loginFlowService;
    private final CrmPermissionService permissionService;
    private final CrmRoleService roleService;

    public CrmTestController(OssLoginFlowService loginFlowService,
                             CrmPermissionService permissionService,
                             CrmRoleService roleService) {
        this.loginFlowService = loginFlowService;
        this.permissionService = permissionService;
        this.roleService = roleService;
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
     * GET /api/crm/test/permissions?systemName=xxx&amp;token=用户OSS_token
     * <p>CO-152：必须显式传用户 OSS token，不再回退全局 03595。
     */
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<CrmUserPermission>> testPermissions(
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error(
                    "token query param required (global OSS token path removed)"));
        }
        CrmUserPermission permission = permissionService.getUserPermission(token, systemName);
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved", permission));
    }

    /**
     * 全局 system-token 端点已移除（CO-152）。
     * GET /api/crm/test/system-token
     */
    @GetMapping("/system-token")
    public ResponseEntity<ApiResponse<String>> getSystemToken() {
        return ResponseEntity.ok(ApiResponse.error(
                "system-token endpoint removed: use user login OSS token instead (CO-152)"));
    }

    /**
     * 测试获取用户角色（泊冉接口4）。
     * POST /api/crm/test/job-list
     * Body: {"data":["08402","08640"]}
     */
    @PostMapping("/job-list")
    public ResponseEntity<ApiResponse<CrmJobListResponse>> testJobList(
            @RequestParam List<String> jobNumbers) {
        CrmJobListResponse response = roleService.getUserJobList(jobNumbers);
        if (response != null) {
            return ResponseEntity.ok(ApiResponse.success("Job list retrieved", response));
        }
        return ResponseEntity.ok(ApiResponse.error("Failed to get job list"));
    }
}
