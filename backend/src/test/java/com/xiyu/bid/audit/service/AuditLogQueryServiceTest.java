package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.dto.AuditLogQueryResponse;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditLogQueryServiceTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogQueryService service = new AuditLogQueryService(
            auditLogRepository,
            userRepository,
            new AuditLogItemMapper()
    );

    @Test
    void queryLogs_shouldUseRepositorySearchAndFilterMappedModule() {
        AuditLog projectLog = log(1L, "1", "project-user", "UPDATE", "Project", "100");
        AuditLog tenderLog = log(2L, "2", "tender-user", "UPDATE", "Tender", "200");
        User projectUser = User.builder()
                .id(1L)
                .username("project-user")
                .fullName("项目用户")
                .role(User.Role.ADMIN)
                .build();
        when(auditLogRepository.searchLogs("状态", "UPDATE", null, null, null, true))
                .thenReturn(List.of(projectLog, tenderLog));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(projectUser));
        when(userRepository.findByUsername("tender-user")).thenReturn(Optional.empty());

        AuditLogQueryResponse response = service.queryLogs("状态", "UPDATE", "project", null, null, null, true);

        assertEquals(1, response.getItems().size());
        assertEquals("project", response.getItems().get(0).getModule());
        assertEquals("项目用户", response.getItems().get(0).getOperator());
        assertEquals(1, response.getSummary().getTotalCount());
        verify(auditLogRepository).searchLogs("状态", "UPDATE", null, null, null, true);
    }

    @Test
    void queryLogs_shouldResolveBlankUserIdByUsername() {
        AuditLog log = log(3L, " ", "blank-user", "CREATE", "Qualification", "300");
        User user = User.builder()
                .id(3L)
                .username("blank-user")
                .fullName("空白用户")
                .role(User.Role.ADMIN)
                .build();
        when(auditLogRepository.searchLogs(null, null, null, null, null, null))
                .thenReturn(List.of(log));
        when(userRepository.findByUsername("blank-user")).thenReturn(Optional.of(user));

        AuditLogQueryResponse response = service.queryLogs(null, null, null, null, null, null, null);

        assertEquals("空白用户", response.getItems().get(0).getOperator());
        verify(userRepository).findByUsername("blank-user");
    }

    @Test
    void queryLogsForActor_shouldRestrictRepositorySearchToCurrentUser() {
        AuditLog ownLog = log(4L, "42", "xiaowang", "DELETE", "Task", "400");
        User user = User.builder()
                .id(42L)
                .username("xiaowang")
                .fullName("小王")
                .role(User.Role.STAFF)
                .build();
        when(userRepository.findByUsername("xiaowang")).thenReturn(Optional.of(user));
        when(auditLogRepository.searchLogsForActor("删除", "DELETE", "xiaowang", "42", null, null, true))
                .thenReturn(List.of(ownLog));
        when(userRepository.findAllById(List.of(42L))).thenReturn(List.of(user));

        AuditLogQueryResponse response = service.queryLogsForActor(
                "xiaowang",
                "删除",
                "DELETE",
                "task",
                null,
                null,
                true
        );

        assertEquals(1, response.getItems().size());
        assertEquals("小王", response.getItems().get(0).getOperator());
        assertEquals("task", response.getItems().get(0).getModule());
        verify(auditLogRepository).searchLogsForActor("删除", "DELETE", "xiaowang", "42", null, null, true);
    }

    @Test
    void queryLogsForActor_shouldReturnEmptyWhenActorMissing() {
        AuditLogQueryResponse response = service.queryLogsForActor(
                " ",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(0, response.getItems().size());
        assertEquals(0, response.getSummary().getTotalCount());
        verifyNoInteractions(auditLogRepository, userRepository);
    }

    private AuditLog log(Long id, String userId, String username, String action, String entityType, String entityId) {
        return AuditLog.builder()
                .id(id)
                .userId(userId)
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description("状态更新")
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
