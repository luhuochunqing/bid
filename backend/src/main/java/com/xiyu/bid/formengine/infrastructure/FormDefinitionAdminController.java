// Input: HTTP 请求、表单定义 DTO
// Output: 管理端 API（ADMIN 角色访问）
// Pos: Infrastructure/接口适配层
// 维护声明: 仅做协议适配和参数校验，业务逻辑委托 Application 层.
package com.xiyu.bid.formengine.infrastructure;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.formengine.application.FormDefinitionAdminService;
import com.xiyu.bid.formengine.application.FormDefinitionAdminService.ConditionRuleDto;
import com.xiyu.bid.formengine.application.FormDefinitionAdminService.CreateFormDefinitionRequest;
import com.xiyu.bid.formengine.application.FormDefinitionAdminService.UpdateFormDefinitionRequest;
import com.xiyu.bid.formengine.application.FormDefinitionAdminService.VisibilityRuleDto;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldConditionEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldVisibilityEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.CrossFieldValidationRuleEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.TenantFormFieldOverrideEntity;
import java.util.List;

/**
 * 表单定义管理端 API。
 * 仅限 ADMIN 角色访问。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/form-definitions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "表单定义管理", description = "表单定义 CRUD 和规则管理 API（仅 ADMIN）")
public class FormDefinitionAdminController {

    private final FormDefinitionAdminService adminService;

    @GetMapping
    @Operation(summary = "分页查询表单定义列表")
    public ResponseEntity<ApiResponse<Page<FormDefinitionRegistryEntity>>> listDefinitions(
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("GET /api/admin/form-definitions - page={}", pageable.getPageNumber());
        Page<FormDefinitionRegistryEntity> page = adminService.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success("查询成功", page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询表单定义详情")
    public ResponseEntity<ApiResponse<FormDefinitionRegistryEntity>> getDefinition(@PathVariable Long id) {
        log.debug("GET /api/admin/form-definitions/{}", id);
        FormDefinitionRegistryEntity entity = adminService.findById(id);
        return ResponseEntity.ok(ApiResponse.success("查询成功", entity));
    }

    @PostMapping
    @Operation(summary = "创建表单定义")
    public ResponseEntity<ApiResponse<FormDefinitionRegistryEntity>> createDefinition(
            @RequestBody CreateFormDefinitionRequest request,
            @AuthenticationPrincipal UserDetails user) {

        log.info("POST /api/admin/form-definitions - scope={}, user={}", request.scope(), user.getUsername());
        FormDefinitionRegistryEntity entity = adminService.create(request, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("创建成功", entity));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新表单定义")
    public ResponseEntity<ApiResponse<FormDefinitionRegistryEntity>> updateDefinition(
            @PathVariable Long id,
            @RequestBody UpdateFormDefinitionRequest request) {

        log.info("PUT /api/admin/form-definitions/{}", id);
        FormDefinitionRegistryEntity entity = adminService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("更新成功", entity));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除（禁用）表单定义")
    public ResponseEntity<ApiResponse<Void>> deleteDefinition(@PathVariable Long id) {
        log.info("DELETE /api/admin/form-definitions/{}", id);
        adminService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布表单定义（递增版本号）")
    public ResponseEntity<ApiResponse<FormDefinitionRegistryEntity>> publishDefinition(@PathVariable Long id) {
        log.info("POST /api/admin/form-definitions/{}/publish", id);
        FormDefinitionRegistryEntity entity = adminService.publish(id);
        return ResponseEntity.ok(ApiResponse.success("发布成功", entity));
    }

    @PostMapping("/{id}/visibility")
    @Operation(summary = "保存字段可见性规则")
    public ResponseEntity<ApiResponse<Void>> saveVisibilityRules(
            @PathVariable Long id,
            @RequestBody List<VisibilityRuleDto> rules) {

        log.info("POST /api/admin/form-definitions/{}/visibility - ruleCount={}", id, rules.size());
        adminService.saveVisibilityRules(id, rules);
        return ResponseEntity.ok(ApiResponse.success("保存成功", null));
    }

    @PostMapping("/{id}/conditions")
    @Operation(summary = "保存字段条件规则")
    public ResponseEntity<ApiResponse<Void>> saveConditionRules(
            @PathVariable Long id,
            @RequestBody List<ConditionRuleDto> rules) {

        log.info("POST /api/admin/form-definitions/{}/conditions - ruleCount={}", id, rules.size());
        adminService.saveConditionRules(id, rules);
        return ResponseEntity.ok(ApiResponse.success("保存成功", null));
    }

    @GetMapping("/{id}/visibility")
    @Operation(summary = "获取字段可见性规则")
    public ResponseEntity<ApiResponse<List<FormFieldVisibilityEntity>>> getVisibilityRules(@PathVariable Long id) {
        log.debug("GET /api/admin/form-definitions/{}/visibility", id);
        List<FormFieldVisibilityEntity> rules = adminService.getVisibilityRules(id);
        return ResponseEntity.ok(ApiResponse.success("查询成功", rules));
    }

    @GetMapping("/{id}/conditions")
    @Operation(summary = "获取字段条件规则")
    public ResponseEntity<ApiResponse<List<FormFieldConditionEntity>>> getConditionRules(@PathVariable Long id) {
        log.debug("GET /api/admin/form-definitions/{}/conditions", id);
        List<FormFieldConditionEntity> rules = adminService.getConditionRules(id);
        return ResponseEntity.ok(ApiResponse.success("查询成功", rules));
    }

    @GetMapping("/{id}/cross-field-rules")
    @Operation(summary = "获取跨字段验证规则")
    public ResponseEntity<ApiResponse<List<CrossFieldValidationRuleEntity>>> getCrossFieldRules(@PathVariable Long id) {
        log.debug("GET /api/admin/form-definitions/{}/cross-field-rules", id);
        List<CrossFieldValidationRuleEntity> rules = adminService.getCrossFieldRules(id);
        return ResponseEntity.ok(ApiResponse.success("查询成功", rules));
    }

    @PostMapping("/{id}/cross-field-rules")
    @Operation(summary = "保存跨字段验证规则")
    public ResponseEntity<ApiResponse<Void>> saveCrossFieldRules(
            @PathVariable Long id,
            @RequestBody List<FormDefinitionAdminService.CrossFieldRuleDto> rules) {
        log.info("POST /api/admin/form-definitions/{}/cross-field-rules - ruleCount={}", id, rules.size());
        adminService.saveCrossFieldRules(id, rules);
        return ResponseEntity.ok(ApiResponse.success("保存成功", null));
    }

    @GetMapping("/{id}/tenant-overrides")
    @Operation(summary = "获取租户字段覆盖")
    public ResponseEntity<ApiResponse<List<TenantFormFieldOverrideEntity>>> getTenantOverrides(@PathVariable Long id) {
        log.debug("GET /api/admin/form-definitions/{}/tenant-overrides", id);
        List<TenantFormFieldOverrideEntity> overrides = adminService.getTenantOverrides(id);
        return ResponseEntity.ok(ApiResponse.success("查询成功", overrides));
    }

    @PostMapping("/{id}/tenant-overrides")
    @Operation(summary = "保存租户字段覆盖")
    public ResponseEntity<ApiResponse<Void>> saveTenantOverrides(
            @PathVariable Long id,
            @RequestBody List<FormDefinitionAdminService.TenantOverrideDto> overrides) {
        log.info("POST /api/admin/form-definitions/{}/tenant-overrides - count={}", id, overrides.size());
        adminService.saveTenantOverrides(id, overrides);
        return ResponseEntity.ok(ApiResponse.success("保存成功", null));
    }
}
