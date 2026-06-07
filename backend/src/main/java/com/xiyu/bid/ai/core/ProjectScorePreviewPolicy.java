package com.xiyu.bid.ai.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProjectScorePreviewPolicy {

    private static final BigDecimal BUDGET_THRESHOLD = new BigDecimal("500");

    private ProjectScorePreviewPolicy() {
    }

    public static PreviewResult evaluate(PreviewInput input) {
        PreviewInput safeInput = input == null ? PreviewInput.empty() : input;
        List<String> tags = safeTags(safeInput.tags());
        BigDecimal budget = safeInput.budget() == null ? BigDecimal.ZERO : safeInput.budget();

        int winScore = 60;
        if ("政府".equals(safeInput.industry())) {
            winScore += 10;
        }
        if ("央国企".equals(safeInput.industry())) {
            winScore += 5;
        }
        if (tags.contains("信创")) {
            winScore += 5;
        }
        if (tags.contains("智慧城市")) {
            winScore += 5;
        }
        if (budget.compareTo(BUDGET_THRESHOLD) > 0) {
            winScore -= 5;
        }
        winScore = Math.max(0, Math.min(100, winScore));

        String winLevel = winScore >= 80 ? "high" : winScore >= 60 ? "medium" : "low";

        List<CategoryCoverage> categories = List.of(
            category("技术", 40, tags.contains("信创") ? 32 : 28, 40, tags.contains("信创") ? 80 : 70,
                tags.contains("信创") ? List.of("大数据平台") : List.of("物联网架构方案", "大数据平台")),
            category("商务", 30, 25, 30, 83, List.of()),
            category("案例", 20, tags.contains("智慧城市") ? 14 : 8, 20, tags.contains("智慧城市") ? 70 : 40,
                tags.contains("智慧城市") ? List.of() : List.of("智慧城市案例")),
            category("服务", 10, 7, 10, 70, List.of("运维承诺"))
        );

        List<GapItem> gapItems = categories.stream()
            .flatMap(category -> category.gaps().stream()
                .map(gap -> new GapItem(
                    category.name(),
                    gap,
                    resolveGapRequirement(gap),
                    "missing"
                )))
            .toList();

        List<GeneratedTask> generatedTasks = gapItems.stream()
            .sorted(Comparator.comparing(GapItem::category))
            .map(item -> new GeneratedTask(
                "补齐" + item.scorePoint(),
                "技术".equals(item.category()) ? "high" : "medium",
                item.required(),
                true
            ))
            .toList();

        List<SummaryRisk> risks = gapItems.stream()
            .limit(3)
            .map(item -> new SummaryRisk(
                "技术".equals(item.category()) ? "high" : "medium",
                item.scorePoint() + " 仍缺失，可能影响评分"
            ))
            .toList();

        List<String> suggestions = new ArrayList<>();
        suggestions.add("优先补充关键评分点材料，先处理高权重项");
        if (tags.contains("信创")) {
            suggestions.add("突出国产化兼容和信创生态证明材料");
        }
        if (!tags.contains("智慧城市")) {
            suggestions.add("补充智慧城市类案例，提升案例项覆盖率");
        }

        return new PreviewResult(winScore, winLevel, categories, gapItems, risks, suggestions, generatedTasks);
    }

    public static String resolveGapRequirement(String gap) {
        return switch (gap) {
            case "物联网架构方案" -> "架构图+技术说明";
            case "大数据平台" -> "平台架构+性能指标";
            case "智慧城市案例" -> "至少1个同类案例";
            case "运维承诺" -> "3年免费运维承诺";
            default -> "补充相关证明材料";
        };
    }

    private static CategoryCoverage category(String name, int weight, int covered, int total, int percentage, List<String> gaps) {
        return new CategoryCoverage(name, weight, covered, total, percentage, gaps);
    }

    private static List<String> safeTags(List<String> tags) {
        return tags == null ? List.of() : List.copyOf(tags);
    }

    public record PreviewInput(
        String projectName,
        String industry,
        BigDecimal budget,
        List<String> tags,
        Long projectId,
        Long tenderId
    ) {
        public PreviewInput {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }

        static PreviewInput empty() {
            return new PreviewInput(null, null, BigDecimal.ZERO, List.of(), null, null);
        }
    }

    public record PreviewResult(
        Integer winScore,
        String winLevel,
        List<CategoryCoverage> scoreCategories,
        List<GapItem> gapItems,
        List<SummaryRisk> risks,
        List<String> suggestions,
        List<GeneratedTask> generatedTasks
    ) {
        public PreviewResult {
            scoreCategories = scoreCategories == null ? List.of() : List.copyOf(scoreCategories);
            gapItems = gapItems == null ? List.of() : List.copyOf(gapItems);
            risks = risks == null ? List.of() : List.copyOf(risks);
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
            generatedTasks = generatedTasks == null ? List.of() : List.copyOf(generatedTasks);
        }
    }

    public record CategoryCoverage(String name, Integer weight, Integer covered, Integer total, Integer percentage, List<String> gaps) {
        public CategoryCoverage {
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
        }
    }

    public record GapItem(String category, String scorePoint, String required, String status) {
    }

    public record SummaryRisk(String level, String content) {
    }

    public record GeneratedTask(String name, String priority, String suggestion, Boolean selected) {
    }
}
