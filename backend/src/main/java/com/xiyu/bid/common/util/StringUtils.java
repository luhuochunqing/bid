package com.xiyu.bid.common.util;

/**
 * 字符串处理工具类。
 *
 * <p>统一 {@code truncate} 等工具方法，避免在多个 StateService 中逐字复制。
 *
 * @since CO-602 PR 设计评估修复
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * 截断字符串到指定长度。null 安全。
     *
     * @param s 原始字符串，可为 null
     * @param maxLen 最大长度
     * @return 截断后的字符串；入参为 null 时返回空串
     */
    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (maxLen <= 0) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
