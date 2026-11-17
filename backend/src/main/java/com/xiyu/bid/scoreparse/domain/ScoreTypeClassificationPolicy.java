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
 *   <li>描述性要求或定性分档评价 → SUBJECTIVE</li>
 *   <li>可计算报价公式、数量递增、证书或量化门槛 → OBJECTIVE</li>
 *   <li>默认 → SUBJECTIVE</li>
 * </ol>
 * <p>定性评价优先，数字区间本身不能证明客观条件。
 */
public class ScoreTypeClassificationPolicy {

    /** 量化条件特征：证书/认证/资质门槛、每单位计分、年限/面积/金额门槛、财务指标 */
    private static final Pattern QUANTIFIED_PATTERN = Pattern.compile(
            "(认证|证书|资质)"                                  // 证书/资质门槛
            + "|每(提供|人|个|项|套|家|份|次)"                   // 每单位计分
            + "|[0-9０-９]+\\s*(年以上|年及以上|㎡以上|㎡及以上|平方米以上|平方米及以上|千瓦以上|台以上|万元以上|万元及以上|万以上|万及以上|亿元以上|亿元及以上|亿以上|日前|日后)"
            + "|([≥>≤<]|不低于|不少于|不小于|超过|达到|满足)\\s*[0-9０-９]+\\s*(万|亿|元|个|项|年|家|套|台|人|份|次|㎡|平方米)"
            + "|持有|销售额|营业额|营业收入|营收|合同额|合同金额|开票额|开票金额|纳税额|资产总额|净资产|注册资本|注册资金|金额|数量|有效期|日期|公式|基准价|偏离率|折扣率|起订额|信用额度"
    );

    /** 条件动作及量化门槛匹配（静态编译，避免热路径重复编译；排除"满分"以防误伤主观项） */
    private static final Pattern QUALIFIED_NUMERIC_PATTERN = Pattern.compile(
            "([≥>≤<]|具备|提供|满足|达到|不低于|不少于|超过).{0,40}[0-9０-９]+"
    );

    /** 描述性要求特征：主观评分档位、模糊评价、专家评审方案性/设计性内容（精确排除"一般纳税人"及"优先/优质/优惠/择优"等商业客观用语） */
    private static final Pattern DESCRIPTIVE_PATTERN = Pattern.compile(
            "横向比较|综合比较|先进|合理|可行|酌情|视情况|可操作|完整性强|科学性"
            + "|(优秀|优良|优等|优(?=[、，,和与及/]))|中等|一般(?!纳税人)|较强|完好|理解深刻|贴合"
            + "|分档打分|分档评审|分档评价|分档计分|按[A-DＡ-Ｄa-d、，,/ ]+分档|分[A-DＡ-Ｄa-d]档|[A-DＡ-Ｄ]{2,4}档"
            + "|由[^。；\n]{0,30}(专家|评委|评标委员会)[^。；\n]{0,30}(打分|评审)|专家评审|专家打分|评委打分|评委评审|评标委员会[^。；\n]{0,30}(打分|评审)"
            + "|综合评价|综合评审|针对性|合理性|可行性|充分性|可操作性"
    );

    /** Qualitative grade language plus a score interval denotes an evaluator's judgment, not a numeric threshold. */
    private static final Pattern DESCRIPTIVE_GRADE_PATTERN = Pattern.compile(
            "(?:更为优越|基本满足|经验(?:比较)?丰富|实力强大)[^。；;]{0,180}(?:得|评分)\\s*"
                    + "[0-9０-９]+(?:[.．][0-9０-９]+)?\\s*[-—–~～至]\\s*[0-9０-９]+(?:[.．][0-9０-９]+)?\\s*分"
    );

    public String classify(String detail) {
        return classify(detail, null);
    }

    public String classify(String detail, String scoreTypeGuess) {
        if (detail == null || detail.isBlank()) {
            return "SUBJECTIVE";
        }
        // 明确主观评价措辞优先，数字区间本身不能把横向评价改判为客观。
        if (DESCRIPTIVE_PATTERN.matcher(detail).find() || DESCRIPTIVE_GRADE_PATTERN.matcher(detail).find()) {
            return "SUBJECTIVE";
        }
        if (IncrementalCountScorePolicy.parse(detail).isPresent()) {
            return "OBJECTIVE";
        }
        // 明确可验证条件优先于模型猜测；报价公式属于可计算客观项。
        if (QUANTIFIED_PATTERN.matcher(detail).find()
                || QUALIFIED_NUMERIC_PATTERN.matcher(detail).find()) {
            return "OBJECTIVE";
        }
        // 模型值仅用于没有明确规则信号的文本。
        if (scoreTypeGuess != null && !scoreTypeGuess.isBlank()) {
            String normalized = scoreTypeGuess.trim().toUpperCase();
            if ("OBJECTIVE".equals(normalized) || "SUBJECTIVE".equals(normalized)) {
                return normalized;
            }
        }
        return "SUBJECTIVE";
    }
}
