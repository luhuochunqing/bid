// Input: 评分项 dim/detail 文本
// Output: 知识库类别（CERT/PERSON/PROJECT/WAREHOUSE/BRAND/OTHER）+ 提取关键词/数量
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-011 分型计分前置

package com.xiyu.bid.scoreparse.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项知识库类别判定策略（spec 041 FR-011 分型计分前置）。
 *
 * <p>判定优先级：BRAND &gt; WAREHOUSE &gt; PERSON &gt; PROJECT &gt; CERT &gt; OTHER
 * （人员项常含证书字样、仓储项常含资质字样，故具体业务类别优先于 CERT 兜底）。
 */
public class KnowledgeCategoryPolicy {

    public static final String CATEGORY_CERT = "CERT";
    public static final String CATEGORY_PERSON = "PERSON";
    public static final String CATEGORY_PROJECT = "PROJECT";
    public static final String CATEGORY_WAREHOUSE = "WAREHOUSE";
    public static final String CATEGORY_BRAND = "BRAND";
    public static final String CATEGORY_OTHER = "OTHER";

    private static final List<CategoryRule> RULES = List.of(
            new CategoryRule(CATEGORY_BRAND, Pattern.compile("品牌|授权|厂家|制造商")),
            new CategoryRule(CATEGORY_WAREHOUSE, Pattern.compile("仓库|仓储|库房|库容")),
            new CategoryRule(CATEGORY_PERSON,
                    Pattern.compile("人员|项目经理|项目负责人|项目团队|拟派|团队")),
            new CategoryRule(CATEGORY_PROJECT, Pattern.compile("业绩|合同|案例|类似项目")),
            new CategoryRule(CATEGORY_CERT, Pattern.compile("证书|资质|认证|许可证|ISO|CMMI",
                    Pattern.CASE_INSENSITIVE)));

    /** 匹配请求构建时的噪声词（判定精确匹配，避免误伤业务词） */
    private static final Set<String> STOP_WORDS = Set.of(
            "具有", "提供", "要求", "投标", "满足", "相关", "以及", "包括", "不少于",
            "不低于", "以上", "以下", "单个", "其中", "有效", "期内", "且在", "并附",
            "万元", "平米", "平方米", "平方", "具备", "承诺", "服务", "证明", "需要");

    /** 知识库类别判定：dim + detail 联合匹配，命中优先级最高的规则。 */
    public String categorize(String dim, String detail) {
        String text = (dim == null ? "" : dim) + " " + (detail == null ? "" : detail);
        for (CategoryRule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                return rule.category();
            }
        }
        return CATEGORY_OTHER;
    }

    /** 提取要求资质等级：如 "甲级" / "一级" / "CMMI 3" / "特级"。 */
    public String extractLevel(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("([一二三四甲乙丙]级|CMMI\\s*[1-5]|L[1-5]|特级)").matcher(text);
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", "") : null;
    }

    /** 提取要求数量：如 "不少于 5 人" / "业绩 3 个"。 */
    public Integer extractCount(String text, String unitPattern) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+)\\s*(?:" + unitPattern + ")").matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** 提取业务关键词：按非字母数字汉字切分，过滤停用词/短 token/纯数字，去重保序。 */
    public List<String> extractKeywords(String text, int limit) {
        if (text == null || text.isBlank() || limit <= 0) {
            return List.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : text.split("[^\\p{IsHan}a-zA-Z0-9]+")) {
            if (token.length() < 2 || token.matches("\\d+") || STOP_WORDS.contains(token)) {
                continue;
            }
            keywords.add(token);
            if (keywords.size() >= limit) {
                break;
            }
        }
        return List.copyOf(keywords);
    }

    private record CategoryRule(String category, Pattern pattern) {
    }
}
