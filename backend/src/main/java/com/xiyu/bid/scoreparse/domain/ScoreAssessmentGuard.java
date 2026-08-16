// Input: LLM 阶段2打分原始输出字段 + 权重上限 + 评分类别
// Output: 守卫后 Assessment（超区间置空/主观数字丢弃/quoteMissing 置空）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-016 / SC-003 / contracts/llm-output-schema.md §5

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;

/**
 * 阶段 2 打分结果守卫（spec 041 FR-016 / SC-003）。
 *
 * <p>不信任模型数值，统一在本守卫收敛三类规则：
 * <ul>
 *   <li>主观项：actualScore/matchRatio/evidence 强制丢弃（数字得分零泄漏），
 *       仅保留 suggestion（missedReason 同步丢弃，主观项无"未满足"语义）</li>
 *   <li>客观项：actualScore ∈ [0, weight]，超区间置 null 并标记 rangeInvalid（调用方记日志）</li>
 *   <li>quoteMissing=true 时 quote 置 null（前端显示"标书引用：无"）</li>
 * </ul>
 */
public class ScoreAssessmentGuard {

    public static final String TYPE_OBJECTIVE = "OBJECTIVE";
    public static final String TYPE_SUBJECTIVE = "SUBJECTIVE";

    /**
     * @param input      LLM 原始输出（actualScore 已由调用方从 Double 转 BigDecimal）
     * @param weight     权重上限
     * @param scoreType  OBJECTIVE / SUBJECTIVE
     */
    public Result guard(Input input, BigDecimal weight, String scoreType) {
        if (TYPE_SUBJECTIVE.equals(scoreType)) {
            return new Result(null, null, null, null, null,
                    input.suggestion(), false, true);
        }

        boolean quoteMissing = Boolean.TRUE.equals(input.quoteMissing());
        String quote = quoteMissing ? null : input.quote();
        boolean rangeInvalid = false;
        BigDecimal actualScore = input.actualScore();
        if (actualScore != null
                && (actualScore.compareTo(BigDecimal.ZERO) < 0 || actualScore.compareTo(weight) > 0)) {
            actualScore = null;
            rangeInvalid = true;
        }
        return new Result(actualScore, input.matchRatio(), input.evidence(),
                quote, input.missedReason(), input.suggestion(), rangeInvalid, false);
    }

    /**
     * LLM 原始输出（与 infrastructure 的 ScoreAssessmentOutput 字段一一对应，
     * domain 不依赖 infrastructure，故独立定义）。
     */
    public static class Input {
        private BigDecimal actualScore;
        private Integer matchRatio;
        private String evidence;
        private String quote;
        private Boolean quoteMissing;
        private String missedReason;
        private String suggestion;

        public static InputBuilder builder() {
            return new InputBuilder();
        }

        public BigDecimal actualScore() {
            return actualScore;
        }

        public Integer matchRatio() {
            return matchRatio;
        }

        public String evidence() {
            return evidence;
        }

        public String quote() {
            return quote;
        }

        public Boolean quoteMissing() {
            return quoteMissing;
        }

        public String missedReason() {
            return missedReason;
        }

        public String suggestion() {
            return suggestion;
        }

        public static class InputBuilder {
            private final Input input = new Input();

            public InputBuilder actualScore(BigDecimal value) {
                input.actualScore = value;
                return this;
            }

            public InputBuilder matchRatio(Integer value) {
                input.matchRatio = value;
                return this;
            }

            public InputBuilder evidence(String value) {
                input.evidence = value;
                return this;
            }

            public InputBuilder quote(String value) {
                input.quote = value;
                return this;
            }

            public InputBuilder quoteMissing(Boolean value) {
                input.quoteMissing = value;
                return this;
            }

            public InputBuilder missedReason(String value) {
                input.missedReason = value;
                return this;
            }

            public InputBuilder suggestion(String value) {
                input.suggestion = value;
                return this;
            }

            public Input build() {
                return input;
            }
        }
    }

    /**
     * @param rangeInvalid       客观项得分超 [0, weight] 已置空（FR-016 异常日志信号）
     * @param subjectiveDropped  主观项数字被强制丢弃（SC-003 泄漏信号）
     */
    public record Result(BigDecimal actualScore, Integer matchRatio, String evidence,
                         String quote, String missedReason, String suggestion,
                         boolean rangeInvalid, boolean subjectiveDropped) {
    }
}
