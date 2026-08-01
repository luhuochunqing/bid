package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WarehouseImportTaskStateService 单元测试（企微通知部分）。
 *
 * <p>导入完成通知直推企微，body 透传"成功/失败/附件"统计
 * （原为 publishEvent(notificationId=null) 无人消费的死链 + dead body 变量）。
 */
@ExtendWith(MockitoExtension.class)
class WarehouseImportTaskStateServiceTest {

    @Mock
    private WarehouseImportTaskRepository importTaskRepo;

    @Mock
    private WeComPushService weComPushService;

    @InjectMocks
    private WarehouseImportTaskStateService stateService;

    @Test
    void complete_shouldPushWeComWithBodyStats() {
        WarehouseImportTaskEntity task = new WarehouseImportTaskEntity();
        task.setId(31L);
        task.setCreatedBy(300L);
        when(importTaskRepo.findById(31L)).thenReturn(Optional.of(task));

        stateService.complete(31L, 5, List.of(), null, null);

        ArgumentCaptor<NotificationCreatedEvent> captor = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(weComPushService).pushForRecipient(captor.capture(), eq(300L));

        NotificationCreatedEvent event = captor.getValue();
        assertThat(event.title()).isEqualTo("📥 仓库信息导入 — 完成");
        assertThat(event.body()).isEqualTo("成功 5 条 | 失败 0 条 | 关联附件 0 个 | 未匹配附件 0 个");
        assertThat(event.type()).isEqualTo("WAREHOUSE_IMPORT");
        assertThat(event.sourceEntityType()).isEqualTo("WAREHOUSE_IMPORT_TASK");
        assertThat(event.sourceEntityId()).isEqualTo(31L);
    }

    @Test
    void complete_shouldMarkFailureInTitleWhenErrorsExist() {
        WarehouseImportTaskEntity task = new WarehouseImportTaskEntity();
        task.setId(32L);
        task.setCreatedBy(300L);
        when(importTaskRepo.findById(32L)).thenReturn(Optional.of(task));

        stateService.complete(32L, 3, List.of(
                new WarehouseImportAppService.RowError(2, "缺少必填列")), null, null);

        ArgumentCaptor<NotificationCreatedEvent> captor = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(weComPushService).pushForRecipient(captor.capture(), eq(300L));

        NotificationCreatedEvent event = captor.getValue();
        assertThat(event.title()).isEqualTo("📥 仓库信息导入 — 完成（含失败）");
        assertThat(event.body()).contains("成功 3 条").contains("失败 1 条");
    }
}
