package com.xiyu.bid.brandauth.manufacturer.application.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 品牌授权导出报告生成器（纯函数）.
 *
 * <p>生成 _导出报告.txt 文本内容，包含导出时间、授权数、
 * 附件成功/失败计数与失败清单。不依赖 Spring，可独立单测。
 */
public final class BrandAuthExportReportBuilder {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BrandAuthExportReportBuilder() {}

    /**
     * 构建导出报告文本.
     *
     * @param exportTime 导出时间
     * @param authCount 导出授权数
     * @param successCount 附件成功数
     * @param failedCount 附件失败数
     * @param failures 失败附件清单（每条为 "文件夹/文件名 → 失败原因"）
     * @return UTF-8 报告文本
     */
    public static String build(final LocalDateTime exportTime,
                                final int authCount,
                                final int successCount,
                                final int failedCount,
                                final List<String> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("导出报告\n");
        sb.append("========\n");
        sb.append("导出时间: ").append(exportTime.format(FMT)).append("\n");
        sb.append("导出授权数: ").append(authCount).append("\n");
        sb.append("附件成功: ").append(successCount).append("\n");
        sb.append("附件失败: ").append(failedCount).append("\n");

        int failCount = failures == null ? 0 : failures.size();
        if (failCount > 0) {
            sb.append("\n失败清单:\n");
            for (int i = 0; i < failures.size(); i++) {
                sb.append(i + 1).append(". ")
                        .append(failures.get(i)).append("\n");
            }
        }
        return sb.toString();
    }
}
