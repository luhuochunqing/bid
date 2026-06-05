package com.xiyu.bid.tendersource.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.tendersource.application.TenderSourceConnectionTestService;
import com.xiyu.bid.tendersource.dto.TenderSourceConfigResponse;
import com.xiyu.bid.tendersource.dto.TenderSourceConfigSaveRequest;
import com.xiyu.bid.tendersource.dto.TenderSourceTestRequest;
import com.xiyu.bid.tendersource.dto.TenderSourceTestResponse;
import com.xiyu.bid.tendersource.entity.TenderSourceConfig;
import com.xiyu.bid.tendersource.service.TenderSourceConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标讯源配置控制器。
 * GET /api/tender-sources/config — 获取当前配置（投标管理员可读）
 * PUT /api/tender-sources/config — 保存配置（仅投标管理员可写）
 * POST /api/tender-sources/test-connection — 测试连接
 */
@RestController
@RequestMapping("/api/tender-sources")
@RequiredArgsConstructor
@Slf4j
public class TenderSourceController {

    private final TenderSourceConnectionTestService tenderSourceConnectionTestService;
    private final TenderSourceConfigService tenderSourceConfigService;

    /**
     * 获取标讯源配置。
     */
    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<TenderSourceConfigResponse>> getConfig() {
        log.info("GET /api/tender-sources/config - Fetching source configuration");
        TenderSourceConfig config = tenderSourceConfigService.getConfig();
        if (config == null) {
            return ResponseEntity.ok(ApiResponse.success("Configuration not set", null));
        }
        // 使用响应 DTO 掩码 API 密钥
        TenderSourceConfigResponse response = new TenderSourceConfigResponse(config);
        return ResponseEntity.ok(ApiResponse.success("Configuration retrieved", response));
    }

    /**
     * 保存标讯源配置。
     * 仅 bid_admin 可写（通过菜单权限校验）。
     */
    @PutMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<TenderSourceConfigResponse>> saveConfig(
            @Valid @RequestBody TenderSourceConfigSaveRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("PUT /api/tender-sources/config - Saving source configuration by user: {}",
                userDetails.getUsername());

        TenderSourceConfig config = buildConfigFromRequest(request);

        // 如果有明文 apiKey，通过一个中间字段传递
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            config.setApiKeyEncrypted(request.getApiKey());
        }

        String operatorId = resolveUserId(userDetails);
        TenderSourceConfig saved = tenderSourceConfigService.saveConfig(config, operatorId, "system");

        // 使用响应 DTO 掩码 API 密钥
        TenderSourceConfigResponse response = new TenderSourceConfigResponse(saved);
        log.info("Tender source config saved successfully");
        return ResponseEntity.ok(ApiResponse.success("标讯源配置保存成功", response));
    }

    /**
     * 测试标讯源连接。
     */
    @PostMapping("/test-connection")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<TenderSourceTestResponse>> testConnection(
            @Valid @RequestBody TenderSourceTestRequest request) {
        log.info("POST /api/tender-sources/test-connection - Testing connection for platform: {}",
                request.getPlatform());
        TenderSourceTestResponse response = tenderSourceConnectionTestService.testConnection(request);
        return ResponseEntity.ok(ApiResponse.success("Connection test completed", response));
    }

    private TenderSourceConfig buildConfigFromRequest(TenderSourceConfigSaveRequest request) {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setId(1L);
        config.setPlatforms(request.getPlatforms() != null ? request.getPlatforms() : List.of());
        config.setApiEndpoint(request.getApiEndpoint());
        config.setKeywords(request.getKeywords());
        config.setRegions(request.getRegions() != null ? request.getRegions() : List.of());
        config.setBudgetMin(request.getBudgetMin() != null ? request.getBudgetMin() : java.math.BigDecimal.ZERO);
        config.setBudgetMax(request.getBudgetMax() != null ? request.getBudgetMax() : new java.math.BigDecimal("1000"));
        config.setAutoSync(request.getAutoSync() != null ? request.getAutoSync() : false);
        config.setSyncIntervalMinutes(request.getSyncIntervalMinutes() != null ? request.getSyncIntervalMinutes() : 1440);
        config.setAutoDedupe(request.getAutoDedupe() != null ? request.getAutoDedupe() : true);
        return config;
    }

    private String resolveUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return userDetails.getUsername();
    }
}
