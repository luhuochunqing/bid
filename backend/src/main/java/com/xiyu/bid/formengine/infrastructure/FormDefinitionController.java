// Input: HTTP 请求、scope 路径参数
// Output: 运行时 API（认证用户访问）
// Pos: Infrastructure/接口适配层
// 维护声明: 仅做协议适配和参数校验，业务逻辑委托 Application 层.
package com.xiyu.bid.formengine.infrastructure;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.formengine.application.AdaptiveFormService;
import com.xiyu.bid.formengine.domain.ResolvedForm;
import com.xiyu.bid.formengine.domain.SubmitResult;
import com.xiyu.bid.formengine.domain.ValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 表单运行时 API。
 * 用于前端动态获取表单 schema、验证数据和提交表单。
 */
@Slf4j
@RestController
@RequestMapping("/api/form-definitions")
@RequiredArgsConstructor
@Tag(name = "表单运行时", description = "动态表单运行时 API")
public class FormDefinitionController {

    private final AdaptiveFormService adaptiveFormService;

    @GetMapping("/{scope}/active")
    @Operation(summary = "获取激活的表单定义", description = "根据 scope 返回已解析的表单定义，包含可见性规则处理后的字段")
    public ResponseEntity<ApiResponse<ResolvedForm>> getActiveForm(
            @PathVariable String scope,
            @AuthenticationPrincipal UserDetails user) {

        log.debug("GET /api/form-definitions/{}/active - user={}", scope, user.getUsername());

        Long orgId = null; // M3: resolve from JWT tenant claim
        ResolvedForm form = adaptiveFormService.resolve(scope, user.getUsername(), orgId);
        return ResponseEntity.ok(ApiResponse.success("获取表单定义成功", form));
    }

    @PostMapping("/{scope}/validate")
    @Operation(summary = "验证表单数据", description = "在不保存的情况下验证表单数据是否满足字段定义和验证规则")
    public ResponseEntity<ApiResponse<ValidationResult>> validateForm(
            @PathVariable String scope,
            @RequestBody Map<String, Object> formData,
            @AuthenticationPrincipal UserDetails user) {

        log.debug("POST /api/form-definitions/{}/validate - user={}", scope, user.getUsername());

        ValidationResult result = adaptiveFormService.validate(scope, formData);
        return ResponseEntity.ok(ApiResponse.success("验证完成", result));
    }

    @PostMapping("/{scope}/submit")
    @Operation(summary = "提交表单数据", description = "验证并提交表单数据")
    public ResponseEntity<ApiResponse<SubmitResult>> submitForm(
            @PathVariable String scope,
            @RequestBody Map<String, Object> formData,
            @AuthenticationPrincipal UserDetails user) {

        log.info("POST /api/form-definitions/{}/submit - user={}", scope, user.getUsername());

        SubmitResult result = adaptiveFormService.submit(scope, formData, user.getUsername());
        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.success("提交成功", result));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(result.message()));
    }
}
