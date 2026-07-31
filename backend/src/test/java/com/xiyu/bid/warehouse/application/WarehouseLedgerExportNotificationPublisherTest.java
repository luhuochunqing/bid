package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * WarehouseLedgerExportNotificationPublisher 单元测试。
 *
 * <p>台账导出完成通知与 WarehouseExportNotificationPublisher 对齐：
 * title 承载简短摘要，body 承载详情（此前全部塞 title，企微 128 字符截断丢文案）。
 */
@ExtendWith(MockitoExtension.class)
class WarehouseLedgerExportNotificationPublisherTest {

    @Mock
    private WeComPushService weComPushService;

    @Test
    void publish_shouldPushWeComWithShortTitleAndDetailBody() {
        WarehouseLedgerExportNotificationPublisher publisher =
                new WarehouseLedgerExportNotificationPublisher(weComPushService);

        WarehouseExportTaskEntity task = new WarehouseExportTaskEntity();
        task.setId(21L);
        task.setCreatedBy(200L);
        task.setCompletedAt(LocalDateTime.of(2026, 7, 31, 16, 0, 0));

        WarehouseLedgerExportAppService.ExportRequest req =
                new WarehouseLedgerExportAppService.ExportRequest("all_in_use", null, null, null);

        publisher.publish(task, 88, req, 3200);

        ArgumentCaptor<NotificationCreatedEvent> captor = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(weComPushService).pushForRecipient(captor.capture(), eq(200L));

        NotificationCreatedEvent event = captor.getValue();
        assertThat(event.title()).isEqualTo("📤 仓库台账导出 — 完成");
        assertThat(event.body())
                .contains("仓库台账导出包-20260731_160000.zip")
                .contains("88 条")
                .contains("3 秒")
                .contains("范围：全部使用中");
        assertThat(event.type()).isEqualTo("WAREHOUSE_LEDGER_EXPORT");
        assertThat(event.sourceEntityType()).isEqualTo("WAREHOUSE_LEDGER_EXPORT_TASK");
        assertThat(event.sourceEntityId()).isEqualTo(21L);
    }
}
