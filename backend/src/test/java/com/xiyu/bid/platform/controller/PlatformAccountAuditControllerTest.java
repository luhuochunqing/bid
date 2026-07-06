// Input: PlatformAccountAuditController + mock repository/mapper
// Output: 审计日志查询与 VIEW_PASSWORD 过滤验证
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.controller;

import com.xiyu.bid.audit.dto.AuditLogItemDTO;
import com.xiyu.bid.audit.service.AuditLogItemMapper;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * CO-522: 验证 {@link PlatformAccountAuditController} 的查询与过滤逻辑。
 * 重点关注 VIEW_PASSWORD 日志被过滤（不暴露给前端）。
 */
@ExtendWith(MockitoExtension.class)
class PlatformAccountAuditControllerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogItemMapper itemMapper;

    @InjectMocks
    private PlatformAccountAuditController controller;

    @Test
    @DisplayName("查询返回 PlatformAccount 实体类型的日志，按 entityId 匹配")
    void getAccountAuditLogs_queriesByEntityTypeAndId() {
        AuditLog createLog = AuditLog.builder().id(1L).action("CREATE")
                .entityType("PlatformAccount").entityId("42")
                .userId("7").username("admin").build();
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(eq("PlatformAccount"), eq("42")))
                .thenReturn(List.of(createLog));
        when(itemMapper.toItemDto(any(), any())).thenReturn(
                AuditLogItemDTO.builder().id(1L).actionType("create").build());

        ResponseEntity<ApiResponse<List<AuditLogItemDTO>>> resp = controller.getAccountAuditLogs(42L);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getData()).hasSize(1);
        assertThat(resp.getBody().getData().get(0).getActionType()).isEqualTo("create");
    }

    @Test
    @DisplayName("VIEW_PASSWORD 日志被过滤，不暴露给前端")
    void getAccountAuditLogs_filtersViewPasswordAction() {
        AuditLog createLog = AuditLog.builder().id(1L).action("CREATE")
                .entityType("PlatformAccount").entityId("42")
                .userId("7").username("admin").build();
        AuditLog viewPwdLog = AuditLog.builder().id(2L).action("VIEW_PASSWORD")
                .entityType("PlatformAccount").entityId("42")
                .userId("7").username("admin").build();
        AuditLog updateLog = AuditLog.builder().id(3L).action("UPDATE")
                .entityType("PlatformAccount").entityId("42")
                .userId("7").username("admin").build();
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(any(), any()))
                .thenReturn(List.of(createLog, viewPwdLog, updateLog));
        when(itemMapper.toItemDto(any(), any())).thenAnswer(inv ->
                AuditLogItemDTO.builder().id(((AuditLog) inv.getArgument(0)).getId()).build());

        ResponseEntity<ApiResponse<List<AuditLogItemDTO>>> resp = controller.getAccountAuditLogs(42L);

        List<AuditLogItemDTO> data = resp.getBody().getData();
        assertThat(data).hasSize(2);  // VIEW_PASSWORD 被过滤
        assertThat(data.stream().map(AuditLogItemDTO::getId)).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("系统自动触发的日志（system/scheduler/AUTO_*）被过滤")
    void getAccountAuditLogs_filtersSystemTriggered() {
        AuditLog userLog = AuditLog.builder().id(1L).action("CREATE")
                .userId("7").username("admin").build();
        AuditLog systemLog = AuditLog.builder().id(2L).action("CREATE")
                .userId("system").username("system").build();
        AuditLog schedulerLog = AuditLog.builder().id(3L).action("AUTO_SYNC")
                .userId("scheduler").username("scheduler").build();
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(any(), any()))
                .thenReturn(List.of(userLog, systemLog, schedulerLog));
        when(itemMapper.toItemDto(any(), any())).thenAnswer(inv ->
                AuditLogItemDTO.builder().id(((AuditLog) inv.getArgument(0)).getId()).build());

        ResponseEntity<ApiResponse<List<AuditLogItemDTO>>> resp = controller.getAccountAuditLogs(42L);

        List<AuditLogItemDTO> data = resp.getBody().getData();
        assertThat(data).hasSize(1);
        assertThat(data.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("无日志时返回空列表")
    void getAccountAuditLogs_emptyReturnsEmptyList() {
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(any(), any()))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<List<AuditLogItemDTO>>> resp = controller.getAccountAuditLogs(99L);

        assertThat(resp.getBody().getData()).isEmpty();
    }
}
