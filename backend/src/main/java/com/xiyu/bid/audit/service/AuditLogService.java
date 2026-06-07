// Input: 操作事件、查询条件、请求上下文和 Repository 编排服务
// Output: 异步/同步日志写入、全量审计查询与个人操作查询结果
// Pos: Service/审计/操作日志应用编排层
// 维护声明: 仅维护日志编排入口；规则、查询、映射变化请同步拆分后的协作者.
package com.xiyu.bid.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.audit.dto.AuditLogQueryResponse;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.repository.AuditLogRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计/操作日志服务 facade，保留原有契约并委托拆分后的写入、查询协作者。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService implements IAuditLogService {

    private final AuditLogWriter auditLogWriter;
    private final AuditLogQueryService auditLogQueryService;
    private final AuditRequestContextProvider contextProvider;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void log(AuditLogEntry entry) {
        auditLogWriter.logAsync(entry, contextProvider.currentContext());
    }

    @Override
    public AuditLog logSync(AuditLogEntry entry) {
        return auditLogWriter.logSync(entry, contextProvider.currentContext());
    }

    public void logLogin(String userId, String username, boolean success, String errorMessage) {
        log(AuditLogEntry.builder()
                .userId(userId)
                .username(username)
                .action("LOGIN")
                .description("User " + (success ? "logged in" : "failed to log in"))
                .success(success)
                .errorMessage(errorMessage)
                .build());
    }

    public void logLogout(String userId, String username) {
        log(AuditLogEntry.builder()
                .userId(userId)
                .username(username)
                .action("LOGOUT")
                .description("User logged out")
                .success(true)
                .build());
    }

    public void logCreate(String userId, String username, String entityType, String entityId, Object createdEntity) {
        log(AuditLogEntry.builder()
                .userId(userId)
                .username(username)
                .action("CREATE")
                .entityType(entityType)
                .entityId(entityId)
                .description("Created " + entityType + ": " + entityId)
                .newValue(toJsonString(createdEntity))
                .success(true)
                .build());
    }

    public void logUpdate(String userId, String username, String entityType, String entityId,
                          Object oldValue, Object newValue) {
        log(AuditLogEntry.builder()
                .userId(userId)
                .username(username)
                .action("UPDATE")
                .entityType(entityType)
                .entityId(entityId)
                .description("Updated " + entityType + ": " + entityId)
                .oldValue(toJsonString(oldValue))
                .newValue(toJsonString(newValue))
                .success(true)
                .build());
    }

    public void logDelete(String userId, String username, String entityType, String entityId, Object deletedEntity) {
        log(AuditLogEntry.builder()
                .userId(userId)
                .username(username)
                .action("DELETE")
                .entityType(entityType)
                .entityId(entityId)
                .description("Deleted " + entityType + ": " + entityId)
                .oldValue(toJsonString(deletedEntity))
                .success(true)
                .build());
    }

    public void logExport(String userId, String username, String entityType, int recordCount) {
        log(AuditLogEntry.builder()
                .userId(userId)
                .username(username)
                .action("EXPORT")
                .entityType(entityType)
                .description("Exported " + recordCount + " records from " + entityType)
                .success(true)
                .build());
    }

    public List<AuditLog> getUserLogs(String userId) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    public List<AuditLog> getEntityLogs(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    @Transactional
    public void cleanupOldLogs(int daysToKeep) {
        LocalDateTime beforeDate = LocalDateTime.now().minusDays(daysToKeep);
        long deletedCount = auditLogRepository.countByTimestampBefore(beforeDate);
        auditLogRepository.deleteOldLogs(beforeDate);
        log.info("Deleted {} old audit logs (older than {} days)", deletedCount, daysToKeep);
    }

    @Override
    public AuditLogQueryResponse queryLogs(String keyword,
                                           String action,
                                           String module,
                                           String operator,
                                           LocalDateTime start,
                                           LocalDateTime end,
                                           Boolean success) {
        return auditLogQueryService.queryLogs(keyword, action, module, operator, start, end, success);
    }

    @Override
    public AuditLogQueryResponse queryMyOperationLogs(String username,
                                                      String keyword,
                                                      String action,
                                                      String module,
                                                      LocalDateTime start,
                                                      LocalDateTime end,
                                                      Boolean success) {
        return auditLogQueryService.queryLogsForActor(username, keyword, action, module, start, end, success);
    }

    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String value) {
            return value;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON", e);
            return obj.toString();
        }
    }

    @Builder
    @Data
    public static class AuditLogEntry {
        private String userId;
        private String username;
        private String action;
        private String entityType;
        private String entityId;
        private String description;
        private String oldValue;
        private String newValue;
        private Boolean success;
        private String errorMessage;
    }
}
