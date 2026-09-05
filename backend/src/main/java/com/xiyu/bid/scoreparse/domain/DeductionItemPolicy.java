// Input: 评分候选条目或文本
// Output: 是否为扣分项/信用负面项的 boolean 判定
// Pos: scoreparse/domain — 纯领域模型，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 统一收敛 isDeduction 判定

package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Pattern;

/**
 * 扣分项/信用负面项判定策略（纯领域规则，FP-Java）。
 * 统一收敛各层对扣分项、诚信评价、负面清单、失信惩戒、违规违约等减分/兜底项的谓词判定。
 */
public final class DeductionItemPolicy {

    private static final Pattern DEDUCTION_PATTERN = Pattern.compile(
            "扣分|扣完|扣除|不扣分|按项扣|每.*扣|每发生.*扣|罚款|诚信|信用|负面|违规|违约|失信|处罚|黑名单"
    );

    private DeductionItemPolicy() {
    }

    /**
     * 判断文本片段是否包含扣分/负面特征词。
     */
    public static boolean isDeductionText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return DEDUCTION_PATTERN.matcher(text).find();
    }

    /**
     * 判断评分候选项是否为扣分项/信用负面项（综合扫描 dim、parentDim、detail）。
     */
    public static boolean isDeduction(ScoreCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String dim = candidate.dim() != null ? candidate.dim() : "";
        String parentDim = candidate.parentDim() != null ? candidate.parentDim() : "";
        String detail = candidate.detail() != null ? candidate.detail() : "";
        return isDeductionText(dim + " " + parentDim + " " + detail);
    }
}
