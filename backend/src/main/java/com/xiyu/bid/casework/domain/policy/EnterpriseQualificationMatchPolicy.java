package com.xiyu.bid.casework.domain.policy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：企业资质证书智能匹配与推荐策略。
 *
 * <p>遵循 FP-Java 规范：无状态纯函数、无 Spring/DB 依赖、入参不可变、100% 可单测。</p>
 */
public final class EnterpriseQualificationMatchPolicy {

    private static final int EXACT_STANDARD_MATCH_SCORE = 95;
    private static final int TITLE_KEYWORD_MATCH_SCORE = 85;
    private static final int SCOPE_KEYWORD_MATCH_SCORE = 75;
    private static final int EXPIRING_WARN_DAYS = 60;

    public record Candidate(
            Long id,
            String name,
            String level,
            String certificateNo,
            String issuer,
            LocalDate issueDate,
            LocalDate expiryDate,
            String status,
            String certScope,
            String fileUrl,
            String fileName,
            boolean retired
    ) {}

    public record MatchResult(
            Long id,
            String name,
            String level,
            String certificateNo,
            String issuer,
            String issueDate,
            String expiryDate,
            String status,
            String certScope,
            String fileUrl,
            String fileName,
            int matchScore,
            String matchLabel,
            List<String> matchReasons
    ) {}

    private record StandardTarget(String code, String name, String regex) {}

    private static final List<StandardTarget> STANDARD_TARGETS = List.of(
            new StandardTarget("ISO9001", "质量管理体系(ISO9001)", "(?i)(?:iso\\s*9001|gb/?t\\s*19001|质量管理体系)"),
            new StandardTarget("ISO14001", "环境管理体系(ISO14001)", "(?i)(?:iso\\s*14001|gb/?t\\s*24001|环境管理体系)"),
            new StandardTarget("ISO45001", "职业健康安全管理体系(ISO45001)", "(?i)(?:iso\\s*45001|gb/?t\\s*45001|ohsas\\s*18001|职业健康安全)"),
            new StandardTarget("ISO27001", "信息安全管理体系(ISO27001)", "(?i)(?:iso\\s*27001|gb/?t\\s*22080|信息安全管理)"),
            new StandardTarget("ISO20000", "信息技术服务管理(ISO20000)", "(?i)(?:iso\\s*20000|信息技术服务管理)"),
            new StandardTarget("AAA", "企业信用评价(AAA级)", "(?i)(?:aaa|3a|信用评价|企业信用|质量信用|品牌信誉)"),
            new StandardTarget("AFTER_SALES", "商品售后服务评价", "(?i)(?:商品售后|售后服务认证|五星级售后)"),
            new StandardTarget("SAFETY_LICENSE", "安全生产许可证", "(?i)(?:安全生产许可|安许)"),
            new StandardTarget("HIGH_TECH", "高新技术企业证书", "(?i)(?:高新技术企业|高新企业)"),
            new StandardTarget("SPECIALIZED", "专精特新企业证书", "(?i)(?:专精特新)"),
            new StandardTarget("CMMI", "CMMI软件能力成熟度认证", "(?i)(?:cmmi)"),
            new StandardTarget("ITSS", "ITSS信息技术服务标准", "(?i)(?:itss)"),
            new StandardTarget("BUSINESS_LICENSE", "企业法人营业执照", "(?i)(?:营业执照)")
    );

    private EnterpriseQualificationMatchPolicy() {}

    /**
     * 针对评分项维度与细则要求，从企业资质库候选集中匹配最符合条件的证书。
     */
    public static List<MatchResult> matchCertificates(
            String dim, String detail, String keyword,
            List<Candidate> candidates, LocalDate asOfDate) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        LocalDate today = asOfDate != null ? asOfDate : LocalDate.now();
        String fullReqText = (Objects.toString(dim, "") + " " + Objects.toString(detail, "")).toLowerCase(Locale.ROOT);
        String queryKeyword = hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";

        // 识别招标条款中要求的标准体系集合
        List<StandardTarget> demandedStandards = new ArrayList<>();
        for (StandardTarget target : STANDARD_TARGETS) {
            if (Pattern.compile(target.regex()).matcher(fullReqText).find()) {
                demandedStandards.add(target);
            }
        }

        List<MatchResult> results = new ArrayList<>();

        for (Candidate cand : candidates) {
            if (cand == null || cand.retired() || "RETIRED".equalsIgnoreCase(cand.status())) {
                continue;
            }

            int score = 0;
            List<String> reasons = new ArrayList<>();
            String candName = Objects.toString(cand.name(), "").toLowerCase(Locale.ROOT);
            String candScope = Objects.toString(cand.certScope(), "").toLowerCase(Locale.ROOT);

            // 1. 命中具体标准体系
            boolean standardHit = false;
            for (StandardTarget target : demandedStandards) {
                if (Pattern.compile(target.regex()).matcher(candName).find()
                        || Pattern.compile(target.regex()).matcher(candScope).find()) {
                    score = Math.max(score, EXACT_STANDARD_MATCH_SCORE);
                    reasons.add("命中" + target.name() + "认证要求");
                    standardHit = true;
                    break;
                }
            }

            // 2. 自由关键词/通用资质匹配（当未命中具体标准，但名称契合条款时）
            if (!standardHit) {
                Set<String> entities = PunctuationEntityExtractor.extractPunctuationEntities(dim, detail);
                for (String entity : entities) {
                    if (entity.length() >= 2 && candName.contains(entity.toLowerCase(Locale.ROOT))) {
                        score = Math.max(score, TITLE_KEYWORD_MATCH_SCORE);
                        reasons.add("证书名称契合条款关键词「" + entity + "」");
                        break;
                    }
                }
            }

            // 3. 搜索词加成
            if (!queryKeyword.isEmpty() && (candName.contains(queryKeyword) || candScope.contains(queryKeyword))) {
                score = Math.min(100, (score > 0 ? score + 5 : 80));
                reasons.add("命中搜索词「" + queryKeyword + "」");
            }

            // 若完全未命中任何资质要素，则跳过
            if (score < 60) {
                continue;
            }

            // 4. 有效期与状态核验
            String displayStatus = "VALID";
            if (cand.expiryDate() != null) {
                if (cand.expiryDate().isBefore(today)) {
                    score = Math.min(score, 45); // 过期惩罚
                    displayStatus = "EXPIRED";
                    reasons.add("证书已于 " + cand.expiryDate() + " 过期");
                } else {
                    long remainingDays = ChronoUnit.DAYS.between(today, cand.expiryDate());
                    if (remainingDays <= EXPIRING_WARN_DAYS) {
                        displayStatus = "EXPIRING";
                        reasons.add("证书即将于 " + remainingDays + " 天后到期");
                    } else {
                        reasons.add("证书在库且有效(至 " + cand.expiryDate() + ")");
                    }
                }
            } else {
                reasons.add("长期有效/未注明有效截止期");
            }

            String matchLabel = score >= 80 ? "优质" : (score >= 60 ? "良好" : "一般");

            results.add(new MatchResult(
                    cand.id(),
                    cand.name(),
                    cand.level(),
                    cand.certificateNo(),
                    cand.issuer(),
                    cand.issueDate() != null ? cand.issueDate().toString() : null,
                    cand.expiryDate() != null ? cand.expiryDate().toString() : null,
                    displayStatus,
                    cand.certScope(),
                    cand.fileUrl(),
                    cand.fileName(),
                    score,
                    matchLabel,
                    reasons
            ));
        }

        // 排序：分数降序 -> 有效优先 -> 有附件图片优先
        return results.stream()
                .sorted(Comparator.comparing(MatchResult::matchScore, Comparator.reverseOrder())
                        .thenComparing(r -> !"EXPIRED".equals(r.status()), Comparator.reverseOrder())
                        .thenComparing(r -> hasText(r.fileUrl()), Comparator.reverseOrder())
                        .thenComparing(MatchResult::expiryDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
