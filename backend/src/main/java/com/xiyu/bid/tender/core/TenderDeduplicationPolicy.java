package com.xiyu.bid.tender.core;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 标讯去重策略（纯核心）。
 * 判断逻辑：招标主体 + 项目类型 + 报名截止时间 + 开标时间 四字段完全匹配。
 *
 * <p>空值处理：
 * <ul>
 *   <li>招标主体：任一方为 null 或空白 → 直接返回 false（缺乏足够匹配信息）</li>
 *   <li>时间字段：任一方为 null → 直接返回 false</li>
 *   <li>项目类型：null 归一化为空字符串参与比较。双方均 null 视为匹配，
 *       一方 null 一方有值视为不匹配</li>
 * </ul>
 *
 * <p>时间比较截断到秒：前端时间选择器精确到分钟，数据库中不同字段可能保存为
 * DATETIME / DATETIME(6) 等不同精度，秒以下差异不应影响业务去重判定。
 *
 * <p>不依赖任何外部资源。调用方入口覆盖情况见 {@link TenderDeduplicationService} 类 Javadoc。
 */
public final class TenderDeduplicationPolicy {

    private TenderDeduplicationPolicy() { /* utility */ }

    /**
     * 判断两笔标讯是否重复。
     *
     * @param purchaser1     标讯1的招标主体
     * @param projectType1   标讯1的项目类型
     * @param regDeadline1   标讯1的报名截止时间
     * @param bidOpenTime1   标讯1的开标时间
     * @param purchaser2     标讯2的招标主体
     * @param projectType2   标讯2的项目类型
     * @param regDeadline2   标讯2的报名截止时间
     * @param bidOpenTime2   标讯2的开标时间
     * @return true 如果四字段完全匹配
     */
    public static boolean isDuplicate(
            String purchaser1, String projectType1, LocalDateTime regDeadline1, LocalDateTime bidOpenTime1,
            String purchaser2, String projectType2, LocalDateTime regDeadline2, LocalDateTime bidOpenTime2) {
        if (isBlank(purchaser1) || isBlank(purchaser2)) {
            return false;
        }
        // 时间字段必须都有值才能判定重复；任一为 null 表示未填写，不应与任何情况匹配
        if (regDeadline1 == null || regDeadline2 == null
                || bidOpenTime1 == null || bidOpenTime2 == null) {
            return false;
        }
        return normalize(purchaser1).equalsIgnoreCase(normalize(purchaser2))
                && normalize(projectType1).equalsIgnoreCase(normalize(projectType2))
                && Objects.equals(truncateToSeconds(regDeadline1), truncateToSeconds(regDeadline2))
                && Objects.equals(truncateToSeconds(bidOpenTime1), truncateToSeconds(bidOpenTime2));
    }

    private static LocalDateTime truncateToSeconds(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * 生成去重提示文本。
     */
    public static String formatDuplicateMessage(String purchaser, String projectType,
            LocalDateTime regDeadline, LocalDateTime bidOpenTime) {
        return String.format("【%s】+【%s】+【%s】+【%s】已存在，请联系投标管理员确认是否覆盖原标讯",
                purchaser, projectType, regDeadline, bidOpenTime);
    }

    /**
     * 批量导入场景的去重提示：包含已有标讯标题和四字段判定依据。
     *
     * @param existing       已存在的标讯实体（可为 null，表示未携带详情）
     * @param newRowPurchaser 新行 Excel 中的招标主体名称（用于补充提示）
     */
    public static String formatImportDuplicateMessage(com.xiyu.bid.entity.Tender existing, String newRowPurchaser) {
        if (existing == null) {
            var purchaser = newRowPurchaser != null ? newRowPurchaser : "";
            return String.format(
                    "标讯重复：招标主体「%s」、项目类型、报名截止时间、开标时间均一致，系统判定为同一条标讯。如确为不同标讯，请修改项目类型、报名截止或开标时间后重试",
                    purchaser);
        }
        var title = existing.getTitle() != null && !existing.getTitle().isBlank() ? existing.getTitle() : "(无标题)";
        var purchaser = existing.getPurchaserName() != null ? existing.getPurchaserName() : "";
        return String.format(
                "标讯重复：与已有标讯「%s」的招标主体「%s」、项目类型、报名截止时间、开标时间均一致，系统判定为同一条标讯。如确为不同标讯，请修改项目类型、报名截止或开标时间后重试",
                title, purchaser);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
