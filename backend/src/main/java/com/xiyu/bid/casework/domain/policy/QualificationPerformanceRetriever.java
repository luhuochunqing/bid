// Input: 评分项 dim/detail, 标书资格项列表 qualificationRequirements, 标书原文 rawTenderText
// Output: 提取的资格项业绩实质要求对象（PerformanceRequirementResult）或 Optional.empty()
// Pos: casework/domain/policy/ - 业绩评分项穿透资格项业绩检索纯核心策略（FP-Java 无外部依赖）
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：业绩评分项穿透资格项业绩要求检索策略。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 *
 * <p>业务价值：解决标书评分项中业绩条款往往仅为空泛引用（如“详见初步评审第7条供应商资格”）
 * 而导致相似度计算落空的问题。本策略在识别到业绩类评分项时，精准穿透至标书的资格审查/初步评审
 * 条款中提取真实的业绩门槛（时间、金额、品类、客户层级），并严格防范全文档泛化切词污染。</p>
 */
public final class QualificationPerformanceRetriever {

    private static final List<String> PERF_DIM_KEYWORDS = List.of(
            "业绩", "同类项目", "类似项目", "供货业绩", "合同业绩", "供应商业绩", "企业业绩"
    );

    private static final List<String> PERF_DETAIL_INDICATORS = List.of(
            "有效业绩", "资格条件规定的", "资格条款要求", "初步评审", "供应商资格", "资格审查", "类似业绩", "同类业绩"
    );

    /** 资格项章节正则锚点（限定在资格审查/初步评审范围，坚决阻断全文档非资格区域噪声） */
    private static final Pattern QUALIFICATION_SECTION_PATTERN = Pattern.compile(
            "(?:(?:初步评审|资格审查|资格评审|资格性审查|供应商资格|资格条件|投标人资格|申请人资格)[^\\n]*)([\\s\\S]*?)(?=(?:\\n\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?(?:详细评审|技术需求|商务要求|投标文件格式|第四章|第五章|第六章|第七章)|$))",
            Pattern.CASE_INSENSITIVE
    );

    /** 资格项中属于业绩条款的判定正则（必须同时出现业绩或类似供货要求，以及时间/数量/金额等实质条件） */
    private static final Pattern PERF_CLAUSE_PATTERN = Pattern.compile(
            "[^。；;\\n]*?(?:业绩|类似项目|供货记录|合同)[^。；;\\n]*?(?:年|项|万|元|发票|电商|供应|不低于|不少于|累计)[^。；;\\n]*",
            Pattern.CASE_INSENSITIVE
    );

    /** 提取核心关键词特征池 */
    private static final List<String> CORE_FEATURE_WORDS = List.of(
            "大型集团", "集团级", "电商", "电商化", "办公用品", "标准工业品", "工业品", "mro",
            "发票", "近三年", "近3年", "自然年", "2000万", "4500万", "累计金额", "年度供应"
    );

    public record PerformanceRequirementResult(
            String matchedRequirementText,
            List<String> keywords,
            List<String> targetCategories,
            List<String> amountThresholds
    ) {}

    /**
     * 判断当前评分项是否属于“业绩相关评分项”。
     */
    public boolean isPerformanceScoreItem(String dim, String detail) {
        String dimLower = dim != null ? dim.trim().toLowerCase(Locale.ROOT) : "";
        for (String kw : PERF_DIM_KEYWORDS) {
            if (dimLower.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        if (!hasText(detail)) {
            return false;
        }

        String detailLower = detail.trim().toLowerCase(Locale.ROOT);
        // 如果详情中明确提及业绩并且指向资格条款或初步评审
        if (detailLower.contains("业绩")) {
            for (String indicator : PERF_DETAIL_INDICATORS) {
                if (detailLower.contains(indicator.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 穿透检索当前项目的资格项业绩实质要求。
     *
     * @param dim 评分项维度
     * @param detail 评分项详情
     * @param qualificationRequirements 项目已解析的资格项列表
     * @param rawTenderText 标书原文（可选，备选检索源）
     * @return 资格项业绩要求结构化结果
     */
    public Optional<PerformanceRequirementResult> retrieve(
            String dim,
            String detail,
            List<String> qualificationRequirements,
            String rawTenderText) {

        if (!isPerformanceScoreItem(dim, detail)) {
            return Optional.empty();
        }

        // 1. 优先从已结构化的资格项列表提取
        if (qualificationRequirements != null && !qualificationRequirements.isEmpty()) {
            for (String req : qualificationRequirements) {
                if (isSubstantivePerformanceRequirement(req)) {
                    return Optional.of(buildResult(req));
                }
            }
        }

        // 2. 次选：从标书原文的资格审查/初步评审章节提取
        if (hasText(rawTenderText)) {
            Matcher sectionMatcher = QUALIFICATION_SECTION_PATTERN.matcher(rawTenderText);
            while (sectionMatcher.find()) {
                String sectionContent = sectionMatcher.group(1);
                if (hasText(sectionContent)) {
                    // 在资格章节中扫描包含业绩的完整段落或条款
                    String[] paragraphs = sectionContent.split("\\n+");
                    for (String para : paragraphs) {
                        if (isSubstantivePerformanceRequirement(para)) {
                            return Optional.of(buildResult(para.trim()));
                        }
                    }

                    Matcher clauseMatcher = PERF_CLAUSE_PATTERN.matcher(sectionContent);
                    if (clauseMatcher.find()) {
                        return Optional.of(buildResult(clauseMatcher.group().trim()));
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean isSubstantivePerformanceRequirement(String text) {
        if (!hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("业绩") && !lower.contains("供货") && !lower.contains("同类项目")) {
            return false;
        }
        // 必须含有实质门槛指标特征（排除如“经营业绩良好”、“展示公司业绩”等纯描述背景词）
        boolean hasThresholdIndicator = lower.contains("年") || lower.contains("项") || lower.contains("万")
                || lower.contains("元") || lower.contains("发票") || lower.contains("不低于")
                || lower.contains("不少于") || lower.contains("累计") || lower.contains("合同")
                || lower.contains("电商") || lower.contains("供应");

        return hasThresholdIndicator;
    }

    private PerformanceRequirementResult buildResult(String reqText) {
        Set<String> hitKeywords = new LinkedHashSet<>();
        List<String> targetCategories = new ArrayList<>();
        List<String> amountThresholds = new ArrayList<>();

        String lower = reqText.toLowerCase(Locale.ROOT);

        for (String feature : CORE_FEATURE_WORDS) {
            if (lower.contains(feature.toLowerCase(Locale.ROOT))) {
                hitKeywords.add(feature);
            }
        }

        // 提取典型品类
        if (lower.contains("办公用品") || lower.contains("办公耗材")) {
            targetCategories.add("办公用品");
            hitKeywords.add("办公用品");
        }
        if (lower.contains("标准工业品") || lower.contains("工业品") || lower.contains("mro")) {
            targetCategories.add("标准工业品");
            hitKeywords.add("标准工业品");
        }

        // 提取典型金额
        Matcher amountMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?(?:万|亿|千)?元)").matcher(reqText);
        while (amountMatcher.find()) {
            amountThresholds.add(amountMatcher.group(1));
            hitKeywords.add(amountMatcher.group(1));
        }

        return new PerformanceRequirementResult(
                reqText,
                List.copyOf(hitKeywords),
                List.copyOf(targetCategories),
                List.copyOf(amountThresholds)
        );
    }

    public record QualPerfBonusResult(int score, List<String> reasons) {}

    /**
     * 计算资格项业绩要求对候选案例的实质加权。
     */
    public static QualPerfBonusResult calculatePerformanceBonus(
            String qualRequirementText, List<String> qualKeywords, String candidateText) {
        if (!hasText(qualRequirementText) || !hasText(candidateText)) {
            return new QualPerfBonusResult(0, List.of());
        }

        int bonus = 0;
        List<String> reasons = new ArrayList<>();
        String candLower = candidateText.toLowerCase(Locale.ROOT);
        String qualLower = qualRequirementText.toLowerCase(Locale.ROOT);

        // 1. 品类实质匹配（办公用品、标准工业品、MRO、电商化等）
        List<String> hitCategories = new ArrayList<>();
        if ((qualLower.contains("办公用品") || qualLower.contains("办公耗材"))
                && (candLower.contains("办公用品") || candLower.contains("办公耗材"))) {
            hitCategories.add("办公用品");
        }
        if ((qualLower.contains("标准工业品") || qualLower.contains("工业品") || qualLower.contains("mro"))
                && (candLower.contains("工业品") || candLower.contains("mro") || candLower.contains("劳保") || candLower.contains("五金"))) {
            hitCategories.add("工业品");
        }
        if ((qualLower.contains("电商") || qualLower.contains("商城"))
                && (candLower.contains("电商") || candLower.contains("商城") || candLower.contains("数字化采购"))) {
            hitCategories.add("电商化");
        }
        if (!hitCategories.isEmpty()) {
            bonus += Math.min(12, hitCategories.size() * 6);
            reasons.add("命中资格项业绩品类(" + String.join("、", hitCategories) + ")");
        }

        // 2. 客户/企业层级实质匹配（大型集团、央企、国企、集团级等）
        if ((qualLower.contains("大型集团") || qualLower.contains("集团级") || qualLower.contains("央企") || qualLower.contains("国企"))
                && (candLower.contains("集团") || candLower.contains("央企") || candLower.contains("国企") || candLower.contains("世界500强"))) {
            bonus += 8;
            reasons.add("命中资格项客户层级(大型集团/央国企)");
        }

        // 3. 证明材料与门槛要素（发票、合同、年度供应、累计金额）
        List<String> hitThresholds = new ArrayList<>();
        if (qualLower.contains("发票") && candLower.contains("发票")) {
            hitThresholds.add("发票");
        }
        if ((qualLower.contains("年度") || qualLower.contains("年内")) && (candLower.contains("年度") || candLower.contains("年内") || candLower.contains("长协"))) {
            hitThresholds.add("年度合同");
        }
        if (qualKeywords != null) {
            for (String kw : qualKeywords) {
                if (kw.contains("万") || kw.contains("亿")) {
                    if (candLower.contains(kw.toLowerCase(Locale.ROOT))) {
                        hitThresholds.add(kw);
                        break;
                    }
                }
            }
        }
        if (!hitThresholds.isEmpty()) {
            bonus += 5;
            reasons.add("契合资格项门槛凭据(" + String.join("、", hitThresholds) + ")");
        }

        return new QualPerfBonusResult(Math.min(25, bonus), reasons);
    }
}
