// Input: 评分项 dim/detail 文本
// Output: 面积、金额、资质等级、数量、签约时间、关键词等约束提取
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-011 评分项约束提取

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项约束条件与关键词抽取工具（纯核心 POJO，无外部依赖）。
 */
public final class ScoreItemConstraintExtractor {

    private ScoreItemConstraintExtractor() {
    }

    /** 匹配请求构建时的噪声词（判定精确匹配，避免误伤业务词） */
    private static final Set<String> STOP_WORDS = Set.of(
            "具有", "提供", "要求", "投标", "满足", "相关", "以及", "包括", "不少于",
            "不低于", "以上", "以下", "单个", "其中", "有效", "期内", "且在", "并附",
            "万元", "平米", "平方米", "平方", "具备", "承诺", "服务", "证明", "需要",
            "根据", "评价", "评分", "评审", "情况", "排序", "按照", "对应", "分值", "满分",
            "优秀", "良好", "一般", "合格", "业绩", "供货", "合同", "案例", "仓库", "仓储");

    private static final Pattern AREA_PATTERN = Pattern.compile(
            "([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\\.[0-9]+)?)\\s*(万)?\\s*(㎡|平方米|平米|平方|m²|m2)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CONTRACT_AMOUNT_PATTERN = Pattern.compile(
            "(?:合同|协议)?金额(?:在|为|达到|需达到|要求)?\\s*"
                    + "(?:人民币\\s*)?"
                    + "([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\\.[0-9]+)?)\\s*"
                    + "(万|亿)?\\s*元?\\s*(?:[（(]含[）)]\\s*)?(?:以上|及以上|不少于|不低于|大于等于|≥)");
    private static final Pattern CONTRACT_AMOUNT_PREFIX_PATTERN = Pattern.compile(
            "(?:合同|协议)?金额\\s*(?:不少于|不低于|大于等于|≥)\\s*"
                    + "(?:人民币\\s*)?([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\\.[0-9]+)?)\\s*(万|亿)?\\s*元?");

    private static final List<String> HIGH_PRIORITY_DOMAIN_TERMS = List.of(
            "中央企业", "央企", "地方国企", "国有企业", "国企", "中央金融企业",
            "MRO", "工业品", "办公用品", "劳保", "备品备件", "电商", "电子商城"
    );

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{4})\\s*年(?:\\s*(\\d{1,2})\\s*月)?(?:\\s*(\\d{1,2})\\s*日)?\\s*(?:起|至|至今|以[后来]|及以后|及之后|~|-|—)");
    private static final Pattern NEAR_YEARS_PATTERN = Pattern.compile(
            "近\\s*([一二三两四五12345])\\s*年");

    public record EstimateCount(int count, boolean explicit) {}

    /** Only unambiguous resource counts are used; dates, amounts and incidental digits are not counts. */
    public static EstimateCount requiredForEstimate(String text, String category, BigDecimal weight) {
        if (text == null) return new EstimateCount(1, false);
        text = text.replaceAll("[（(][^）)]*[）)]", "");
        String number = "([0-9]+|[一二两三四五六七八九十])";
        Set<Integer> counts = new LinkedHashSet<>();
        var per = Pattern.compile("每(?:提供|增加)?\\s*" + number + "?\\s*(?:个|份|项|名|人|座|处)[^。；;\\n]{0,20}?(?:得|加)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*分").matcher(text);
        var cap = Pattern.compile("(?:最高|最多|满分)[^。；;\\n]{0,8}?([0-9]+(?:\\.[0-9]+)?)\\s*分").matcher(text);
        BigDecimal maximum = cap.find() ? new BigDecimal(cap.group(1)) : weight;
        while (per.find()) {
            int units = per.group(1) == null ? 1 : IncrementalCountScorePolicy.parseCount(per.group(1)).orElse(0);
            BigDecimal points = new BigDecimal(per.group(2));
            if (units > 0 && points.signum() > 0 && maximum != null && maximum.signum() > 0) {
                BigDecimal count = maximum.divide(points, 0, java.math.RoundingMode.CEILING).multiply(BigDecimal.valueOf(units));
                if (count.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0) counts.add(count.intValue());
            }
        }
        String resource = switch (category) {
            case "CERT" -> "证书|认证|资质"; case "WAREHOUSE" -> "仓库|仓储|库房";
            case "BRAND" -> "品牌|授权"; case "PERSON" -> "人员|员工|项目经理|工程师";
            default -> "业绩|合同|案例";
        };
        var direct = Pattern.compile("(?:需(?:要)?|要求|至少|不少于|提供|具有|具备)\\s*(?:提供|拥有)?\\s*" + number
                + "\\s*(?:个|份|项|名|人|座|处)\\s*(?:有效|可用|独立|在用|自建或租赁)?\\s*(?:" + resource + ")").matcher(text);
        while (direct.find()) {
            if (!text.substring(0, direct.start()).stripTrailing().endsWith("每"))
                IncrementalCountScorePolicy.parseCount(direct.group(1)).filter(n -> n > 0).ifPresent(counts::add);
        }
        return counts.size() == 1 ? new EstimateCount(counts.iterator().next(), true) : new EstimateCount(1, false);
    }

    /** 提取面积门槛：如 "不少于 5000 平方米"。抽失败或 ≤0 返回 null，禁止当成无门槛。 */
    public static BigDecimal extractMinArea(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = AREA_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String number = matcher.group(1).replace(",", "");
        BigDecimal value;
        try {
            value = new BigDecimal(number);
        } catch (NumberFormatException exception) {
            return null;
        }
        if ("万".equals(matcher.group(2))) {
            value = value.multiply(new BigDecimal("10000"));
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return value;
    }

    /** 提取业绩合同金额下限，统一换算为元；未识别到明确下限时返回 null。 */
    public static BigDecimal extractMinContractAmount(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = CONTRACT_AMOUNT_PATTERN.matcher(text);
        if (!matcher.find()) {
            matcher = CONTRACT_AMOUNT_PREFIX_PATTERN.matcher(text);
            if (!matcher.find()) {
                return null;
            }
        }
        BigDecimal value;
        try {
            value = new BigDecimal(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
        if ("万".equals(matcher.group(2))) {
            value = value.multiply(new BigDecimal("10000"));
        } else if ("亿".equals(matcher.group(2))) {
            value = value.multiply(new BigDecimal("100000000"));
        }
        return value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    /** 判断条款是否明确提出合同金额门槛，用于区分"没有金额要求"和"有要求但解析失败"。 */
    public static boolean hasContractAmountThreshold(String text) {
        return text != null
                && (text.contains("合同金额") || text.contains("协议金额"))
                && (text.contains("以上") || text.contains("不少于") || text.contains("不低于")
                || text.contains("大于等于") || text.contains("≥"));
    }

    /** 提取要求资质等级：如 "甲级" / "一级" / "CMMI 3" / "特级"。 */
    public static String extractLevel(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("([一二三四甲乙丙]级|CMMI\\s*[1-5]|L[1-5]|特级)").matcher(text);
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", "") : null;
    }

    /** 提取要求数量：如 "不少于 5 人" / "业绩 3 个"。 */
    public static Integer extractCount(String text, String unitPattern) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+)\\s*(?:" + unitPattern + ")").matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /**
     * 结合评分维度与细则提取业务关键词，优先提取领域专有词汇（如央企、国企、MRO、工业品等）。
     */
    public static List<String> extractKeywords(String dim, String text, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        String combined = (dim == null ? "" : dim) + " " + (text == null ? "" : text);
        for (String term : HIGH_PRIORITY_DOMAIN_TERMS) {
            if (combined.contains(term)) {
                keywords.add(term);
                if (keywords.size() >= limit) {
                    return List.copyOf(keywords);
                }
            }
        }
        if (dim != null && !dim.isBlank()) {
            for (String kw : extractKeywords(dim, limit)) {
                keywords.add(kw);
                if (keywords.size() >= limit) {
                    return List.copyOf(keywords);
                }
            }
        }
        if (text != null && !text.isBlank()) {
            for (String kw : extractKeywords(text, limit)) {
                keywords.add(kw);
                if (keywords.size() >= limit) {
                    return List.copyOf(keywords);
                }
            }
        }
        return List.copyOf(keywords);
    }

    /** 提取业绩签约时间下限：如 "时间为2022年1月至今" 或 "近三年"。 */
    public static LocalDate extractSignedAfter(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_RANGE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
                int day = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 1;
                return LocalDate.of(year, month, day);
            } catch (NumberFormatException | java.time.DateTimeException ignored) {
            }
        }
        Matcher nearMatcher = NEAR_YEARS_PATTERN.matcher(text);
        if (nearMatcher.find()) {
            String numStr = nearMatcher.group(1);
            int years = switch (numStr) {
                case "一", "1" -> 1;
                case "二", "两", "2" -> 2;
                case "三", "3" -> 3;
                case "四", "4" -> 4;
                case "五", "5" -> 5;
                default -> 3;
            };
            return LocalDate.now().minusYears(years);
        }
        return null;
    }

    /**
     * 提取业绩所需数量：优先匹配 "每增加/提供1个...得X分，最高得Y分" 计算满分所需份数，
     * 其次匹配 "不少于X个/份/项"，最后降级为首次出现的数字+单位。
     */
    public static Integer extractProjectRequiredCount(String text, BigDecimal weight) {
        if (text == null || text.isBlank()) {
            return null;
        }
        var incrementalRule = IncrementalCountScorePolicy.parse(text);
        if (incrementalRule.isPresent()) {
            return incrementalRule.get().requiredCountForMaximumScore();
        }
        Matcher perMatch = Pattern.compile("每(?:增加|提供|具备)?\\s*(\\d+|[一二两三四五六七八九十]+)\\s*(?:个|项|份)[^，。；;\\n]*?得\\s*(\\d+(?:\\.\\d+)?)\\s*分(?:[,，\\s]*?(?:最高|满分|最多)[^，。；;\\n]*?得?\\s*(\\d+(?:\\.\\d+)?)\\s*分)?").matcher(text);
        if (perMatch.find()) {
            var unitCount = IncrementalCountScorePolicy.parseCount(perMatch.group(1));
            if (unitCount.isPresent()) {
                int unit = unitCount.orElse(0);
                BigDecimal perScore = new BigDecimal(perMatch.group(2));
                String maxScoreStr = perMatch.group(3);
                BigDecimal maxScore = (maxScoreStr != null) ? new BigDecimal(maxScoreStr) : weight;
                if (perScore.compareTo(BigDecimal.ZERO) > 0 && maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0) {
                    int calcCount = maxScore.divide(perScore, 0, java.math.RoundingMode.CEILING).intValue()
                            * unit;
                    if (calcCount > 0) return calcCount;
                }
            }
        }
        Matcher minMatch = Pattern.compile("(?:不少于|提供|具备|具有|完成)\\s*(\\d+)\\s*(?:个|项|份)").matcher(text);
        if (minMatch.find()) {
            return Integer.valueOf(minMatch.group(1));
        }
        return extractCount(text, "个|项|份");
    }

    /** 提取业务关键词：按非字母数字汉字切分，过滤停用词/短 token/纯数字，去重保序。 */
    public static List<String> extractKeywords(String text, int limit) {
        if (text == null || text.isBlank() || limit <= 0) {
            return List.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : text.split("[^\\p{IsHan}a-zA-Z0-9]+")) {
            if (token.length() < 2 || token.matches("\\d+") || token.matches("\\d+(?:\\.\\d+)?分") || STOP_WORDS.contains(token)) {
                continue;
            }
            keywords.add(token);
            if (keywords.size() >= limit) {
                break;
            }
        }
        return List.copyOf(keywords);
    }
}
