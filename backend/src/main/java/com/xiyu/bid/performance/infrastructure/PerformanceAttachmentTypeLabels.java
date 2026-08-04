package com.xiyu.bid.performance.infrastructure;

import java.util.Map;

/**
 * 业绩附件类型 → 中文标签映射。
 *
 * <p>需求 §1：附件类型中文标签作为 H3 标题展示，BID_NOTICE 作为 H4。
 * 标签顺序即 Word 合订本中展示顺序。
 */
public final class PerformanceAttachmentTypeLabels {

    // ========== 附件类型常量 ==========

    public static final String TYPE_CONTRACT_AGREEMENT = "CONTRACT_AGREEMENT";
    public static final String TYPE_MALL_SCREENSHOT = "MALL_SCREENSHOT";
    public static final String TYPE_SOE_DIRECTORY = "SOE_DIRECTORY";
    public static final String TYPE_RELATIONSHIP_PROOF = "RELATIONSHIP_PROOF";
    public static final String TYPE_CATEGORY_PAGE = "CATEGORY_PAGE";
    public static final String TYPE_BID_NOTICE = "BID_NOTICE";
    public static final String TYPE_OTHER = "OTHER";

    /** 附件类型 → 中文标签 */
    public static final Map<String, String> LABELS = Map.of(
            TYPE_CONTRACT_AGREEMENT, "合同协议",
            TYPE_MALL_SCREENSHOT, "商城截图",
            TYPE_SOE_DIRECTORY, "央企名录",
            TYPE_RELATIONSHIP_PROOF, "关系证明",
            TYPE_CATEGORY_PAGE, "品类页",
            TYPE_BID_NOTICE, "中标通知书",
            TYPE_OTHER, "其他附件"
    );

    /**
     * 央企共享去重的附件类型集合。
     *
     * <p>需求 §3：针对央企用户，同一个集团 + 同一个签约抬头下，
     * 以下附件类型只需展示一次：关系证明、央企名录、品类页、商城截图。
     * 这些附件通常代表集团层级资质，多份合同共享同一份。
     */
    public static final java.util.Set<String> SOE_SHAREABLE_TYPES = java.util.Set.of(
            TYPE_RELATIONSHIP_PROOF,
            TYPE_SOE_DIRECTORY,
            TYPE_CATEGORY_PAGE,
            TYPE_MALL_SCREENSHOT
    );

    /** H4 级别附件类型（仅 BID_NOTICE） */
    public static final String H4_ATTACHMENT_TYPE = TYPE_BID_NOTICE;

    /** 合订本展示顺序 */
    public static final java.util.List<String> DISPLAY_ORDER = java.util.List.of(
            TYPE_CONTRACT_AGREEMENT,
            TYPE_SOE_DIRECTORY,
            TYPE_RELATIONSHIP_PROOF,
            TYPE_CATEGORY_PAGE,
            TYPE_MALL_SCREENSHOT,
            TYPE_OTHER,
            TYPE_BID_NOTICE
    );

    private PerformanceAttachmentTypeLabels() {
        // 常量类，禁止实例化
    }

    /** 获取中文标签，未知类型返回 fileType 原值。 */
    public static String labelOf(String fileType) {
        if (fileType == null) return "未分类";
        return LABELS.getOrDefault(fileType, fileType);
    }

    /** 判断是否为央企共享附件类型。 */
    public static boolean isSoeShareable(String fileType) {
        return SOE_SHAREABLE_TYPES.contains(fileType);
    }
}
