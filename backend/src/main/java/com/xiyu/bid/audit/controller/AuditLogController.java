// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.audit.controller;

import com.xiyu.bid.audit.dto.AuditLogQueryResponse;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final IAuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<ApiResponse<AuditLogQueryResponse>> getAuditLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        Boolean success = successFromStatus(status);
        AuditLogQueryResponse response = auditLogService.queryLogs(keyword, action, module, operator, start, end, success);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuditLogQueryResponse>> getMyOperationLogs(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        Boolean success = successFromStatus(status);
        AuditLogQueryResponse response = auditLogService.queryMyOperationLogs(
                authentication.getName(),
                keyword,
                action,
                module,
                start,
                end,
                success
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Boolean successFromStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return !"failed".equalsIgnoreCase(status);
    }
}
