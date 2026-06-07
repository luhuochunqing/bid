package com.xiyu.bid.settings.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.settings.dto.AiModelTestRequest;
import com.xiyu.bid.settings.dto.AiModelTestResponse;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import com.xiyu.bid.settings.dto.SystemInfoResponse;
import com.xiyu.bid.settings.service.AiModelConnectionTestService;
import com.xiyu.bid.settings.service.SettingsService;
import com.xiyu.bid.settings.service.SystemInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final AiModelConnectionTestService aiModelConnectionTestService;
    private final SystemInfoService systemInfoService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getSettings()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateSettings(@RequestBody SettingsUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully", settingsService.updateSettings(request)));
    }

    @PostMapping("/ai-models/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AiModelTestResponse>> testAiModel(@RequestBody AiModelTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI model connection tested", aiModelConnectionTestService.testConnection(request)));
    }

    @GetMapping("/runtime-permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SettingsResponse.RuntimePermissionProfile>> getRuntimePermissions(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getRuntimePermissionProfile(authentication.getName())));
    }

    @GetMapping("/system-info")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemInfoResponse>> getSystemInfo() {
        return ResponseEntity.ok(ApiResponse.success(systemInfoService.getSystemInfo()));
    }
}
