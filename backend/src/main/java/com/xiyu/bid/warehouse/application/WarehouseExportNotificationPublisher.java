package com.xiyu.bid.warehouse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportZipBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仓库 ZIP 导出包完成通知发布器：构建结果摘要 JSON、格式化筛选摘要、发布 NotificationCreatedEvent。
 * 拆出来以保持 WarehouseExportAppService 行数预算。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseExportNotificationPublisher {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public String buildResultSummaryJson(int totalCount, WarehouseExportZipBuilder.ZipBuildResult zip,
                                          WarehouseFilterDTO filterDTO, long elapsedMs,
                                          WarehouseAttachmentExportScope attachmentScope) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCount", totalCount);
        map.put("xlsxBytes", zip.stats().xlsxBytes);
        map.put("zipBytes", zip.totalBytes());
        map.put("propertyCertCount", zip.stats().propertyCertCount);
        map.put("invoiceCount", zip.stats().invoiceCount);
        map.put("photosCount", zip.stats().photosCount);
        map.put("elapsedMs", elapsedMs);
        map.put("filterSummary", buildFilterSummary(filterDTO));
        map.put("attachmentScope", formatAttachmentScope(attachmentScope));
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String formatAttachmentScope(WarehouseAttachmentExportScope scope) {
        if (scope instanceof WarehouseAttachmentExportScope.All) {
            return "全部附件";
        }
        if (scope instanceof WarehouseAttachmentExportScope.Partial partial) {
            String typeNames = partial.types().stream()
                    .map(WarehouseAttachmentType::displayName)
                    .sorted()
                    .collect(Collectors.joining("、"));
            return "部分附件（" + typeNames + "）";
        }
        return scope.toString();
    }

    public static String buildFilterSummary(WarehouseFilterDTO filterDTO) {
        if (filterDTO == null) return "勾选模式";
        List<String> tags = new ArrayList<>();
        if (filterDTO.keyword() != null && !filterDTO.keyword().isBlank()) tags.add("关键词:" + filterDTO.keyword());
        if (filterDTO.types() != null && !filterDTO.types().isEmpty()) tags.add("类型:" + filterDTO.types());
        if (filterDTO.statuses() != null && !filterDTO.statuses().isEmpty()) tags.add("状态:" + filterDTO.statuses());
        if (filterDTO.provinces() != null && !filterDTO.provinces().isEmpty()) tags.add("省份:" + String.join(",", filterDTO.provinces()));
        if (filterDTO.endDateFrom() != null || filterDTO.endDateTo() != null) {
            tags.add("到期:" + (filterDTO.endDateFrom() == null ? "..." : filterDTO.endDateFrom())
                    + " ~ " + (filterDTO.endDateTo() == null ? "..." : filterDTO.endDateTo()));
        }
        if (filterDTO.hasPropertyCert() != null && filterDTO.hasPropertyCert()) tags.add("有产权证");
        if (filterDTO.hasInvoice() != null && filterDTO.hasInvoice()) tags.add("有发票");
        if (filterDTO.hasPhotos() != null && filterDTO.hasPhotos()) tags.add("有照片");
        if (filterDTO.hasLeaseContract() != null && filterDTO.hasLeaseContract()) tags.add("有租赁合同");
        if (filterDTO.contactPersonKeyword() != null) tags.add("联系人:" + filterDTO.contactPersonKeyword());
        return tags.isEmpty() ? "全部" : "全部（" + String.join("，", tags) + "）";
    }

    public void publish(WarehouseExportTaskEntity task, int totalCount,
                        WarehouseExportZipBuilder.ZipBuildResult zip,
                        WarehouseFilterDTO filterDTO, long elapsedMs,
                        DateTimeFormatter tsFmt,
                        WarehouseAttachmentExportScope attachmentScope) {
        try {
            String title = "📤 仓库信息导出包 — 完成";
            String body = String.format(
                    "仓库信息导出包_%s.zip（%d 条，含 %d 份产权证 / %d 份发票 / %d 张照片 / %d 份租赁合同；耗时 %d 秒；%s；附件范围：%s）",
                    task.getCompletedAt() != null ? task.getCompletedAt().format(tsFmt) : "",
                    totalCount,
                    zip.stats().propertyCertCount, zip.stats().invoiceCount, zip.stats().photosCount, zip.stats().leaseContractCount,
                    elapsedMs / 1000,
                    buildFilterSummary(filterDTO),
                    formatAttachmentScope(attachmentScope));
            eventPublisher.publishEvent(new NotificationCreatedEvent(
                    null,
                    List.of(task.getCreatedBy()),
                    "WAREHOUSE_EXPORT",
                    title,
                    "WAREHOUSE_EXPORT_TASK",
                    task.getId()
            ));
            log.info("仓库导出完成通知已发布: taskId={}, totalCount={}, elapsedMs={}",
                    task.getId(), totalCount, elapsedMs);
        } catch (RuntimeException e) {
            log.warn("发布仓库导出完成通知失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }
}
