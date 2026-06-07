package com.xiyu.bid.tender.core;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 标讯去重策略（纯核心）。
 * 判断逻辑：招标主体 + 报名截止时间 + 开标时间 三字段完全匹配。
 * 任何一方招标主体为 null 或空白时直接返回 false（缺乏足够匹配信息）。
 * 不依赖任何外部资源。
 */
public final class TenderDeduplicationPolicy {

    private TenderDeduplicationPolicy() { /* utility */ }

    /**
     * 判断两笔标讯是否重复。
     *
     * @param purchaser1    标讯1的招标主体
     * @param regDeadline1 标讯1的报名截止时间
     * @param bidOpenTime1 标讯1的开标时间
     * @param purchaser2   标讯2的招标主体
     * @param regDeadline2 标讯2的报名截止时间
     * @param bidOpenTime2 标讯2的开标时间
     * @return true 如果三字段完全匹配
     */
    public static boolean isDuplicate(
            String purchaser1, LocalDateTime regDeadline1, LocalDateTime bidOpenTime1,
            String purchaser2, LocalDateTime regDeadline2, LocalDateTime bidOpenTime2) {
        if (isBlank(purchaser1) || isBlank(purchaser2)) {
            return false;
        }
        return Objects.equals(normalize(purchaser1), normalize(purchaser2))
                && Objects.equals(regDeadline1, regDeadline2)
                && Objects.equals(bidOpenTime1, bidOpenTime2);
    }

    /**
     * 生成去重提示文本。
     */
    public static String formatDuplicateMessage(String purchaser, LocalDateTime regDeadline,
            LocalDateTime bidOpenTime) {
        return String.format("【%s】+【%s】+【%s】已存在，请联系投标管理员确认是否覆盖原标讯",
                purchaser, regDeadline, bidOpenTime);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
