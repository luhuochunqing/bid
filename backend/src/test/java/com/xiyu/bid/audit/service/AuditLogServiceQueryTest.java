package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.dto.AuditLogQueryResponse;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceQueryTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void queryLogsUsesRepositorySearchLogsInsteadOfLoadingAllRows() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        AuditLog auditLog = AuditLog.builder()
                .id(100L)
                .userId("user-42")
                .username("audit-admin")
                .action("UPDATE")
                .entityType("PROJECT")
                .entityId("P-100")
                .description("Updated project status")
                .ipAddress("127.0.0.1")
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();

        when(auditLogRepository.searchLogs("status", "update", "audit-admin", start, end, true))
                .thenReturn(List.of(auditLog));
        lenient().when(auditLogRepository.findAll(any(Sort.class))).thenReturn(List.of(auditLog));
        when(userRepository.findByUsername("audit-admin")).thenReturn(Optional.empty());

        AuditLogQueryService auditLogService = new AuditLogQueryService(
                auditLogRepository,
                userRepository,
                new AuditLogItemMapper()
        );

        AuditLogQueryResponse response = auditLogService.queryLogs(
                "status",
                "update",
                "project",
                "audit-admin",
                start,
                end,
                true
        );

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getActionType()).isEqualTo("update");
        verify(auditLogRepository).searchLogs("status", "update", "audit-admin", start, end, true);
        verify(auditLogRepository, never()).findAll(any(Sort.class));
    }
}
