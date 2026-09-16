// Input: 资格项/评分项业绩条款文本（可能包含多个品类与金额）
// Output: 结构化的品类-金额门槛对（金额统一归一为万元）
// Pos: casework/domain/policy/ - 业绩金额门槛解析纯核心策略（FP-Java 无外部依赖）
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.domain.policy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：从业绩条款文本中解析“品类-金额门槛”对。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 *
 * <p>解析口径：按逗号/分号/句号/换行分段；金额按“逗号内就近 + 前向『的』标记”归属品类，
 * 避免跨品类串台；单金额多品类视为共享门槛；缺少金额的品类段向后寻找最近的金额段；
 * 区间金额取下界（如 2000-4500 万元 取 2000）；带“不超过/以下/以内”上限修饰的金额
 * 不计入业绩门槛；同品类多金额取最大值；金额统一换算为万元。</p>
 */
public final class PerformanceThresholdExtractor {

    public static final String CATEGORY_OFFICE = "办公用品";
    public static final String CATEGORY_INDUSTRIAL = "标准工业品";

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
                    + "(?:\\s*[-–—~～至]\\s*(?:\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?))?"
                    + "\\s*(亿元|亿|万元|万|千元|千|元)");
    private static final Pattern SEGMENT_SPLIT_PATTERN = Pattern.compile("(?<!\\d)[，,]|[；;。\\n]");
    private static final List<String> UPPER_BOUND_MARKERS = List.of("不超过", "不高于", "以下", "以内", "最高");
    private static final int UPPER_BOUND_WINDOW = 8;
    private static final int FORWARD_MARKER_WINDOW = 8;
    private static final String FORWARD_MARKER = "的";

    private static final List<String> OFFICE_WORDS = List.of("办公用品", "办公耗材", "办公文具", "办公设备", "办公类");
    private static final List<String> INDUSTRIAL_WORDS = List.of("标准工业品", "工业品", "mro", "劳保", "五金", "机电");

    public record CategoryThreshold(String category, BigDecimal amountWan, String rawText) {}

    private record CategoryHit(String category, int index) {}

    private record AmountHit(BigDecimal amountWan, int start, int end) {}

    private PerformanceThresholdExtractor() {}

    public static List<CategoryThreshold> extract(String requirementText) {
        if (!hasText(requirementText)) {
            return List.of();
        }
        List<CategoryThreshold> thresholds = new ArrayList<>();
        Set<String> currentCategories = new LinkedHashSet<>();
        for (String segment : SEGMENT_SPLIT_PATTERN.split(requirementText)) {
            if (!hasText(segment)) {
                continue;
            }
            List<CategoryHit> categories = findCategoryHits(segment.toLowerCase(Locale.ROOT));
            List<AmountHit> amounts = findAmounts(segment);
            if (!categories.isEmpty() && !amounts.isEmpty()) {
                thresholds.addAll(pairWithinSegment(segment, categories, amounts));
                currentCategories.clear();
                for (CategoryHit category : categories) {
                    currentCategories.add(category.category());
                }
            } else if (!categories.isEmpty()) {
                currentCategories.clear();
                for (CategoryHit category : categories) {
                    currentCategories.add(category.category());
                }
            } else if (!amounts.isEmpty() && !currentCategories.isEmpty()) {
                BigDecimal maxAmount = amounts.stream()
                        .map(AmountHit::amountWan)
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
                String trimmed = segment.trim();
                for (String category : currentCategories) {
                    thresholds.add(new CategoryThreshold(category, maxAmount, trimmed));
                }
            }
        }
        return List.copyOf(dedupe(thresholds));
    }

    /**
     * 检测文本中出现的归一品类（不要求出现金额），供准入证据识别使用。
     */
    public static Set<String> detectCategories(String text) {
        if (!hasText(text)) {
            return Set.of();
        }
        Set<String> categories = new LinkedHashSet<>();
        for (CategoryHit hit : findCategoryHits(text.toLowerCase(Locale.ROOT))) {
            categories.add(hit.category());
        }
        return Set.copyOf(categories);
    }

    private static List<CategoryThreshold> pairWithinSegment(
            String segment, List<CategoryHit> categories, List<AmountHit> amounts) {
        String trimmed = segment.trim();
        if (amounts.size() == 1) {
            // 单金额多品类共享（如“办公用品及工业品年度供应业绩金额不低于3000万元”）
            List<CategoryThreshold> shared = new ArrayList<>();
            for (CategoryHit category : categories) {
                shared.add(new CategoryThreshold(category.category(), amounts.get(0).amountWan(), trimmed));
            }
            return shared;
        }
        Map<String, BigDecimal> amountByCategory = new LinkedHashMap<>();
        for (AmountHit amount : amounts) {
            CategoryHit category = resolveCategory(segment, amount, categories);
            if (category != null) {
                amountByCategory.merge(category.category(), amount.amountWan(), BigDecimal::max);
            }
        }
        List<CategoryThreshold> thresholds = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : amountByCategory.entrySet()) {
            thresholds.add(new CategoryThreshold(entry.getKey(), entry.getValue(), trimmed));
        }
        return thresholds;
    }

    private static CategoryHit resolveCategory(String segment, AmountHit amount, List<CategoryHit> categories) {
        CategoryHit marked = categoryAfterByMarker(segment, amount, categories);
        if (marked != null) {
            return marked;
        }
        CategoryHit nearestBefore = null;
        int beforeDistance = Integer.MAX_VALUE;
        CategoryHit nearestAfter = null;
        int afterDistance = Integer.MAX_VALUE;
        for (CategoryHit category : categories) {
            if (category.index() <= amount.start()) {
                int distance = amount.start() - category.index();
                if (distance < beforeDistance) {
                    beforeDistance = distance;
                    nearestBefore = category;
                }
            } else {
                int distance = category.index() - amount.end();
                if (distance < afterDistance) {
                    afterDistance = distance;
                    nearestAfter = category;
                }
            }
        }
        return nearestBefore != null ? nearestBefore : nearestAfter;
    }

    private static CategoryHit categoryAfterByMarker(String segment, AmountHit amount, List<CategoryHit> categories) {
        for (CategoryHit category : categories) {
            if (category.index() < amount.end()) {
                continue;
            }
            int gapLength = category.index() - amount.end();
            if (gapLength > FORWARD_MARKER_WINDOW) {
                continue;
            }
            if (segment.substring(amount.end(), category.index()).contains(FORWARD_MARKER)) {
                return category;
            }
        }
        return null;
    }

    private static List<CategoryThreshold> dedupe(List<CategoryThreshold> thresholds) {
        Map<String, CategoryThreshold> byCategory = new LinkedHashMap<>();
        for (CategoryThreshold threshold : thresholds) {
            byCategory.merge(threshold.category(), threshold, (a, b) ->
                    a.amountWan().compareTo(b.amountWan()) >= 0 ? a : b);
        }
        return new ArrayList<>(byCategory.values());
    }

    private static List<CategoryHit> findCategoryHits(String lowerText) {
        List<CategoryHit> hits = new ArrayList<>(collectHits(lowerText, OFFICE_WORDS, CATEGORY_OFFICE));
        hits.addAll(collectHits(lowerText, INDUSTRIAL_WORDS, CATEGORY_INDUSTRIAL));
        hits.sort(Comparator.comparingInt(CategoryHit::index));
        return hits;
    }

    private static List<CategoryHit> collectHits(String lowerText, List<String> words, String category) {
        List<CategoryHit> hits = new ArrayList<>();
        for (String word : words) {
            int from = 0;
            int index;
            while ((index = lowerText.indexOf(word, from)) >= 0) {
                hits.add(new CategoryHit(category, index));
                from = index + word.length();
            }
        }
        return hits;
    }

    private static List<AmountHit> findAmounts(String segment) {
        List<AmountHit> amounts = new ArrayList<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(segment);
        while (matcher.find()) {
            if (hasUpperBoundPrefix(segment, matcher.start())) {
                continue;
            }
            amounts.add(new AmountHit(toWan(matcher.group(1), matcher.group(2)), matcher.start(), matcher.end()));
        }
        return amounts;
    }

    private static boolean hasUpperBoundPrefix(String segment, int amountStart) {
        int from = Math.max(0, amountStart - UPPER_BOUND_WINDOW);
        String prefix = segment.substring(from, amountStart);
        for (String marker : UPPER_BOUND_MARKERS) {
            if (prefix.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal toWan(String number, String unit) {
        BigDecimal value = new BigDecimal(number.replace(",", ""));
        return switch (unit) {
            case "亿元", "亿" -> value.multiply(BigDecimal.valueOf(10000));
            case "千元", "千" -> value.divide(BigDecimal.TEN);
            case "万元", "万" -> value;
            default -> value.divide(BigDecimal.valueOf(10000));
        };
    }
}
