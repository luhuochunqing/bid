// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.controller;

import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TestController {

    @GetMapping("/public/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "西域数智化投标管理平台后端运行中");
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/user/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserProfile(Authentication authentication) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", authentication.getName());
        profile.put("authorities", authentication.getAuthorities());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> adminDashboard() {
        return ResponseEntity.ok(
                ApiResponse.success("Welcome to Admin Dashboard")
        );
    }

    @GetMapping("/manager/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<String>> managerDashboard() {
        return ResponseEntity.ok(
                ApiResponse.success("Welcome to Manager Dashboard")
        );
    }
}
