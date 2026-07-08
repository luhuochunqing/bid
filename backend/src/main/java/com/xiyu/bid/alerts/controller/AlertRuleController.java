// Input: alerts service and request DTOs
// Output: Alert Rule REST API endpoints (admin/bidAdmin/bidTeamLeader-only)
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.alerts.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.alerts.dto.AlertRuleCreateRequest;
import com.xiyu.bid.alerts.dto.AlertRuleUpdateRequest;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertRuleService;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警规则 Controller。权限已收紧：仅 ADMIN、投标管理员(/bidAdmin)、投标组长(bid-TeamLeader) 可访问；
 * 投标专员(bid-Team)、行政人员(bid-administration) 等普通用户不可读取或操作告警规则。
 */
@RestController
@RequestMapping("/api/alerts/rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER')")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @PostMapping
    @Auditable(action = "CREATE", entityType = "AlertRule", description = "Create alert rule")
    public ResponseEntity<ApiResponse<AlertRule>> createAlertRule(@Valid @RequestBody AlertRuleCreateRequest request) {
        AlertRule alertRule = alertRuleService.createAlertRule(request);
        return ResponseEntity.ok(ApiResponse.success("Alert rule created successfully", alertRule));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AlertRule>> getAlertRuleById(@PathVariable Long id) {
        AlertRule alertRule = alertRuleService.getAlertRuleById(id);
        return ResponseEntity.ok(ApiResponse.success(alertRule));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlertRule>>> getAllAlertRules() {
        List<AlertRule> alertRules = alertRuleService.getAllAlertRules();
        return ResponseEntity.ok(ApiResponse.success(alertRules));
    }

    @GetMapping("/enabled")
    public ResponseEntity<ApiResponse<List<AlertRule>>> getEnabledAlertRules() {
        List<AlertRule> alertRules = alertRuleService.getEnabledAlertRules();
        return ResponseEntity.ok(ApiResponse.success(alertRules));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<AlertRule>>> getAlertRulesByType(@PathVariable AlertRule.AlertType type) {
        List<AlertRule> alertRules = alertRuleService.getAlertRulesByType(type);
        return ResponseEntity.ok(ApiResponse.success(alertRules));
    }

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", entityType = "AlertRule", description = "Update alert rule")
    public ResponseEntity<ApiResponse<AlertRule>> updateAlertRule(
            @PathVariable Long id,
            @Valid @RequestBody AlertRuleUpdateRequest request) {

        AlertRule alertRule = alertRuleService.updateAlertRule(id, request);
        return ResponseEntity.ok(ApiResponse.success("Alert rule updated successfully", alertRule));
    }

    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE", entityType = "AlertRule", description = "Delete alert rule")
    public ResponseEntity<ApiResponse<Void>> deleteAlertRule(@PathVariable Long id) {
        alertRuleService.deleteAlertRule(id);
        return ResponseEntity.ok(ApiResponse.success("Alert rule deleted successfully", null));
    }

    @PatchMapping("/{id}/toggle")
    @Auditable(action = "TOGGLE", entityType = "AlertRule", description = "Toggle alert rule enabled status")
    public ResponseEntity<ApiResponse<AlertRule>> toggleAlertRuleEnabled(@PathVariable Long id) {
        AlertRule alertRule = alertRuleService.toggleAlertRuleEnabled(id);
        return ResponseEntity.ok(ApiResponse.success("Alert rule toggled successfully", alertRule));
    }
}
