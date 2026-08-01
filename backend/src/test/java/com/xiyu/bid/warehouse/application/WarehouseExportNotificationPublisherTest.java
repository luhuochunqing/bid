package com.xiyu.bid.warehouse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportZipBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * WarehouseExportNotificationPublisher 单元测试。
 *
 * <p>覆盖企微外发链路修复（原为 publishEvent(notificationId=null) 无人消费的死链）：
 * 完成通知直推企微，title 承载简短摘要，body 承载导出详情（文件名/条数/附件统计/耗时/范围）。
 */
@ExtendWith(MockitoExtension.class)
class WarehouseExportNotificationPublisherTest {

    @Mock
    private WeComPushService weComPushService;

    @Test
    void publish_shouldPushWeComWithTitleAndBody() {
        WarehouseExportNotificationPublisher publisher =
                new WarehouseExportNotificationPublisher(new ObjectMapper(), weComPushService);

        WarehouseExportTaskEntity task = new WarehouseExportTaskEntity();
        task.setId(11L);
        task.setCreatedBy(100L);
        task.setCompletedAt(LocalDateTime.of(2026, 7, 31, 15, 30, 0));

        WarehouseExportZipBuilder.ZipStats stats = new WarehouseExportZipBuilder.ZipStats();
        stats.propertyCertCount = 3;
        stats.invoiceCount = 2;
        stats.photosCount = 5;
        stats.leaseContractCount = 1;
        WarehouseExportZipBuilder.ZipBuildResult zip =
                new WarehouseExportZipBuilder.ZipBuildResult(Path.of("/tmp/x.zip"), 1024L, stats);

        publisher.publish(task, 42, zip, null, 6500, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"),
                new WarehouseAttachmentExportScope.All());

        ArgumentCaptor<NotificationCreatedEvent> captor = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(weComPushService).pushForRecipient(captor.capture(), eq(100L));

        NotificationCreatedEvent event = captor.getValue();
        assertThat(event.title()).isEqualTo("📤 仓库信息导出包 — 完成");
        assertThat(event.body())
                .contains("仓库信息导出包_20260731_153000.zip")
                .contains("42 条")
                .contains("3 份产权证")
                .contains("2 份发票")
                .contains("5 张照片")
                .contains("1 份租赁合同")
                .contains("耗时 6 秒")
                .contains("附件范围：全部附件");
        assertThat(event.type()).isEqualTo("WAREHOUSE_EXPORT");
        assertThat(event.sourceEntityType()).isEqualTo("WAREHOUSE_EXPORT_TASK");
        assertThat(event.sourceEntityId()).isEqualTo(11L);
        assertThat(event.recipientUserIds()).containsExactly(100L);
    }

    @Test
    void publish_shouldNotPropagatePushFailure() {
        WarehouseExportNotificationPublisher publisher =
                new WarehouseExportNotificationPublisher(new ObjectMapper(), weComPushService);

        WarehouseExportTaskEntity task = new WarehouseExportTaskEntity();
        task.setId(12L);
        task.setCreatedBy(100L);

        WarehouseExportZipBuilder.ZipStats stats = new WarehouseExportZipBuilder.ZipStats();
        WarehouseExportZipBuilder.ZipBuildResult zip =
                new WarehouseExportZipBuilder.ZipBuildResult(Path.of("/tmp/x.zip"), 1L, stats);

        org.mockito.Mockito.doThrow(new RuntimeException("wecom down"))
                .when(weComPushService).pushForRecipient(org.mockito.ArgumentMatchers.any(), eq(100L));

        // 推送失败只记 warn，不向导出主流程抛异常
        publisher.publish(task, 1, zip, null, 1000, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"),
                new WarehouseAttachmentExportScope.All());

        verify(weComPushService).pushForRecipient(org.mockito.ArgumentMatchers.any(), eq(100L));
    }
}
