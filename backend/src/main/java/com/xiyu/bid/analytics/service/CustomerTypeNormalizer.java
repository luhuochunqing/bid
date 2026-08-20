// Input: raw customerType string (中文标准值/英文枚举/历史变体)
// Output: 5 种标准分类之一的中文标签，不匹配返回 null
// Pos: Pure Domain/纯函数归一化工具（无注入、无 IO）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import java.util.Set;

/**
 * 客户类型归一化纯函数工具：将项目 customerType 字段值归一到 5 种标准分类之一。
 *
 * <p>双源取值口径 {@code coalesce(nullif(pid.customer_type,''), p.customer_type)} 下，
 * 立项详情存英文枚举名（GOVERNMENT/CENTRAL_SOE/LOCAL_SOE/PRIVATE/FOREIGN/OTHER，
 * 见 ProjectInitiationMapper.applyInput），项目主表快照存中文文本（标讯导入/人工录入）。
 * M1 趋势维度分组/筛选/下钻与 M2 客户类型统计统一经本工具归一化，保证口径一致。</p>
 */
public final class CustomerTypeNormalizer {

    // 5 种标准客户类型分类（与 Excel 导入模板一致）
    public static final String CATEGORY_GOVERNMENT = "政府机关/事业单位/高校";
    public static final String CATEGORY_CENTRAL_SOE = "央企";
    public static final String CATEGORY_LOCAL_SOE = "地方国企";
    public static final String CATEGORY_PRIVATE = "民企";
    public static final String CATEGORY_FOREIGN = "港澳台及外企";

    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            CATEGORY_GOVERNMENT, CATEGORY_CENTRAL_SOE, CATEGORY_LOCAL_SOE,
            CATEGORY_PRIVATE, CATEGORY_FOREIGN
    );

    private CustomerTypeNormalizer() {
    }

    /**
     * 将项目的 customerType 字段值归一到 5 种标准分类之一。
     * 支持数据库中存储的英文枚举（GOVERNMENT/CENTRAL_SOE/LOCAL_SOE/PRIVATE/FOREIGN 等）及中文变体。
     * 不匹配任何分类时返回 null（调用方跳过不统计）。
     */
    public static String normalize(String customerType) {
        if (customerType == null || customerType.isBlank()) {
            return null;
        }
        String trimmed = customerType.trim();
        // 1) 精确匹配中文标准分类
        if (ALLOWED_CATEGORIES.contains(trimmed)) {
            return trimmed;
        }
        String upper = trimmed.toUpperCase();

        // 2) 英文枚举精确与前缀匹配
        if (upper.equals("GOVERNMENT") || upper.equals("GOVERNMENT_INSTITUTION")) {
            return CATEGORY_GOVERNMENT;
        }
        if (upper.equals("CENTRAL_SOE") || upper.equals("STATE_OWNED")) {
            return CATEGORY_CENTRAL_SOE;
        }
        if (upper.equals("LOCAL_SOE")) {
            return CATEGORY_LOCAL_SOE;
        }
        if (upper.equals("PRIVATE") || upper.equals("PRIVATE_ENTERPRISE")) {
            return CATEGORY_PRIVATE;
        }
        if (upper.equals("FOREIGN") || upper.equals("FOREIGN_HK_MACAO_TW") || upper.startsWith("FOREIGN_")) {
            return CATEGORY_FOREIGN;
        }

        // 3) 中文模糊匹配：覆盖常见变体写法
        if (trimmed.contains("政府") || trimmed.contains("事业单位") || trimmed.contains("高校")
                || trimmed.contains("机关")) {
            return CATEGORY_GOVERNMENT;
        }
        if (trimmed.contains("央企") || trimmed.contains("中央企业")) {
            return CATEGORY_CENTRAL_SOE;
        }
        if (trimmed.contains("地方国企") || ((trimmed.contains("国企") || trimmed.contains("国有")) && !trimmed.contains("央"))) {
            return CATEGORY_LOCAL_SOE;
        }
        if (trimmed.contains("民企") || trimmed.contains("民营") || trimmed.contains("私营") || trimmed.contains("企业客户")) {
            return CATEGORY_PRIVATE;
        }
        if (trimmed.contains("港澳台") || trimmed.contains("港") || trimmed.contains("澳") || trimmed.contains("台")
                || trimmed.contains("外企") || trimmed.contains("外资") || trimmed.contains("合资")) {
            return CATEGORY_FOREIGN;
        }
        return null;
    }
}
