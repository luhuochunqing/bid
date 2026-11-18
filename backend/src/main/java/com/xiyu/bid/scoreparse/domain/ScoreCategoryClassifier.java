// Input: 评分维度名称
// Output: 是否为顶层大类判定（商务/技术/价格等）
// Pos: scoreparse/domain — 纯核心，无框架依赖
package com.xiyu.bid.scoreparse.domain;

/**
 * 评分大类分类器（纯核心，无框架依赖）。
 * <p>识别评分项的维度名称是否仅为顶层大类分类（如"商务"、"技术"、"价格"等），
 * 避免大类名称覆盖具体的叶子评分项细则标题。
 */
public final class ScoreCategoryClassifier {

    private ScoreCategoryClassifier() {
    }

    /**
     * 判断给定维度名称是否为顶层大类（或空）。
     *
     * @param dim 维度名称
     * @return true 若为空或属于大类词汇
     */
    public static boolean isTopLevelCategory(String dim) {
        if (dim == null || dim.isBlank()) {
            return true;
        }
        String clean = dim.replaceAll("[\\s（(][^）)]*[）)]", "").trim().toLowerCase();
        return clean.matches("^(商务|技术|价格|报价|综合|评议|商务分|技术分|价格分|报价分|商务部分|技术部分|价格部分|报价部分|综合评审|商务评议|技术评议|business|technical|price|commercial)$");
    }
}
