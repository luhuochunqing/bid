// Input: 已认证 HTTP 请求 + performanceId
// Output: 该业绩的全部操作日志 DTO 列表
// Pos: 业绩模块/Controller/操作日志只读适配层
// 维护声明: 协议适配与权限校验；业务规则下沉到 audit 包 AuditLogItemMapper.
// CO-440: 修复投标专员在业绩详情抽屉查看操作日志 403。
//   原根因：PerformanceDetailDrawer.vue 错误调用通用 /api/audit（hasAnyRole('ADMIN','AUDITOR')）端点，
//   且 AUDITOR 是幽灵角色（系统 7 个标准 RoleProfile 中不存在），导致投标专员被一刀切拦截。
//   修复方向：对齐 QualificationAuditController 范式，新增业绩专属审计端点，
//   权限与 PerformanceController 一致：hasAuthority('performance.manage')。
package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.audit.dto.AuditLogItemDTO;
import com.xiyu.bid.audit.service.AuditLogItemMapper;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.RoleProfileCatalog;
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

/**
 * 业绩操作日志只读 Controller。
 * <p>对齐 {@code QualificationAuditController} 范式：业务实体专属审计端点，
 * 替代通用 {@code /api/audit}（{@code hasAnyRole('ADMIN','AUDITOR')}）一刀切拦截非管理员角色的问题。
 * <p>权限与 {@code PerformanceController} 一致：{@code hasAuthority('performance.manage')}。
 * <p>CO-440：投标专员在业绩详情抽屉查看操作日志 403 修复。
 */
@RestController
@RequestMapping("/api/knowledge/performance")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + RoleProfileCatalog.PERFORMANCE_MANAGE_PERMISSION + "')")
public class PerformanceAuditController {

    private static final String ENTITY_TYPE = "Performance";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogItemMapper itemMapper;

    @GetMapping("/{id}/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLogItemDTO>>> getPerformanceAuditLogs(@PathVariable Long id) {
        List<AuditLog> logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByTimestampDesc(ENTITY_TYPE, String.valueOf(id));

        // §4.2.1.7 排除系统自动触发（定时任务/扫描等无用户登录态的操作）
        List<AuditLog> userLogs = logs.stream()
                .filter(log -> !isSystemTriggered(log))
                .toList();

        Map<String, User> userCache = resolveUsers(userLogs);
        List<AuditLogItemDTO> items = userLogs.stream()
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
