package com.xiyu.bid.integration.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.integration.application.WeComIntegrationAppService;
import com.xiyu.bid.integration.dto.WeComConnectivityResponse;
import com.xiyu.bid.integration.dto.WeComIntegrationRequest;
import com.xiyu.bid.integration.dto.WeComIntegrationResponse;
import com.xiyu.bid.integration.dto.WeComSendTestRequest;
import com.xiyu.bid.integration.dto.WeComSendTestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for WeCom (企业微信) integration configuration.
 * All endpoints are under /api/admin/integrations/wecom — auto-protected as ADMIN by SecurityConfig.
 *
 * DTO ↔ domain conversion happens here; the app service handles orchestration only.
 */
@RestController
@RequestMapping("/api/admin/integrations/wecom")
@RequiredArgsConstructor
public class WeComIntegrationController {

    private final WeComIntegrationAppService appService;

    @GetMapping
    public ResponseEntity<ApiResponse<WeComIntegrationResponse>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success(appService.getConfig()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<WeComIntegrationResponse>> saveConfig(
            @Valid @RequestBody WeComIntegrationRequest request
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("当前请求缺少认证上下文");
        }
        String operator = authentication.getName();
        WeComIntegrationResponse response = appService.saveConfig(request, operator);
        return ResponseEntity.ok(ApiResponse.success("企业微信集成配置已保存", response));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<WeComConnectivityResponse>> testConnectivity() {
        WeComConnectivityResponse response = appService.testConnectivity();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/send-test")
    public ResponseEntity<ApiResponse<WeComSendTestResponse>> sendTest(
            @RequestBody(required = false) WeComSendTestRequest body
    ) {
        WeComSendTestResponse response = appService.sendTestMessage(body == null ? null : body.content());
        return ResponseEntity.ok(ApiResponse.success("测试消息已发送", response));
    }

    /** No global @ControllerAdvice maps IllegalArgumentException; handle locally to return 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}
