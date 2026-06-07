package com.xiyu.bid.qualification.controller;

import com.xiyu.bid.businessqualification.application.service.AlertConfigAppService;
import com.xiyu.bid.businessqualification.domain.model.AlertConfig;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资质到期提醒告警配置接口。
 * <p>
 * 仅 BID_ADMIN（投标管理员）可访问，支持查询当前配置和更新配置。
 */
@RestController
@RequestMapping("/api/qualifications/alert-config")
@RequiredArgsConstructor
public class AlertConfigController {

    private final AlertConfigAppService alertConfigAppService;

    @GetMapping
    @PreAuthorize("hasRole('BID_ADMIN')")
    public ResponseEntity<ApiResponse<AlertConfig>> getConfig() {
        AlertConfig config = alertConfigAppService.getConfig();
        return ResponseEntity.ok(ApiResponse.success("Alert config retrieved successfully", config));
    }

    @PutMapping
    @PreAuthorize("hasRole('BID_ADMIN')")
    public ResponseEntity<ApiResponse<AlertConfig>> updateConfig(
            @RequestBody @NotNull UpdateAlertConfigRequest request) {
        AlertConfig config = alertConfigAppService.updateConfig(request.getAlertDays(), request.isEnabled());
        return ResponseEntity.ok(ApiResponse.success("Alert config updated successfully", config));
    }

    @Data
    public static class UpdateAlertConfigRequest {
        @Min(1)
        @Max(365)
        private int alertDays;

        private boolean enabled;
    }
}
