// Input: 评分维度名称
// Output: 顶层大类判定与候选名称检查
// Pos: scoreparse/domain — 纯核心，无框架依赖
package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Pattern;

/**
 * 评分大类分类器（纯核心，无框架依赖）。
 * <p>识别评分项的维度名称是否仅为顶层大类分类（如"商务"、"商务评分（20"等），
 * 以及空名称、纯序号、"评分项 N"兜底名或含表格分隔符的残缺名称，
 * 避免无效名称覆盖具体的叶子评分项细则标题或直接落库。
 */
public final class ScoreCategoryClassifier {

    private static final Pattern TOP_LEVEL_NAMES = Pattern.compile(
            "^(商务|技术|价格|报价|综合|评议|商务分|技术分|价格分|报价分"
                    + "|商务部分|技术部分|价格部分|报价部分|综合评审|商务评议|技术评议"
                    + "|business|technical|price|commercial)$");
    private static final Pattern GENERIC_TITLE_NAMES = Pattern.compile(
            "^(商务评分|技术评分|价格评分|报价评分|综合评分|详细评审|评分标准|评审标准)$");
    private static final Pattern PRICE_TITLE = Pattern.compile(
            "^(?:(?:价格|报价)(?:分|部分|评分)?|price)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_SCORING_OPERATION = Pattern.compile(
            "[=＝]|(?:得|加|扣)\\s*\\d+(?:\\.\\d+)?\\s*分");
    private static final Pattern PURE_NUMBER_OR_CODE = Pattern.compile("^(?:\\d+(?:[.-]\\d+)*|[一二三四五六七八九十]+)$");
    private static final Pattern ALPHANUMERIC_CODE = Pattern.compile("^[A-Za-z]\\d+(?:[.-]\\d+)*$");
    private static final Pattern PURE_WEIGHT_CELL = Pattern.compile(
            "^[~≈]?\\s*\\d+(?:\\.\\d+)?\\s*(?:分|分值|[%％])?\\s*$");
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("^评分项(?:\\s*[A-Za-z]?\\d+(?:[.-]\\d+)*)?$");
    private static final Pattern SCORE_SUFFIX = Pattern.compile(
            "\\s*[（(](?:满分|最高)?\\s*\\d+(?:\\.\\d+)?\\s*分[）)]\\s*$");

    private ScoreCategoryClassifier() {
    }

    /** 分类用于来源身份等领域判定。保留既有空维度和大类语义。 */
    public static boolean isTopLevelCategory(String dim) {
        if (dim == null || dim.isBlank()) {
            return true;
        }
        String clean = dim.replaceAll("[\\s（(][^）)]*[）)]", "").trim().toLowerCase(java.util.Locale.ROOT);
        return TOP_LEVEL_NAMES.matcher(clean).matches();
    }

    /** 名称恢复时需要从细则另找具体标题的维度。 */
    public static boolean needsSpecificTitle(String dim) {
        if (isInvalidOrPlaceholderName(dim)) {
            return true;
        }
        String clean = dim.replaceAll("[（(][^）)]*[）)]", "").replaceAll("\\s+", "");
        return isTopLevelCategory(clean) || GENERIC_TITLE_NAMES.matcher(clean).matches();
    }

    /** 报价项可沿用原文大类名，前提是已取得实际计分公式。结构与分值由快照另外校验。 */
    public static boolean isPublishableName(String name, ScoreFact fact) {
        if (!needsSpecificTitle(name)) {
            return true;
        }
        if (isInvalidOrPlaceholderName(name) || fact == null
                || !PRICE_TITLE.matcher(name.replaceAll("[（(][^）)]*[）)]|\\s+", "")).matches()) {
            return false;
        }
        var diagnostics = fact.sourceMetadata().diagnostics();
        String rule = fact.sourceText();
        return !diagnostics.contains("FULL_RULE_UNIT_UNRESOLVED") && !diagnostics.contains("UNVERIFIED_CLOSURE")
                && PRICE_SCORING_OPERATION.matcher(rule).find()
                && ScoreTableSectionPolicy.isPriceFormula(java.util.List.of(rule.replaceAll("得分\\s*[=＝]", "得分=")));
    }

    /**
     * 判断名称是否为空、纯序号、分值、未闭合截断、含表格分隔符或占位名。
     */
    public static boolean isInvalidOrPlaceholderName(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String trimmed = name.trim();
        String label = stripTrailingScore(trimmed);
        return label.isEmpty() || PURE_NUMBER_OR_CODE.matcher(label).matches()
                || isWeightCell(label)
                || isAlphanumericCode(label) || PLACEHOLDER_NAME.matcher(label).matches()
                || trimmed.contains("|") || hasUnclosedBracket(trimmed);
    }

    static String stripTrailingScore(String text) {
        return SCORE_SUFFIX.matcher(text.trim()).replaceFirst("").trim();
    }

    static boolean isAlphanumericCode(String name) {
        return ALPHANUMERIC_CODE.matcher(name).matches();
    }

    public static boolean isWeightCell(String text) {
        return PURE_WEIGHT_CELL.matcher(text).matches();
    }

    /**
     * 检测文本是否存在未成对闭合的括号（如 OCR/表格列截断导致的 "商务评分（20"）。
     */
    static boolean hasUnclosedBracket(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '（') {
                depth++;
            } else if (c == ')' || c == '）') {
                depth--;
                if (depth < 0) {
                    return true;
                }
            }
        }
        return depth != 0;
    }
}
