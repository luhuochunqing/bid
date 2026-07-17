package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 仓库台账导出（19 列精简版）完成通知发布器。
 *
 * <p>与 {@link WarehouseExportNotificationPublisher} 区分：台账导出无附件统计，
 * 通知文案格式不同。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseLedgerExportNotificationPublisher {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ApplicationEventPublisher eventPublisher;

    public void publish(WarehouseExportTaskEntity task, int totalCount,
                        WarehouseLedgerExportAppService.ExportRequest req, long elapsedMs) {
        try {
            String title = "📤 仓库台账导出 — 完成";
            String body = String.format("仓库台账导出包-%s.zip（%d 条；%d 秒；范围：%s）",
                    task.getCompletedAt() != null ? task.getCompletedAt().format(TS_FMT) : "",
                    totalCount, elapsedMs / 1000, scopeLabel(req));
            eventPublisher.publishEvent(new NotificationCreatedEvent(
                    null, List.of(task.getCreatedBy()),
                    "WAREHOUSE_LEDGER_EXPORT", title,
                    "WAREHOUSE_LEDGER_EXPORT_TASK", task.getId()));
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
