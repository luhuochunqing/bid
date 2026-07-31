package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 仓库台账导出（19 列精简版）完成通知发布器。
 *
 * <p>与 {@link WarehouseExportNotificationPublisher} 对齐：title 承载简短摘要，body 承载详情，
 * 构造 {@link NotificationCreatedEvent} 直推企微（publishEvent(notificationId=null) 无人消费，
 * 详见 Export 对应类注释）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseLedgerExportNotificationPublisher {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final WeComPushService weComPushService;

    public void publish(WarehouseExportTaskEntity task, int totalCount,
                        WarehouseLedgerExportAppService.ExportRequest req, long elapsedMs) {
        try {
            String title = "📤 仓库台账导出 — 完成";
            String body = String.format("仓库台账导出包-%s.zip（%d 条；%d 秒；范围：%s）",
                    task.getCompletedAt() != null ? task.getCompletedAt().format(TS_FMT) : "",
                    totalCount, elapsedMs / 1000, scopeLabel(req));
            NotificationCreatedEvent event = new NotificationCreatedEvent(
                    null, List.of(task.getCreatedBy()),
                    "WAREHOUSE_LEDGER_EXPORT", title, body,
                    "WAREHOUSE_LEDGER_EXPORT_TASK", task.getId(), null);
            weComPushService.pushForRecipient(event, task.getCreatedBy());
            log.info("台账导出完成通知已发布: taskId={}, totalCount={}, elapsedMs={}",
                    task.getId(), totalCount, elapsedMs);
        } catch (RuntimeException e) {
            log.warn("台账导出通知发布失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    static String scopeLabel(WarehouseLedgerExportAppService.ExportRequest req) {
        return switch (req.scope() == null ? "filter" : req.scope()) {
            case "ids" -> "当前勾选";
            case "all_in_use" -> "全部使用中";
            default -> "当前筛选";
        };
    }
}
