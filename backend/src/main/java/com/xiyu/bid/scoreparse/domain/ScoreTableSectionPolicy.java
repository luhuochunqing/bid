// Input: 表格行文本 / ScoreCandidate 字段文本
// Output: 章节大类推导与价格公式判定（纯函数，无日志无 IO）
// Pos: scoreparse/domain — Task1 关键词单一真相源
// 维护声明: 行文本侧（StructuredTableCandidateExtractor）与候选字段侧
//      （ScoreCandidatePostProcessor#isPriceFormulaItem）共享本文件词表；
//      改任一侧词表必须同步另一侧锚定测试，禁止单边加词。
package com.xiyu.bid.scoreparse.domain;

import java.util.List;

/**
 * 评分表格章节与价格公式关键词策略（PR !2589 后续：收敛三处重复词表）。
 * <p>两处消费者互为备份，缺一即丢项或漏放行：
 * <ul>
 *   <li>创建侧：{@code StructuredTableCandidateExtractor} 用行文本判定
 *       {@link #isPriceFormula}（零分兜底）并用 {@link #inferSectionDimension} 注入
 *       {@code parentDim}（超载剔除免疫依据）；</li>
 *   <li>放行侧：{@code ScoreCandidatePostProcessor#isPriceFormulaItem} 用候选字段
 *       复判，放行零分价格公式项通过治理链。</li>
 * </ul>
 * <p>纯核心：仅依赖 {@code java.util}，无日志、无框架，miss 上报由调用链
 * （{@code extractWithSectionReport} → application 层日志）完成，本类保持无副作用。
 */
public final class ScoreTableSectionPolicy {

    /** 章节推导扫描行数上限：标题行通常在表头附近，超限只增噪音不增召回。 */
    public static final int SECTION_SCAN_ROWS = 15;

    /** 独立分表·报价表主体特征（须同时命中 {@link #PRICE_SECTION_HINT} 才算价格表）。 */
    public static final List<String> PRICE_SECTION_SUBJECT = List.of("价格评审", "折扣率", "评审价格", "基准价");

    /** 独立分表·报价表公式特征。 */
    public static final List<String> PRICE_SECTION_HINT = List.of("公式", "算术平均", "价格得分");

    /** 独立分表·商务表特征。 */
    public static final List<String> BIZ_SECTION_KEYWORDS = List.of("商务评审", "商务评分", "商务应答");

    /** 独立分表·技术表特征。 */
    public static final List<String> TECH_SECTION_KEYWORDS = List.of("技术评审", "技术评分", "技术应答", "对接方案");

    /**
     * 1359 中海油项目专用组合特征（商务表无"商务评审"字样时的回退）。
     * 泛化前保留：新项目措辞优先追加到 {@link #BIZ_SECTION_KEYWORDS}，
     * 确认通用后再删此特例，禁止反向把通用词改成特例。
     */
    public static final List<String> BIZ_SECTION_PAIR_1359 = List.of("财务状况", "信用评价");

    /**
     * 1359 中海油项目专用组合特征（技术表回退），维护纪律同 {@link #BIZ_SECTION_PAIR_1359}。
     */
    public static final List<String> TECH_SECTION_PAIR_1359 = List.of("供货业绩", "配送能力");

    /** 价格公式行主体特征（行文本侧）。 */
    public static final List<String> PRICE_FORMULA_SUBJECT = List.of("折扣率", "基准价", "价格得分", "评审价格", "报价得分");

    /** 价格公式行公式特征（行文本侧；"评价"宽泛，必须与主体特征同时命中才算数）。 */
    public static final List<String> PRICE_FORMULA_HINT = List.of("公式", "算术平均", "得分=", "得分 =", "计算", "评价");

    /** 价格公式候选父维度特征（候选字段侧）。 */
    public static final List<String> PRICE_PARENT_DIM = List.of("报价", "价格");

    /** 价格公式候选名称特征（候选字段侧）。 */
    public static final List<String> PRICE_DIM = List.of("折扣率", "报价", "价格得分", "基准价");

    /** 价格公式候选细则特征（候选字段侧）。 */
    public static final List<String> PRICE_DETAIL = List.of("算术平均值", "评审价格得分", "加权折扣率");

    private ScoreTableSectionPolicy() {
    }

    /** 任一关键词命中即 true；text 为空直接 false（CellPayload.text 已归一非空，此处防未来调用方）。 */
    // REUSE-OK: backend/src/main/java/com/xiyu/bid/casework/domain/policy/CaseCategoryMatchPolicy.java — 同名仅 5 行字符串辅助，跨模块复用将引入 scoreparse→casework 错误依赖方向；且签名不同（本方法入参为 List 常量直通，复用需逐调用点转数组），仓库内同类辅助已有 8 处模块自持先例
    public static boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isEmpty() || keywords == null) return false;
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    /** 全部关键词命中即 true（组合特征用，如 1359 特例对）。 */
    public static boolean containsAll(String text, List<String> keywords) {
        if (text == null || text.isEmpty() || keywords == null || keywords.isEmpty()) return false;
        for (String keyword : keywords) {
            if (!text.contains(keyword)) return false;
        }
        return true;
    }

    /**
     * 独立分表章节大类推导（"报价"/"商务"/"技术"，混合单表或未知措辞返回 null 不污染）。
     * <p>混合单表（如 1358/1371 同时含商务与技术特征）必须返回 null，否则跨表维度串扰。
     * 未知措辞同样返回 null：调用方在多表场景下负责上报 miss（见
     * {@code StructuredTableCandidateExtractor#extractWithSectionReport}），
     * 本方法不打日志，保持纯函数。
     */
    public static String inferSectionDimension(List<List<SidecarTableExtraction.CellPayload>> rows) {
        if (rows == null || rows.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(rows.size(), SECTION_SCAN_ROWS);
        for (int i = 0; i < limit; i++) {
            if (rows.get(i) == null) continue;
            for (SidecarTableExtraction.CellPayload c : rows.get(i)) sb.append(c.text()).append(' ');
        }
        String text = sb.toString();
        boolean hasPrice = containsAny(text, PRICE_SECTION_SUBJECT) && containsAny(text, PRICE_SECTION_HINT);
        boolean hasBiz = containsAny(text, BIZ_SECTION_KEYWORDS) || containsAll(text, BIZ_SECTION_PAIR_1359);
        boolean hasTech = containsAny(text, TECH_SECTION_KEYWORDS) || containsAll(text, TECH_SECTION_PAIR_1359);
        if (hasPrice && !hasBiz && !hasTech) return "报价";
        if (hasBiz && !hasTech) return "商务";
        if (hasTech && !hasBiz) return "技术";
        return null;
    }

    /**
     * 价格公式行判定（行文本侧；与候选字段侧
     * {@code ScoreCandidatePostProcessor#isPriceFormulaItem} 互为备份）。
     * <p>Task1 决策边界：仅此类行允许 {@code weight == null} 时零分兜底，
     * 其余无分值行（如资格审查）仍过滤，否则 1371 类混合单表会从 16 项膨胀至 44 项。
     */
    public static boolean isPriceFormula(List<String> texts) {
        if (texts == null || texts.isEmpty()) return false;
        String all = String.join(" ", texts);
        return containsAny(all, PRICE_FORMULA_SUBJECT) && containsAny(all, PRICE_FORMULA_HINT);
    }
}
