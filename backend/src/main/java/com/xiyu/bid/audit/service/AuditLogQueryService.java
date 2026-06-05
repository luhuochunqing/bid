package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.dto.AuditLogItemDTO;
import com.xiyu.bid.audit.dto.AuditLogQueryResponse;
import com.xiyu.bid.audit.dto.AuditLogSummaryDTO;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogItemMapper itemMapper;

    public AuditLogQueryResponse queryLogs(String keyword,
                                           String action,
                                           String module,
                                           String operator,
                                           LocalDateTime start,
                                           LocalDateTime end,
                                           Boolean success) {
        List<AuditLog> logs = auditLogRepository.searchLogs(
                normalizeBlank(keyword),
                normalizeBlank(action),
                normalizeBlank(operator),
                start,
                end,
                success
        );
        return toResponse(logs, module);
    }

    public AuditLogQueryResponse queryLogsForActor(String actorUsername,
                                                   String keyword,
                                                   String action,
                                                   String module,
                                                   LocalDateTime start,
                                                   LocalDateTime end,
                                                   Boolean success) {
        String normalizedActor = normalizeBlank(actorUsername);
        if (normalizedActor == null) {
            return toResponse(List.of(), module);
        }
        String actorUserId = userRepository.findByUsername(normalizedActor)
                .map(user -> String.valueOf(user.getId()))
                .orElse(null);
        List<AuditLog> logs = auditLogRepository.searchLogsForActor(
                normalizeBlank(keyword),
                normalizeBlank(action),
                normalizedActor,
                actorUserId,
                start,
                end,
                success
        );
        return toResponse(logs, module);
    }

    private AuditLogQueryResponse toResponse(List<AuditLog> logs, String module) {
        Map<String, User> userCache = resolveUsers(logs);
        List<AuditLogItemDTO> items = logs.stream()
                .map(log -> itemMapper.toItemDto(log, userCache.get(userKey(log))))
                .filter(item -> module == null || module.isBlank() || module.equalsIgnoreCase(item.getModule()))
                .toList();
        return AuditLogQueryResponse.builder()
                .items(items)
                .summary(toSummary(items, LocalDateTime.now()))
                .build();
    }

    private Map<String, User> resolveUsers(List<AuditLog> logs) {
        Map<String, User> users = new LinkedHashMap<>();
        List<Long> ids = logs.stream()
                .map(AuditLog::getUserId)
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

    private AuditLogSummaryDTO toSummary(List<AuditLogItemDTO> items, LocalDateTime now) {
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(6).toLocalDate().atStartOfDay();
        return AuditLogSummaryDTO.builder()
                .todayCount(countSince(items, todayStart))
                .weekCount(countSince(items, weekStart))
                .failedCount(items.stream().filter(item -> "failed".equals(item.getStatus())).count())
                .activeUserCount(items.stream().map(AuditLogItemDTO::getOperator).filter(Objects::nonNull).distinct().count())
                .totalCount(items.size())
                .build();
    }

    private long countSince(List<AuditLogItemDTO> items, LocalDateTime start) {
        return items.stream()
                .map(AuditLogItemDTO::getTime)
                .filter(Objects::nonNull)
                .map(time -> LocalDateTime.parse(time, AuditLogItemMapper.AUDIT_TIME_FORMATTER))
                .filter(time -> !time.isBefore(start))
                .count();
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

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
