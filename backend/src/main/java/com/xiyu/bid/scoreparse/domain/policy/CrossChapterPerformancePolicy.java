// Input: 评分项 detail/dim, 标书资格项列表 qualificationRequirements, 标书原文 rawTenderText
// Output: 跨章节提取的业绩实质约束对象 CrossChapterPerformanceConstraint
// Pos: backend/src/main/java/com/xiyu/bid/scoreparse/domain/policy/ - 评分项跨章节穿透资格项纯核心策略（FP-Java）
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.scoreparse.domain.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项跨章节穿透资格项业绩检索策略（FP-Java 纯核心无副作用）。
 *
 * <p>业务价值：解决标书评分项中业绩条款往往仅为空泛引用（如“满足资格条件的业绩得基础分60分”）
 * 导致评分解析阶段无法获知品类、金额与证明要求的问题。本策略识别引用信号并精准穿透至
 * 标书的资格审查/初步评审条款中，提取真实的业绩门槛，供业绩库精准匹配使用。</p>
 */
public final class CrossChapterPerformancePolicy {

    /** 评分项中引用资格条件的特征词 */
    private static final List<String> QUALIFICATION_REFERENCE_INDICATORS = List.of(
            "资格条件", "资格审查", "初步评审", "符合资格", "满足资格", "资格要求", "资格条款"
    );

    /** 业绩品类特征词库（用于从资格项中识别品类） */
    private static final List<String> CATEGORY_FEATURE_WORDS = List.of(
            "工业品", "标准工业品", "mro", "劳保", "五金", "工具", "备品备件",
            "办公用品", "办公耗材", "办公设备", "纸张",
            "电商", "电子商城", "数字化商城", "数字化采购", "商城",
            "大型集团", "央企", "国企"
    );

    /** 金额匹配正则（如 1000万元、500万、20000000元） */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(万|亿)?元"
    );

    public record Constraint(
            boolean referencesQualification,
            String rawClauseText,
            BigDecimal minContractAmount,
            List<String> targetCategories,
            List<String> evidenceRequirements
    ) {
        public boolean hasEffectiveConstraint() {
            return minContractAmount != null || !targetCategories.isEmpty();
        }
    }

    /**
     * 判断评分项是否明确引用了资格条件。
     */
    public boolean referencesQualification(String dim, String detail) {
        if (!hasText(detail)) {
            return false;
        }
        String combined = ((dim != null ? dim : "") + " " + detail).toLowerCase(Locale.ROOT);
        for (String indicator : QUALIFICATION_REFERENCE_INDICATORS) {
            if (combined.contains(indicator.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从资格项列表和标书原文中穿透提取业绩实质约束。
     *
     * @param dim 评分维度
     * @param detail 评分细则
     * @param qualificationRequirements 已提取的资格项列表
     * @param rawTenderText 标书全文（备选）
     * @return 结构化约束对象
     */
    public Constraint extract(String dim, String detail, List<String> qualificationRequirements, String rawTenderText) {
        boolean referenced = referencesQualification(dim, detail);
        if (!referenced) {
            return new Constraint(false, null, null, List.of(), List.of());
        }

        // 收集资格审查中与“业绩/合同/供货”相关的条目
        List<String> matchedClauses = new ArrayList<>();
        if (qualificationRequirements != null) {
            for (String req : qualificationRequirements) {
                if (isPerformanceClause(req)) {
                    matchedClauses.add(req.trim());
                }
            }
        }

        // 若资格列表中未提取到，且有标书全文，尝试从资格审查章节中正则截取
        if (matchedClauses.isEmpty() && hasText(rawTenderText)) {
            matchedClauses.addAll(scanRawTenderForQualificationPerformance(rawTenderText));
        }

        if (matchedClauses.isEmpty()) {
            return new Constraint(true, null, null, List.of(), List.of());
        }

        String combinedClause = String.join("\n", matchedClauses);
        BigDecimal minAmount = extractMinAmount(combinedClause);
        List<String> categories = extractCategories(combinedClause);
        List<String> evidences = extractEvidences(combinedClause);

        return new Constraint(true, combinedClause, minAmount, categories, evidences);
    }

    private static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }

    private static boolean isPerformanceClause(String text) {
        if (!hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("业绩") || lower.contains("合同") || lower.contains("供货")
                || lower.contains("案例") || lower.contains("类似项目") || lower.contains("同类项目");
    }

    private static List<String> scanRawTenderForQualificationPerformance(String rawTenderText) {
        List<String> results = new ArrayList<>();
        // 匹配资格审查章节
        Pattern sectionPattern = Pattern.compile(
                "(?:初步评审|资格审查|资格评审|资格条件|投标人资格)[^\\n]*([\\s\\S]*?)(?=(?:\\n\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?(?:详细评审|技术需求|商务要求|投标文件格式)|$))",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = sectionPattern.matcher(rawTenderText);
        if (matcher.find()) {
            String sectionContent = matcher.group(1);
            String[] lines = sectionContent.split("\\n");
            for (String line : lines) {
                if (isPerformanceClause(line) && line.trim().length() > 6) {
                    results.add(line.trim());
                    if (results.size() >= 5) break;
                }
            }
        }
        return results;
    }

    private static BigDecimal extractMinAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        BigDecimal maxThreshold = null;
        while (matcher.find()) {
            String numStr = matcher.group(1);
            if (numStr != null && numStr.matches("\\d+(?:\\.\\d+)?")) {
                BigDecimal value = new BigDecimal(numStr);
                String unit = matcher.group(2);
                if ("万".equals(unit)) {
                    value = value.multiply(new BigDecimal("10000"));
                } else if ("亿".equals(unit)) {
                    value = value.multiply(new BigDecimal("100000000"));
                }
                if (maxThreshold == null || value.compareTo(maxThreshold) > 0) {
                    maxThreshold = value;
                }
            }
        }
        return maxThreshold;
    }

    private static List<String> extractCategories(String text) {
        Set<String> hits = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : CATEGORY_FEATURE_WORDS) {
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                hits.add(word);
            }
        }
        return List.copyOf(hits);
    }

    private static List<String> extractEvidences(String text) {
        List<String> evidences = new ArrayList<>();
        if (text.contains("发票")) evidences.add("发票");
        if (text.contains("合同")) evidences.add("合同关键页");
        if (text.contains("银行") || text.contains("流水")) evidences.add("银行回单/流水");
        if (text.contains("验收")) evidences.add("验收证明");
        return evidences;
    }
}
