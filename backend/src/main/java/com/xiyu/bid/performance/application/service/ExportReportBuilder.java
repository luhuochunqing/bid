package com.xiyu.bid.performance.application.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 导出报告生成器（纯函数）。
 *
 * <p>生成 _导出报告.txt 文本内容，包含导出时间、业绩数、附件类型筛选、
 * 附件总数、成功/失败计数与失败清单。不依赖 Spring，可独立单测。
 */
public final class ExportReportBuilder {

    /** 失败附件记录（业绩名 / fileType code / 文件名 / 失败原因）。 */
    public record FailedAttachmentRecord(
            String performanceName,
            String fileType,
            String fileName,
            String failureReason
    ) {}

    /** 7 种附件类型的规范顺序与中文名映射（与 AttachmentFilter.ALLOWED_TYPES 对齐）。 */
    private static final List<java.util.Map.Entry<String, String>> TYPE_LABELS = List.of(
            java.util.Map.entry("CONTRACT_AGREEMENT", "合同协议"),
            java.util.Map.entry("MALL_SCREENSHOT", "商城截图"),
            java.util.Map.entry("SOE_DIRECTORY", "央企名录"),
            java.util.Map.entry("RELATIONSHIP_PROOF", "关系证明"),
            java.util.Map.entry("CATEGORY_PAGE", "品类页"),
            java.util.Map.entry("BID_NOTICE", "中标通知书"),
            java.util.Map.entry("OTHER", "其他附件")
    );

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExportReportBuilder() {}

    /**
     * 构建导出报告文本。
     *
     * @param exportTime 导出时间
     * @param performanceCount 导出业绩数
     * @param attachmentTypes 附件类型筛选；null 或空 = 全部
     * @param totalAttachments 附件总数
     * @param successCount 成功数
     * @param failures 失败附件清单
     * @return UTF-8 报告文本
     */
    public static String build(LocalDateTime exportTime,
                                int performanceCount,
                                Set<String> attachmentTypes,
                                int totalAttachments,
                                int successCount,
                                List<FailedAttachmentRecord> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("导出报告\n");
        sb.append("========\n");
        sb.append("导出时间: ").append(exportTime.format(FMT)).append("\n");
        sb.append("导出业绩数: ").append(performanceCount).append("\n");
        sb.append("附件类型筛选: ").append(formatTypes(attachmentTypes)).append("\n");
        sb.append("附件总数: ").append(totalAttachments).append("\n");
        sb.append("成功: ").append(successCount).append("\n");
        int failCount = failures == null ? 0 : failures.size();
        sb.append("失败: ").append(failCount).append("\n");

        if (failCount > 0) {
            sb.append("\n失败清单:\n");
            for (int i = 0; i < failures.size(); i++) {
                FailedAttachmentRecord f = failures.get(i);
                sb.append(i + 1).append(". ")
                        .append("业绩「").append(f.performanceName()).append("」/ ")
                        .append(labelOf(f.fileType())).append(" / ")
                        .append(f.fileName())
                        .append(" → 读取失败: ").append(f.failureReason())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private static String formatTypes(Set<String> types) {
        if (types == null || types.isEmpty()) {
            return "全部";
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : TYPE_LABELS) {
            if (types.contains(entry.getKey())) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private static String labelOf(String fileType) {
        for (var entry : TYPE_LABELS) {
            if (entry.getKey().equals(fileType)) {
                return entry.getValue();
            }
        }
        return fileType;
    }
}
