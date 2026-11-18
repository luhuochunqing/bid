package com.xiyu.bid.casework.domain.policy;

import java.util.Locale;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：评分项业务意图确定性路由器。
 *
 * <p>遵循 FP-Java 规范：无状态纯函数、无 Spring 依赖、入参不可变、100% 可单测。</p>
 * <p>在案例推荐执行前，对评分项意图进行确定性分流，实现：
 * <ul>
 *   <li>资质证书类（QUALIFICATION）：直接分流至企业资质库，100% 阻断文本切片推荐；</li>
 *   <li>同类业绩类（PERFORMANCE）：分流至业绩准入通道；</li>
 *   <li>人员团队类（PERSONNEL）：分流至团队人员名录；</li>
 *   <li>技术方案类（TECHNICAL）：保留在方案语义检索流；</li>
 *   <li>商务财务类（COMMERCIAL）：与技术方案互斥。</li>
 * </ul>
 * </p>
 */
public final class ScoreItemIntentRouter {

    public enum Intent {
        TECHNICAL,
        QUALIFICATION,
        PERFORMANCE,
        PERSONNEL,
        COMMERCIAL,
        OTHER;

        public boolean isQualification() {
            return this == QUALIFICATION;
        }

        public boolean isPerformance() {
            return this == PERFORMANCE;
        }

        public boolean isPersonnel() {
            return this == PERSONNEL;
        }

        public boolean isCommercial() {
            return this == COMMERCIAL;
        }

        public boolean isTechnical() {
            return this == TECHNICAL;
        }
    }

    private ScoreItemIntentRouter() {}

    /**
     * 根据评分项维度名称（dim）与细则要求（detail）确定性路由其业务意图。
     */
    public static Intent route(String dim, String detail) {
        if (!hasText(dim) && !hasText(detail)) {
            return Intent.OTHER;
        }

        CaseCategoryMatchPolicy.MacroCategory macro = CaseCategoryMatchPolicy.classifyMacroCategory(dim, detail);
        if (macro == CaseCategoryMatchPolicy.MacroCategory.QUALIFICATION) {
            return Intent.QUALIFICATION;
        }
        if (macro == CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE) {
            return Intent.PERFORMANCE;
        }
        if (macro == CaseCategoryMatchPolicy.MacroCategory.PERSONNEL) {
            return Intent.PERSONNEL;
        }
        if (macro == CaseCategoryMatchPolicy.MacroCategory.COMMERCIAL) {
            return Intent.COMMERCIAL;
        }
        if (macro == CaseCategoryMatchPolicy.MacroCategory.TECHNICAL) {
            return Intent.TECHNICAL;
        }
        return Intent.OTHER;
    }
}
