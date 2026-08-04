package com.xiyu.bid.performance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.performance.infrastructure.PerformanceAttachmentTypeLabels;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业绩合订本导出完成通知发布器：构建结果摘要 JSON、格式化筛选摘要、直推企微完成通知。
 * 对标 {@code WarehouseExportNotificationPublisher}，拆出来以保持 AppService 行数预算。
 *
 * <p>投递方式：与 {@code WarehouseExportNotificationPublisher} 同构，构造 {@link NotificationCreatedEvent}
 * 直接调用 {@link WeComPushService#pushForRecipient}。此前使用 publishEvent(notificationId=null)，
 * 但唯一监听器 NotificationDeliveryTaskListener 对 null notificationId 直接 return，
 * 事件无人消费（通知从未发出），故改为直推。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceBundleExportNotificationPublisher {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final ObjectMapper objectMapper;
    private final WeComPushService weComPushService;

    /**
     * 构建结果摘要 JSON，供 task.result_summary 字段存储。
     */
    public String buildResultSummaryJson(int totalCount, long wordBytes,
                                          Set<String> attachmentTypes, long elapsedMs,
                                          String filterSummary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCount", totalCount);
        map.put("wordBytes", wordBytes);
        map.put("elapsedMs", elapsedMs);
        map.put("filterSummary", filterSummary);
        map.put("attachmentTypes", formatAttachmentTypes(attachmentTypes));
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String formatAttachmentTypes(Set<String> types) {
        if (types == null || types.isEmpty()) return "全部附件";
        String labels = types.stream()
                .map(PerformanceAttachmentTypeLabels::labelOf)
                .sorted()
                .collect(Collectors.joining("、"));
        return "部分附件（" + labels + "）";
    }

    /**
     * 构建筛选摘要文本。
     */
    public static String buildFilterSummary(String keyword, String customerType,
                                             String groupCompany, Set<String> attachmentTypes) {
        List<String> tags = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) tags.add("关键词:" + keyword);
        if (customerType != null && !customerType.isBlank()) tags.add("客户类型:" + customerType);
        if (groupCompany != null && !groupCompany.isBlank()) tags.add("集团:" + groupCompany);
        if (attachmentTypes != null && !attachmentTypes.isEmpty()) {
            tags.add("附件类型:" + attachmentTypes.stream()
                    .map(PerformanceAttachmentTypeLabels::labelOf)
                    .collect(Collectors.joining("、")));
        }
        return tags.isEmpty() ? "全部" : "全部（" + String.join("，", tags) + "）";
    }

    /**
     * 直推完成通知。
     */
    public void publish(PerformanceExportTaskEntity task, int totalCount,
                        long wordBytes, long elapsedMs, String filterSummary) {
        try {
            String title = "📤 业绩合订本导出 — 完成";
            String body = String.format(
                    "业绩合订本_%s.docx（%d 条记录；文件大小 %.2f MB；耗时 %d 秒；%s）",
                    task.getCompletedAt() != null ? task.getCompletedAt().format(TS_FMT) : "",
                    totalCount,
                    wordBytes / 1024.0 / 1024.0,
                    elapsedMs / 1000,
                    filterSummary);
            NotificationCreatedEvent event = new NotificationCreatedEvent(
                    null,
                    List.of(task.getCreatedBy()),
                    "PERFORMANCE_BUNDLE_EXPORT",
                    title,
                    body,
                    "PERFORMANCE_BUNDLE_EXPORT_TASK",
                    task.getId(),
                    null
            );
            weComPushService.pushForRecipient(event, task.getCreatedBy());
            log.info("业绩合订本导出完成通知已发布: taskId={}, totalCount={}, elapsedMs={}",
                    task.getId(), totalCount, elapsedMs);
        } catch (RuntimeException e) {
            log.warn("发布业绩合订本导出完成通知失败: taskId={}, error={}",
                    task.getId(), e.getMessage());
        }
    }
}
