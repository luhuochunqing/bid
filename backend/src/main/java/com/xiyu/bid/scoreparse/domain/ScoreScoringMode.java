package com.xiyu.bid.scoreparse.domain;

/**
 * 评分项打分与评价计算模式。
 * <p>上游 ScoreParse LLM 抽取并归一化。
 */
public enum ScoreScoringMode {
    /** 主观专家评审（优良中差分档、专家打分、横向对比评判） */
    SUBJECTIVE_REVIEW,
    /** 量化数值排位（按数量/金额/指标由多到少排序给分、梯队分档） */
    QUANTITATIVE_RANKING,
    /** 阶梯/累进给分（如每提供一个得X分，每增加Y得Z分，设最高分） */
    STEPWISE_SCORING,
    /** 扣分制（从基准分扣减，扣完为止） */
    DEDUCTION,
    /** 其他模式（固定得分等） */
    OTHER;

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ScoreScoringMode.valueOf(value.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            return OTHER.name();
        }
    }
}
