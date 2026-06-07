package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.performance.application.service.PerformanceAlertConfigAppService;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
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
 * Performance contract expiry alert config API.
 *差异化提醒窗口：央企180天 / 其他90天
 */
@RestController
@RequestMapping("/api/knowledge/performance/alert-config")
@RequiredArgsConstructor
public class PerformanceAlertConfigController {

    private final PerformanceAlertConfigAppService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PerformanceAlertConfig>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success("ok", service.getConfig()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PerformanceAlertConfig>> updateConfig(
            @RequestBody @NotNull UpdateRequest request) {
        PerformanceAlertConfig config = service.updateConfig(
                request.getAlertDaysSoe(),
                request.getAlertDaysDefault(),
                request.isEnabled()
        );
        return ResponseEntity.ok(ApiResponse.success("ok", config));
    }

    @Data
    public static class UpdateRequest {
        @Min(1)
        @Max(365)
        private int alertDaysSoe;
        @Min(1)
        @Max(365)
        private int alertDaysDefault;
        private boolean enabled;
    }
}