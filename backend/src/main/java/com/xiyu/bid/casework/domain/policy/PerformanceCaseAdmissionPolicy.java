// Input: 候选案例全文 + 资格项业绩要求（可含品类-金额门槛）
// Output: 准入判定（tier 1/2/3）与判定依据；tier=0 表示拒绝
// Pos: casework/domain/policy/ - 业绩案例候选准入纯核心策略（FP-Java 无外部依赖）
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：业绩候选案例准入策略。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 *
 * <p>分层语义（编排层保留最优两层，避免空结果与高相关候选被丢弃）：
 * tier=1 金额门槛达成；tier=2 品类命中但无金额佐证；tier=3 仅命中业绩证据；
 * tier=0 拒绝（无业绩证据，或金额证据明确低于门槛）。</p>
 *
 * <p>说明：{@link AdmissionDecision#reasons()} 目前仅用于诊断与日志，
 * 不进入接口响应；面向用户的推荐理由仍由打分链路的资格加分理由承担。</p>
 */
public final class PerformanceCaseAdmissionPolicy {

    public record AdmissionDecision(int tier, List<String> reasons) {}

    /** 业绩证据信号词：仅命中这些词（或命中金额证据）才进入准入评估，防止人员/技术文案混入 */
    private static final List<String> PERFORMANCE_SIGNAL_WORDS = List.of(
            "业绩", "供货", "供货记录", "年度供应", "累计金额", "发票", "集采", "电商化",
            "采购合同", "销售合同", "供货合同", "框架合同", "合同业绩", "合同项下", "合同金额"
    );

    private PerformanceCaseAdmissionPolicy() {}

    public static AdmissionDecision evaluateCandidate(
            KnowledgeCaseReadModel candidate,
            QualificationPerformanceRetriever.PerformanceRequirementResult requirement) {
        if (candidate == null) {
            return new AdmissionDecision(0, List.of("候选为空"));
        }
        return evaluateCandidate(candidateText(candidate), requirement);
    }

    /**
     * 从候选集中选出可准入的候选：优先保留 tier1+tier2（门槛达成/品类命中），
     * 均无时降级保留 tier3（仅业绩信号）；全部无业绩证据时返回空列表（诚实降级）。
     */
    public static List<KnowledgeCaseReadModel> admit(
            List<KnowledgeCaseReadModel> candidates,
            QualificationPerformanceRetriever.PerformanceRequirementResult requirement) {
        Map<Long, Integer> tiers = new LinkedHashMap<>();
        boolean hasCategoryEvidence = false;
        boolean hasSignalFallback = false;
        for (KnowledgeCaseReadModel candidate : candidates) {
            int tier = evaluateCandidate(candidate, requirement).tier();
            tiers.put(candidate.getId(), tier);
            if (tier == 1 || tier == 2) {
                hasCategoryEvidence = true;
            } else if (tier == 3) {
                hasSignalFallback = true;
            }
        }
        if (hasCategoryEvidence) {
            return candidates.stream()
                    .filter(candidate -> {
                        int tier = tiers.getOrDefault(candidate.getId(), 0);
                        return tier == 1 || tier == 2;
                    })
                    .toList();
        }
        if (hasSignalFallback) {
            return candidates.stream()
                    .filter(candidate -> tiers.getOrDefault(candidate.getId(), 0) == 3)
                    .toList();
        }
        return List.of();
    }

    public static AdmissionDecision evaluateCandidate(
            String candidateText,
            QualificationPerformanceRetriever.PerformanceRequirementResult requirement) {
        if (!hasText(candidateText)) {
            return new AdmissionDecision(0, List.of("候选文本为空"));
        }
        String lower = candidateText.toLowerCase(Locale.ROOT);
        Map<String, BigDecimal> candidateAmounts = amountByCategory(candidateText);
        // 证据门：命中业绩信号词或具备金额证据；两者皆无（含纯人员/技术文案）直接拒绝
        if (!hasPerformanceSignal(lower) && candidateAmounts.isEmpty()) {
            return new AdmissionDecision(0, List.of("无业绩证据"));
        }

        Set<String> candidateCategories = PerformanceThresholdExtractor.detectCategories(candidateText);
        List<PerformanceThresholdExtractor.CategoryThreshold> thresholds =
                requirement != null && requirement.thresholds() != null ? requirement.thresholds() : List.of();
        Set<String> requiredCategories =
                requirement != null && requirement.targetCategories() != null
                        ? Set.copyOf(requirement.targetCategories()) : Set.of();

        PerformanceThresholdExtractor.CategoryThreshold metThreshold = null;
        PerformanceThresholdExtractor.CategoryThreshold belowThreshold = null;
        for (PerformanceThresholdExtractor.CategoryThreshold threshold : thresholds) {
            BigDecimal evidence = candidateAmounts.get(threshold.category());
            if (evidence == null) {
                continue;
            }
            if (evidence.compareTo(threshold.amountWan()) >= 0) {
                metThreshold = threshold;
            } else if (belowThreshold == null) {
                belowThreshold = threshold;
            }
        }
        if (metThreshold != null) {
            return new AdmissionDecision(1, List.of("满足资格项业绩门槛(" + label(metThreshold) + ")"));
        }
        if (belowThreshold != null) {
            return new AdmissionDecision(0, List.of("金额证据低于资格项门槛(" + label(belowThreshold) + ")"));
        }

        Set<String> hitCategories = new LinkedHashSet<>(candidateCategories);
        hitCategories.retainAll(requiredCategories);
        if (requiredCategories.isEmpty()) {
            return new AdmissionDecision(3, List.of("业绩信号准入"));
        }
        if (hitCategories.isEmpty()) {
            return new AdmissionDecision(3, List.of("业绩信号准入(未命中资格项品类)"));
        }
        String hitLabel = String.join("、", hitCategories);
        if (thresholds.isEmpty()) {
            return new AdmissionDecision(1, List.of("命中资格项业绩品类(" + hitLabel + ")"));
        }
        return new AdmissionDecision(2, List.of("命中资格项业绩品类(" + hitLabel + ")，缺少金额佐证"));
    }

    private static Map<String, BigDecimal> amountByCategory(String text) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (PerformanceThresholdExtractor.CategoryThreshold threshold : PerformanceThresholdExtractor.extract(text)) {
            amounts.merge(threshold.category(), threshold.amountWan(), BigDecimal::max);
        }
        return amounts;
    }

    private static String candidateText(KnowledgeCaseReadModel candidate) {
        return (Objects.toString(candidate.getRequirementRaw(), "") + " "
                + Objects.toString(candidate.getResponseText(), "") + " "
                + Objects.toString(candidate.getScoringPointTitle(), "")).toLowerCase(Locale.ROOT);
    }

    private static boolean hasPerformanceSignal(String lowerText) {
        for (String word : PERFORMANCE_SIGNAL_WORDS) {
            if (lowerText.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String label(PerformanceThresholdExtractor.CategoryThreshold threshold) {
        return threshold.category() + "≥" + threshold.amountWan().stripTrailingZeros().toPlainString() + "万元";
    }
}
