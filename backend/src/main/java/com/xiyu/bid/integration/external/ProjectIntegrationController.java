// Input: PUT /api/integration/projects/{sourceSystem}/{tenderId} + ProjectUpdateRequest
// Output: ApiResponse<ProjectUpdateResponse>（200）；404/423/422 由全局异常处理
// Pos: integration/external/ - 对外集成接口六：项目修改（接口规范 v3.12 §3.6，tenderId 定位修正）
package com.xiyu.bid.integration.external;

import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * <p>按 (sourceSystem, tenderId) 定位标讯再定位其关联项目；仅写入项目数据，
 * 不修改标讯本身的任何字段。tenderId 为平台标讯主键（CRM 保存自推送标讯接口响应），
 * 非数字值在路径参数绑定阶段即被拒绝（400），杜绝来源键与内部主键的维度混淆。
 */
@RestController
@Tag(name = "项目修改（外部API v3.12）", description = "第三方系统对接接口，接口规范 v3.12 §3.6，通过 X-API-Key 认证")
@RequestMapping("/api/integration/projects")
@PreAuthorize("hasRole('EXTERNAL_API')")
@RequiredArgsConstructor
@Slf4j
public class ProjectIntegrationController {

    private final ProjectIntegrationCommandService projectIntegrationCommandService;
    private final ApiKeyUserResolver apiKeyUserResolver;

    // ── 接口六：项目修改 ───────────────────────────────────────────────────

    @PutMapping("/{sourceSystem}/{tenderId}")
    @Operation(summary = "项目修改", description = "按 (sourceSystem, tenderId) 定位标讯关联项目并部分更新；tenderId 为推送标讯响应返回的平台标讯主键；标讯尚未立项时返回 applied=false 的跳过响应；仅写项目数据，不修改标讯字段")
    public ResponseEntity<ApiResponse<ProjectUpdateResponse>> updateProject(
            @PathVariable String sourceSystem,
            @PathVariable Long tenderId,
            @Valid @RequestBody ProjectUpdateRequest request) {
        log.info("INTEGRATION PUT /api/integration/projects/{}/{} - projectId={} projectNamePresent={} basicPresent={} customerInfosPresent={}",
                sourceSystem, tenderId,
                request.getProjectId(),
                request.getProjectName() != null,
                request.getEvaluationBasic() != null,
                request.getEvaluationCustomerInfos() != null);
        Long userId = apiKeyUserResolver.resolveUserId();
        ProjectUpdateResponse updated = projectIntegrationCommandService.updateByTenderId(
                sourceSystem, tenderId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("项目更新成功", updated));
    }
}
