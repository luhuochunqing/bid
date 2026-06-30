// Input: 前端上传传入的 documentCategory 原始字符串（可能为 TENDER_DOCUMENT / BID_DOCUMENT / null 等）
// Output: 归一化后的标准 DocumentCategory 枚举名（TENDER / BID / OPEN_LIST / WIN_NOTICE / DEPOSIT_RECEIPT / OTHER）
// Pos: projectworkflow/core/ - 纯函数，无框架依赖
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.core;

import java.util.Map;

/**
 * CO-420: 项目档案文档分类归集规则。
 * <p>各阶段上传的文档按阶段来源归集到 6 个标准分类：
 * <ul>
 *   <li>立项阶段 → TENDER（招标文件）</li>
 *   <li>标书制作阶段 → BID（投标文件）</li>
 *   <li>评标阶段 → OPEN_LIST（开标一览表）</li>
 *   <li>结果确认阶段 → WIN_NOTICE（中标通知书）</li>
 *   <li>项目结项阶段 → DEPOSIT_RECEIPT（保证金银行回单）</li>
 *   <li>其他阶段 → OTHER（其他）</li>
 * </ul>
 * <p>兼容历史前端传入的 TENDER_DOCUMENT / BID_DOCUMENT 等旧值，统一映射到标准枚举名。
 */
public final class DocumentCategoryNormalizer {

    /** 历史前端值 → 标准枚举名 映射表。 */
    private static final Map<String, String> ALIAS = Map.of(
            "TENDER_DOCUMENT", "TENDER",
            "BID_DOCUMENT", "BID",
            "EVALUATION_EVIDENCE", "OPEN_LIST",
            "RESULT_EVIDENCE", "WIN_NOTICE",
            "CLOSURE_EVIDENCE", "DEPOSIT_RECEIPT",
            "RETROSPECTIVE_REPORT", "OTHER"
    );

    private static final Map<String, String> STAGE_DEFAULT = Map.of(
            "INITIATION", "TENDER",
            "DRAFTING", "BID",
            "EVALUATION", "OPEN_LIST",
            "RESULT", "WIN_NOTICE",
            "RETROSPECTIVE", "OTHER",
            "CLOSURE", "DEPOSIT_RECEIPT"
    );

    private DocumentCategoryNormalizer() {}

    /**
     * 将前端传入的 documentCategory 归一化为标准枚举名。
     * <ul>
     *   <li>null/blank → null（由调用方决定默认值）</li>
     *   <li>已是标准枚举名（TENDER/BID/...）→ 原样返回</li>
     *   <li>历史别名（TENDER_DOCUMENT 等）→ 映射到标准名</li>
     *   <li>其他未知值 → OTHER</li>
     * </ul>
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        String upper = trimmed.toUpperCase();
        if (isStandard(upper)) return upper;
        String mapped = ALIAS.get(upper);
        return mapped != null ? mapped : "OTHER";
    }

    /**
     * 按项目阶段推导默认分类（当 documentCategory 为空时使用）。
     * @param stageCode 项目阶段代码（INITIATION/DRAFTING/EVALUATION/RESULT/RETROSPECTIVE/CLOSURE）
     */
    public static String defaultByStage(String stageCode) {
        if (stageCode == null || stageCode.isBlank()) return "OTHER";
        return STAGE_DEFAULT.getOrDefault(stageCode.trim().toUpperCase(), "OTHER");
    }

    private static boolean isStandard(String upper) {
        return upper.equals("TENDER") || upper.equals("BID") || upper.equals("OPEN_LIST")
                || upper.equals("WIN_NOTICE") || upper.equals("DEPOSIT_RECEIPT")
                || upper.equals("OTHER");
    }
}
