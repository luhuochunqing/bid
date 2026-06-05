package com.xiyu.bid.projectworkflow.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScoreDraftSeedFactory {

    private static final Pattern SCORE_IN_RULE = Pattern.compile(
            "(最高\\s*\\d+(?:\\.\\d+)?分|满分\\s*\\d+(?:\\.\\d+)?分|得\\s*\\d+(?:\\.\\d+)?分|\\d+(?:\\.\\d+)?分)"
    );

    private ScoreDraftSeedFactory() {
    }

    static DraftSeed buildSeed(String scoreItemTitle, String baseTitle, String ruleText, String scoreText) {
        String normalizedRule = normalizeRuleText(ruleText);
        String normalizedScore = normalizeScoreText(scoreText, normalizedRule);
        String taskAction = inferTaskAction(baseTitle, normalizedRule);
        return new DraftSeed(
                scoreItemTitle,
                normalizedRule,
                normalizedScore,
                taskAction,
                buildTaskTitle(taskAction, scoreItemTitle, normalizedScore),
                buildDescription(scoreItemTitle, normalizedScore, normalizedRule),
                inferDeliverables(baseTitle, normalizedRule)
        );
    }

    static String inferScoreText(String ruleText) {
        if (ruleText == null || ruleText.isBlank()) {
            return null;
        }
        Matcher matcher = SCORE_IN_RULE.matcher(ruleText);
        String preferred = null;
        BigDecimal maxValue = BigDecimal.valueOf(-1);
        String maxScoreText = null;
        while (matcher.find()) {
            String candidate = matcher.group(1).replace("得", "").trim();
            if (candidate.contains("最高") || candidate.contains("满分")) {
                preferred = candidate;
            }
            BigDecimal numericValue = extractNumericScore(candidate);
            if (numericValue.compareTo(maxValue) > 0) {
                maxValue = numericValue;
                maxScoreText = candidate;
            }
        }
        if (preferred != null) {
            return formatScoreText(preferred);
        }
        return maxScoreText != null ? formatScoreText(maxScoreText) : null;
    }

    static String formatScoreText(String raw) {
        String text = Optional.ofNullable(raw).orElse("").trim();
        if (text.isBlank()) {
            return "未明确分值";
        }
        if (text.contains("分")) {
            return text.replaceAll("\\s+", "");
        }
        return text + "分";
    }

    private static String normalizeRuleText(String ruleText) {
        return Optional.ofNullable(ruleText)
                .orElse("")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    private static String normalizeScoreText(String scoreText, String ruleText) {
        if (scoreText != null && !scoreText.isBlank()) {
            return formatScoreText(scoreText);
        }
        String inferred = inferScoreText(ruleText);
        return inferred != null ? inferred : "未明确分值";
    }

    private static BigDecimal extractNumericScore(String scoreText) {
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(Optional.ofNullable(scoreText).orElse(""));
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        return BigDecimal.ZERO;
    }

    private static String inferTaskAction(String title, String ruleText) {
        String text = (Optional.ofNullable(title).orElse("") + " " + Optional.ofNullable(ruleText).orElse(""))
                .toLowerCase(Locale.ROOT);
        if (containsAny(text, "资质", "证书", "认证", "许可")) {
            return "准备";
        }
        if (containsAny(text, "业绩", "案例", "合同", "财务报表")) {
            return "整理";
        }
        if (containsAny(text, "报价", "折扣率", "价格", "结算周期")) {
            return "复核";
        }
        if (containsAny(text, "方案", "实施", "服务", "仓储", "配送", "对接")) {
            return "编写";
        }
        return "处理";
    }

    private static String buildTaskTitle(String action, String title, String scoreText) {
        return action + title + "（" + scoreText + "）";
    }

    private static String buildDescription(String scoreItemTitle,
                                           String scoreValueText,
                                           String scoreRuleText) {
        return """
                评分目标：%s
                分值规则：%s
                评分原文：%s
                执行要求：请准备该项得分所需材料，并确保响应内容可以直接支撑评审打分。
                完成标准：材料齐全、论据清晰、可直接支撑该项得分判断。
                """.formatted(scoreItemTitle, scoreValueText, scoreRuleText);
    }

    private static List<String> inferDeliverables(String title, String ruleText) {
        String text = Optional.ofNullable(title).orElse("") + "\n" + Optional.ofNullable(ruleText).orElse("");
        Set<String> deliverables = new LinkedHashSet<>();

        if (containsAny(text, "资质", "证书", "认证", "许可证")) {
            deliverables.add("资质证书复印件");
            deliverables.add("有效期说明");
        }
        if (containsAny(text, "业绩", "案例", "合同")) {
            deliverables.add("合同关键页");
            deliverables.add("验收证明");
            deliverables.add("项目简介");
        }
        if (containsAny(text, "财务", "营业收入", "审计")) {
            deliverables.add("审计报告或财务报表");
        }
        if (containsAny(text, "方案", "实施", "仓储", "配送", "对接", "服务")) {
            deliverables.add("方案正文");
            deliverables.add("实施或服务说明");
        }
        if (containsAny(text, "价格", "报价", "折扣率", "结算周期")) {
            deliverables.add("报价表");
            deliverables.add("测算依据");
            deliverables.add("承诺函或说明");
        }
        if (deliverables.isEmpty()) {
            deliverables.add("响应说明材料");
        }
        return new ArrayList<>(deliverables);
    }

    private static boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }
}
