package com.xiyu.bid.biddraftagent.domain.validation;

/**
 * 四库联动聚合结果。
 * 包含资质库、人员库、品牌授权库、业绩库的匹配结果以及汇总统计。
 */
public record KnowledgeBaseMatchResult(
        QualificationMatchResult qualificationMatch,
        PersonnelCertMatcher.PersonnelMatchResult personnelMatch,
        BrandAuthMatcher.BrandAuthMatchResult brandAuthMatch,
        PerformanceMatcher.PerformanceMatchResult performanceMatch,
        KnowledgeBaseSummary summary
) {

    /** 四库汇总统计 */
    public record KnowledgeBaseSummary(
            int totalSatisfied,
            int totalAttention,
            int totalUnsatisfied
    ) {
        public static KnowledgeBaseSummary compute(
                QualificationMatchResult qual,
                PersonnelCertMatcher.PersonnelMatchResult personnel,
                BrandAuthMatcher.BrandAuthMatchResult brandAuth,
                PerformanceMatcher.PerformanceMatchResult performance) {

            int satisfied = 0, attention = 0, unsatisfied = 0;

            if (qual != null && qual.items() != null) {
                for (var item : qual.items()) {
                    satisfied += countStatus(item.status(), 1, 0, 0);
                    attention += countStatus(item.status(), 0, 1, 0);
                    unsatisfied += countStatus(item.status(), 0, 0, 1);
                }
            }
            if (personnel != null && personnel.items() != null) {
                for (var item : personnel.items()) {
                    satisfied += countStatus(item.status(), 1, 0, 0);
                    attention += countStatus(item.status(), 0, 1, 0);
                    unsatisfied += countStatus(item.status(), 0, 0, 1);
                }
            }
            if (brandAuth != null && brandAuth.items() != null) {
                for (var item : brandAuth.items()) {
                    satisfied += countStatus(item.status(), 1, 0, 0);
                    attention += countStatus(item.status(), 0, 1, 0);
                    unsatisfied += countStatus(item.status(), 0, 0, 1);
                }
            }
            if (performance != null && performance.items() != null) {
                for (var item : performance.items()) {
                    satisfied += countStatus(item.status(), 1, 0, 0);
                    attention += countStatus(item.status(), 0, 1, 0);
                    unsatisfied += countStatus(item.status(), 0, 0, 1);
                }
            }

            return new KnowledgeBaseSummary(satisfied, attention, unsatisfied);
        }

        private static int countStatus(QualificationMatchStatus status, int s, int a, int u) {
            if (status == QualificationMatchStatus.SATISFIED) return s;
            if (status == QualificationMatchStatus.ATTENTION) return a;
            if (status == QualificationMatchStatus.UNSATISFIED) return u;
            return 0;
        }
    }
}
