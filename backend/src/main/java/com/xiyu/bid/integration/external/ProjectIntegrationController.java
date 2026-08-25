// Input: PUT /api/integration/projects/{sourceSystem}/{sourceId} + ProjectUpdateRequest
// Output: ApiResponse<ProjectUpdateResponse>（200）；404/423/422 由全局异常处理
// Pos: integration/external/ - 对外集成接口六：项目修改（接口规范 v3.12 §3.6）
package com.xiyu.bid.integration.external;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 外部项目修改接口（接口规范 v3.12 §3.6）。
 * 路径: /api/integration/projects
 * 认证方式: X-API-Key Header（由 ApiKeyAuthenticationFilter 处理）。
 * scope 要求: tender:write（复用 CRM 现有 API Key 权限，无需额外配置）。
 *
 * <p>按 (sourceSystem, sourceId) 定位标讯再定位其关联项目；仅写入项目数据，
 * 不修改标讯本身的任何字段。
 */
@RestController
@Tag(name = "项目修改（外部API v3.12）", description = "第三方系统对接接口，接口规范 v3.12 §3.6，通过 X-API-Key 认证")
@RequestMapping("/api/integration/projects")
@PreAuthorize("hasRole('EXTERNAL_API')")
@RequiredArgsConstructor
@Slf4j
public class ProjectIntegrationController {

    private final ProjectIntegrationCommandService projectIntegrationCommandService;
    private final ApiKeyService apiKeyService;
    private final AuthService authService;

    // ── 接口六：项目修改 ───────────────────────────────────────────────────

    @PutMapping("/{sourceSystem}/{sourceId}")
    @Operation(summary = "项目修改", description = "按 (sourceSystem, sourceId) 定位标讯关联项目并部分更新；仅写项目数据，不修改标讯字段")
    public ResponseEntity<ApiResponse<ProjectUpdateResponse>> updateProject(
            @PathVariable String sourceSystem,
            @PathVariable String sourceId,
            @Valid @RequestBody ProjectUpdateRequest request) {
        log.info("INTEGRATION PUT /api/integration/projects/{}/{} - projectId={} projectNamePresent={} basicPresent={} customerInfosPresent={}",
                sourceSystem, sourceId,
                request.getProjectId(),
                request.getProjectName() != null,
                request.getEvaluationBasic() != null,
                request.getEvaluationCustomerInfos() != null);
        Long userId = resolveApiKeyUserId();
        ProjectUpdateResponse updated = projectIntegrationCommandService.updateByExternalId(
                sourceSystem, sourceId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("项目更新成功", updated));
    }

    // ── 内部方法 ────────────────────────────────────────────────────────────

    /**
     * 从 Security Context 中的 principal 解析 API Key 对应的用户 ID。
     * 与 {@code TenderIntegrationController#resolveApiKeyUserId} 同口径，用于写入 updatedBy。
     */
    private Long resolveApiKeyUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        String principal = auth.getPrincipal().toString();
        if (!principal.startsWith("api-key:")) return null;
        Long apiKeyId;
        try {
            apiKeyId = Long.parseLong(principal.substring("api-key:".length()));
        } catch (NumberFormatException e) {
            log.warn("Malformed API key principal: {}", principal);
            return null;
        }
        var keyOpt = apiKeyService.findById(apiKeyId);
        if (keyOpt.isEmpty()) return null;
        String createdBy = keyOpt.get().getCreatedBy();
        if (createdBy == null || createdBy.isBlank()) return null;
        try {
            return authService.resolveUserIdByUsername(createdBy.trim());
        } catch (UsernameNotFoundException e) {
            log.warn("API key user '{}' not found: {}", createdBy, e.getMessage());
            return null;
        }
    }
}
