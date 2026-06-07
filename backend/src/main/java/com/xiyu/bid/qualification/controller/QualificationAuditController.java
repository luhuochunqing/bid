// Input: 已认证 HTTP 请求 + qualificationId
// Output: 该资质的全部操作日志 DTO 列表
// Pos: 资质模块/Controller/操作日志只读适配层
// 维护声明: 协议适配与权限校验；业务规则下沉到 audit 包 AuditLogItemMapper.
// 蓝图 §4.1.3.7：所有角色可查看自己有权访问的资质的操作日志；不再要求 ADMIN/AUDITOR 角色.
package com.xiyu.bid.qualification.controller;

import com.xiyu.bid.audit.dto.AuditLogItemDTO;
import com.xiyu.bid.audit.service.AuditLogItemMapper;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/qualifications")
@RequiredArgsConstructor
public class QualificationAuditController {

    private static final String ENTITY_TYPE = "Qualification";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogItemMapper itemMapper;

    @GetMapping("/{id}/audit-logs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AuditLogItemDTO>>> getQualificationAuditLogs(@PathVariable Long id) {
        List<AuditLog> logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByTimestampDesc(ENTITY_TYPE, String.valueOf(id));

        Map<String, User> userCache = resolveUsers(logs);
        List<AuditLogItemDTO> items = logs.stream()
                .map(log -> itemMapper.toItemDto(log, userCache.get(userKey(log))))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    private Map<String, User> resolveUsers(List<AuditLog> logs) {
        Map<String, User> users = new LinkedHashMap<>();
        List<Long> ids = logs.stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .filter(this::isNumeric)
                .map(Long::parseLong)
                .distinct()
                .toList();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> users.put(String.valueOf(user.getId()), user));
        }
        logs.stream()
                .filter(log -> !users.containsKey(userKey(log)))
                .map(AuditLog::getUsername)
                .filter(Objects::nonNull)
                .filter(username -> !username.isBlank())
                .distinct()
                .forEach(username -> userRepository.findByUsername(username)
                        .ifPresent(user -> users.put(username, user)));
        return users;
    }

    private String userKey(AuditLog log) {
        if (log.getUserId() != null && !log.getUserId().isBlank()) {
            return log.getUserId();
        }
        return log.getUsername();
    }

    private boolean isNumeric(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(Character::isDigit);
    }
}
