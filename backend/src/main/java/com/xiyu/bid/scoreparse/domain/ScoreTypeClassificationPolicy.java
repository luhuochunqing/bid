// Input: 评分项详细要素文本
// Output: OBJECTIVE / SUBJECTIVE 分类
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-003

package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Pattern;

/**
 * 客观/主观判定策略（spec 041 FR-003）。
 * <p>判定优先级（保守原则：无法判定按主观，避免虚假高分）：
 * <ol>
 *   <li>报价类（评标基准价/偏离率公式计算）→ SUBJECTIVE</li>
 *   <li>描述性要求（方案先进/合理/酌情给分）→ SUBJECTIVE</li>
 *   <li>量化条件（证书/数量/年限/面积门槛）→ OBJECTIVE</li>
 *   <li>默认 → SUBJECTIVE</li>
 * </ol>
 * <p>注意顺序：报价判定最优先（报价文本常含数字+分，易被量化规则误判）。
 */
public class ScoreTypeClassificationPolicy {

    /** 报价类关键词（价格分由评标基准价公式计算，非知识库可预计） */
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "投标报价|报价|评标基准价|价格分|偏离率");

    /** 量化条件特征：证书/认证/资质门槛、每单位计分、年限/面积门槛 */
    private static final Pattern QUANTIFIED_PATTERN = Pattern.compile(
            "(认证|证书|资质)"                                  // 证书/资质门槛
            + "|每(提供|人|个|项|套)"                           // 每单位计分
            + "|[0-9０-９]+\\s*(年以上|㎡以上|平方米以上|千瓦以上|台以上)" // 数量门槛
            + "|持有"                                           // 持证类
    );

    /** 描述性要求特征 */
    private static final Pattern DESCRIPTIVE_PATTERN = Pattern.compile(
            "先进|合理|可行|酌情|视情况|可操作|完整性强|科学性|优[良秀]的|一般的|较强|完好|理解深刻|贴合");

    public String classify(String detail) {
        return classify(detail, null);
    }

    public String classify(String detail, String scoreTypeGuess) {
        if (detail == null || detail.isBlank()) {
            return "SUBJECTIVE";
        }
        // 1. 报价类强制覆盖为主观（PRD 业务规则优先：基准价公式计算，知识库不可预测）
        if (PRICE_PATTERN.matcher(detail).find()) {
            return "SUBJECTIVE";
        }
        // 2. AI 结构化判定优先（R025）
        if (scoreTypeGuess != null && !scoreTypeGuess.isBlank()) {
            String normalized = scoreTypeGuess.trim().toUpperCase();
            if ("OBJECTIVE".equals(normalized) || "SUBJECTIVE".equals(normalized)) {
                return normalized;
            }
        }
        // 3. 量化条件规则兜底 → 客观
        if (QUANTIFIED_PATTERN.matcher(detail).find()) {
            return "OBJECTIVE";
        }
        // 4. 描述性要求规则兜底 → 主观
        if (DESCRIPTIVE_PATTERN.matcher(detail).find()) {
            return "SUBJECTIVE";
        }
        // 5. 条件式给分兜底（具备/提供/满足 + 数字 + 分）→ 客观
        if (Pattern.compile("(具备|提供|满足|达到).{0,40}[0-9０-９]+").matcher(detail).find()) {
            return "OBJECTIVE";
        }
        return "SUBJECTIVE";
    }
}
