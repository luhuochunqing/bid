package com.xiyu.bid.ai.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class TenderAiAnalysisPolicy {

    private static final LocalDate DEFAULT_ANALYSIS_DATE = LocalDate.of(1970, 1, 1);

    private TenderAiAnalysisPolicy() {
    }

    public static AnalysisResult evaluate(AnalysisInput input) {
        AnalysisInput safeInput = input == null ? AnalysisInput.empty() : input;
        List<DimensionRating> dimensions = safeInput.dimensionScores() == null ? List.of() : safeInput.dimensionScores();
        List<String> weaknesses = safeList(safeInput.weaknesses());
        List<String> recommendations = safeList(safeInput.recommendations());
        RiskLevel riskLevel = safeInput.riskLevel() == null ? RiskLevel.MEDIUM : safeInput.riskLevel();

        List<RiskItem> risks = new ArrayList<>();
        int itemCount = Math.max(weaknesses.size(), recommendations.size());
        for (int i = 0; i < itemCount; i++) {
            String desc = i < weaknesses.size() ? weaknesses.get(i) : "AI 识别到的潜在风险";
            String action = i < recommendations.size() ? recommendations.get(i) : "建议继续补强相关材料";
            risks.add(new RiskItem(resolveRiskItemLevel(riskLevel, i), desc, action));
        }

        List<AutoTask> autoTasks = recommendations.stream()
            .map(item -> new AutoTask(
                "AI-" + Math.abs(item.hashCode()),
                item,
                "AI助手",
                analysisDate(safeInput.analysisDate()).plusDays(3).toString(),
                riskLevel == RiskLevel.HIGH ? "high" : "medium"
            ))
            .toList();

        return new AnalysisResult(
            safeInput.score(),
            recommendations.isEmpty() ? defaultSuggestion(riskLevel) : recommendations.get(0),
            dimensions.stream()
                .map(item -> new DimensionRating(resolveDimensionName(item.dimension()), item.score()))
                .toList(),
            risks,
            autoTasks
        );
    }

    public static String resolveDimensionName(String dimension) {
        return switch (dimension) {
            case "Technical" -> "需求匹配";
            case "Financial" -> "竞争态势";
            case "Timing" -> "交付能力";
            case "Team" -> "客户关系";
            case "Resources" -> "资质满足";
            case "Risk" -> "竞争态势";
            default -> dimension;
        };
    }

    public static String resolveRiskItemLevel(RiskLevel riskLevel, int index) {
        return riskLevel == RiskLevel.HIGH && index == 0 ? "high" : "medium";
    }

    public static String defaultSuggestion(RiskLevel riskLevel) {
        RiskLevel safeRiskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
        return switch (safeRiskLevel) {
            case LOW -> "整体匹配度较高，建议积极推进";
            case MEDIUM -> "建议补强关键短板后继续推进";
            case HIGH -> "风险偏高，需先完成重点问题整改";
        };
    }

    public static String resolveRiskLevelText(RiskLevel riskLevel) {
        return (riskLevel == null ? RiskLevel.MEDIUM : riskLevel).name();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static LocalDate analysisDate(LocalDate analysisDate) {
        return analysisDate == null ? DEFAULT_ANALYSIS_DATE : analysisDate;
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public record AnalysisInput(
        Integer score,
        RiskLevel riskLevel,
        List<DimensionRating> dimensionScores,
        List<String> weaknesses,
        List<String> recommendations,
        LocalDate analysisDate
    ) {
        public AnalysisInput {
            dimensionScores = dimensionScores == null ? List.of() : List.copyOf(dimensionScores);
            weaknesses = weaknesses == null ? List.of() : List.copyOf(weaknesses);
            recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        }

        static AnalysisInput empty() {
            return new AnalysisInput(null, RiskLevel.MEDIUM, List.of(), List.of(), List.of(), DEFAULT_ANALYSIS_DATE);
        }
    }

    public record AnalysisResult(
        Integer winScore,
        String suggestion,
        List<DimensionRating> dimensionScores,
        List<RiskItem> risks,
        List<AutoTask> autoTasks
    ) {
        public AnalysisResult {
            dimensionScores = dimensionScores == null ? List.of() : List.copyOf(dimensionScores);
            risks = risks == null ? List.of() : List.copyOf(risks);
            autoTasks = autoTasks == null ? List.of() : List.copyOf(autoTasks);
        }
    }

    public record DimensionRating(String dimension, Integer score) {
    }

    public record RiskItem(String level, String desc, String action) {
    }

    public record AutoTask(String id, String title, String owner, String dueDate, String priority) {
    }
}
