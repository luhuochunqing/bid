// Input: WarehouseEntity + User + NotificationAppService mock
// Output: 仓库到期扫描主流程单测（EXPIRING/EXPIRED/CLOSED/IN_USE/去重/接收人/admin 回归/contactDisplay 回归）
// Pos: test/java/.../warehouse/service - 应用服务单测
package com.xiyu.bid.warehouse.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseExpiryScanTaskTest {

    @Mock private WarehouseRepository warehouseRepo;
    @Mock private UserRepository userRepo;
    @Mock private NotificationApplicationService notificationService;
    @Mock private NotificationRepository notificationRepo;

    private WarehouseExpiryScanTask task;

    @BeforeEach
    void setUp() {
        task = new WarehouseExpiryScanTask(warehouseRepo, userRepo, notificationService, notificationRepo);
        lenient().when(notificationService.createNotification(any(), any()))
                .thenReturn(DispatchResult.validWithId(1L));
        // contactDisplay 构建在仓库遍历之前，对所有仓库的 contactPerson 都会调用 findByFullName
        lenient().when(userRepo.findByFullName(any())).thenReturn(List.of());
    }

    private User user(Long id, String fullName, String username) {
        return User.builder()
                .id(id)
                .fullName(fullName)
                .username(username)
                .enabled(true)
                .build();
    }

    private WarehouseEntity warehouse(Long id, String name, LocalDate endDate, WarehouseStatus status) {
        return WarehouseEntity.builder()
                .id(id)
                .name(name)
                .type(WarehouseType.SELF_OPERATED)
                .region("北京")
                .province("北京")
                .address("顺义")
                .area(BigDecimal.TEN)
                .contactPerson("王区长")
                .startDate(LocalDate.now().minusYears(1))
                .endDate(endDate)
                .lessor("出租方")
                .lessee("承租方")
                .hasPropertyCert(true)
                .hasInvoice(true)
                .hasPhotos(true)
                .hasLeaseContract(true)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("核心回归：接收人仅含 admin 角色，应发送到期提醒通知（修复前 return 0 不发）")
    void shouldSendWarning_WhenOnlyAdminRoleUserExists() {
        // given: 环境中只有 admin 角色用户（无 /bidAdmin 或 bid-TeamLeader）
        User admin = user(1L, "管理员", "admin");
        when(userRepo.findEnabledByRoleProfileCodes(anyList())).thenReturn(List.of(admin));

        // given: 即将到期仓库（15 天后）
        WarehouseEntity wh = warehouse(10L, "北京顺义中央仓", LocalDate.now().plusDays(15), WarehouseStatus.IN_USE);
        when(warehouseRepo.findAll()).thenReturn(List.of(wh));
        when(notificationRepo.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(false);

        // when
        int count = task.processScan();

        // then
        assertThat(count).isEqualTo(1);
        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), any());
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.sourceEntityType()).isEqualTo("WAREHOUSE_EXPIRY_WARNING");
        assertThat(req.sourceEntityId()).isEqualTo(10L);
        assertThat(req.recipientUserIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("EXPIRED 仓库：发送 WAREHOUSE_EXPIRED_WARNING 通知")
    void shouldSendExpiredNotification_WhenWarehouseExpired() {
        User admin = user(1L, "管理员", "admin");
        when(userRepo.findEnabledByRoleProfileCodes(anyList())).thenReturn(List.of(admin));

        WarehouseEntity wh = warehouse(10L, "上海浦东中央仓", LocalDate.now().minusDays(7), WarehouseStatus.IN_USE);
        when(warehouseRepo.findAll()).thenReturn(List.of(wh));
        when(notificationRepo.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(false);

        int count = task.processScan();

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), any());
        assertThat(captor.getValue().sourceEntityType()).isEqualTo("WAREHOUSE_EXPIRED_WARNING");
    }

    @Test
    @DisplayName("CLOSED 仓库：跳过，不发通知")
    void shouldSkipClosedWarehouse() {
        User admin = user(1L, "管理员", "admin");
        when(userRepo.findEnabledByRoleProfileCodes(anyList())).thenReturn(List.of(admin));

        WarehouseEntity wh = warehouse(10L, "已关仓仓库", LocalDate.now().minusDays(1), WarehouseStatus.CLOSED);
        when(warehouseRepo.findAll()).thenReturn(List.of(wh));

        int count = task.processScan();

        assertThat(count).isEqualTo(0);
        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("IN_USE 仓库（远未到期，>30 天）：不发通知")
    void shouldNotSendNotification_WhenWarehouseInUseAndFarFromExpiry() {
        User admin = user(1L, "管理员", "admin");
        when(userRepo.findEnabledByRoleProfileCodes(anyList())).thenReturn(List.of(admin));

        WarehouseEntity wh = warehouse(10L, "正常仓库", LocalDate.now().plusDays(100), WarehouseStatus.IN_USE);
        when(warehouseRepo.findAll()).thenReturn(List.of(wh));

        int count = task.processScan();

        assertThat(count).isEqualTo(0);
        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("接收人为空：return 0，不发通知")
    void shouldReturnZero_WhenNoRecipients() {
        when(userRepo.findEnabledByRoleProfileCodes(anyList())).thenReturn(List.of());

        WarehouseEntity wh = warehouse(10L, "到期仓库", LocalDate.now().minusDays(1), WarehouseStatus.IN_USE);
        when(warehouseRepo.findAll()).thenReturn(List.of(wh));

        int count = task.processScan();

        assertThat(count).isEqualTo(0);
        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("24h 内已发：跳过（去重）")
    void shouldSkip_WhenAlreadySentWithin24Hours() {
        User admin = user(1L, "管理员", "admin");
        when(userRepo.findEnabledByRoleProfileCodes(anyList())).thenReturn(List.of(admin));

        WarehouseEntity wh = warehouse(10L, "到期仓库", LocalDate.now().plusDays(15), WarehouseStatus.EXPIRING);
        when(warehouseRepo.findAll()).thenReturn(List.of(wh));
        when(notificationRepo.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(true);

        int count = task.processScan();

        assertThat(count).isEqualTo(0);
        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("核心回归：contactDisplay 正确传入，通知正文应含\"姓名（工号）\"格式")
    void shouldPassContactDisplay_WhenSendingWarning() {
        User admin = user(1L, "管理员", "admin");
        User contact = user(2L, "王区长", "10086");
        when(userRepo.findEnabledByRoleProfileCodes(anyList())).thenReturn(List.of(admin));
        when(userRepo.findByFullName("王区长")).thenReturn(List.of(contact));

        WarehouseEntity wh = warehouse(10L, "北京顺义中央仓", LocalDate.now().plusDays(15), WarehouseStatus.IN_USE);
        when(warehouseRepo.findAll()).thenReturn(List.of(wh));
        when(notificationRepo.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(false);

        task.processScan();

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), any());
        // 修复前传 Map.of()，正文只显示"王区长"；修复后应显示"王区长（10086）"
        assertThat(captor.getValue().body()).contains("王区长（10086）");
    }
}
