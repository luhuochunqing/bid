// Input: 已认证 HTTP 请求 + accountId
// Output: 该平台账号的操作日志 DTO 列表（过滤敏感操作）
// Pos: Controller/操作日志只读适配层
// 维护声明: 协议适配与权限校验；业务规则下沉到 audit 包 AuditLogItemMapper.
package com.xiyu.bid.platform.controller;

import com.xiyu.bid.audit.dto.AuditLogItemDTO;
import com.xiyu.bid.audit.service.AuditLogItemMapper;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 平台账号操作日志只读 Controller。
 * <p>对齐 {@code PerformanceAuditController} / {@code QualificationAuditController} 范式：
 * 业务实体专属审计端点，替代通用 {@code /api/audit}（{@code hasAnyRole('ADMIN','AUDITOR')}）
 * 一刀切拦截非管理员角色的问题。
 * <p>权限与 {@code PlatformAccountController} 一致：{@code hasAuthority('resource')}。
 * <p>CO-522：账户详情抽屉新增「操作日志」Tab 的后端支撑。
 * <p><b>安全</b>：查询结果过滤 {@code VIEW_PASSWORD} 日志——查看明文密码事件仅落库（独立审计），
 * 不暴露给前端展示（需求明确要求）。
 */
@RestController
@RequestMapping("/api/platform/accounts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('resource')")
public class PlatformAccountAuditController {

    private static final String ENTITY_TYPE = "PlatformAccount";
    /** 查看明文密码的 action 名（来自 PlatformAccountService.getPassword 上的 @Auditable）。 */
    private static final String ACTION_VIEW_PASSWORD = "VIEW_PASSWORD";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogItemMapper itemMapper;

    @GetMapping("/{id}/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLogItemDTO>>> getAccountAuditLogs(@PathVariable Long id) {
        List<AuditLog> logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByTimestampDesc(ENTITY_TYPE, String.valueOf(id));

        // CO-522: 过滤查看明文密码事件（不暴露给前端）+ 系统自动触发的日志
        List<AuditLog> visibleLogs = logs.stream()
                .filter(log -> !ACTION_VIEW_PASSWORD.equalsIgnoreCase(log.getAction()))
                .filter(log -> !isSystemTriggered(log))
                .toList();

        Map<String, User> userCache = resolveUsers(visibleLogs);
        List<AuditLogItemDTO> items = visibleLogs.stream()
                .map(log -> itemMapper.toItemDto(log, userCache.get(userKey(log))))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    private boolean isSystemTriggered(AuditLog log) {
        if (log == null) return false;
        String userId = log.getUserId();
        if (userId != null) {
            String uid = userId.trim().toLowerCase();
            if (uid.equals("system") || uid.equals("scheduler") || uid.equals("auto")) {
                return true;
            }
        }
        String action = log.getAction();
        if (action != null) {
            String act = action.trim().toUpperCase();
            if (act.startsWith("AUTO_")) {
                return true;
            }
        }
        return false;
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
