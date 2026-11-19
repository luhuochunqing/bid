package com.xiyu.bid.casework.domain.policy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int EXPIRING_WARN_DAYS = 60;
    private static final Pattern GRADE_LEVEL_PATTERN = Pattern.compile("(?:第\\s*)?([一二三四五1-5])\\s*级");

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

    public record CertificateEvidence(String name, String level, String status, String fileUrl) {}

    private record StandardTarget(String code, String name, Pattern pattern) {
        StandardTarget(String code, String name, String regex) {
            this(code, name, Pattern.compile(regex));
        }
    }

    private static final StandardTarget GRADE_PROTECTION =
            new StandardTarget("GRADE_PROTECTION", "网络安全等级保护备案/测评", "等级保护|等保");

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
            GRADE_PROTECTION,
            new StandardTarget("BUSINESS_LICENSE", "企业法人营业执照", "(?i)(?:营业执照)")
    );

    private EnterpriseQualificationMatchPolicy() {}

    public static boolean coversRequiredStandards(String dim, String detail, List<CertificateEvidence> certificates) {
        String requirement = qualificationRequirement(dim, detail);
        List<StandardTarget> demands = demandedStandards(requirement);
        if (demands.isEmpty()) {
            return false;
        }
        int requiredSecurityGrade = demands.contains(GRADE_PROTECTION) ? extractGradeLevel(requirement) : 0;
        List<CertificateEvidence> usableCertificates = certificates.stream()
                .filter(cert -> ("VALID".equals(cert.status()) || "EXPIRING".equals(cert.status()))
                        && hasText(cert.fileUrl()) && hasText(cert.name()))
                .toList();
        return demands.stream().allMatch(target -> usableCertificates.stream().anyMatch(cert ->
                target.pattern().matcher(cert.name()).find()
                        && (!target.equals(GRADE_PROTECTION)
                        || certificateGrade(cert.name(), cert.level()) >= requiredSecurityGrade)));
    }

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
        String fullReqText = qualificationRequirement(dim, detail);
        String queryKeyword = hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";

        // 识别招标条款中要求的标准体系集合
        List<StandardTarget> demandedStandards = demandedStandards(fullReqText);
        int requiredSecurityGrade = demandedStandards.contains(GRADE_PROTECTION) ? extractGradeLevel(fullReqText) : 0;
        boolean requiresGradedCertificateOnly = requiredSecurityGrade > 0 && demandedStandards.size() == 1;
        Set<String> requirementEntities = PunctuationEntityExtractor.extractPunctuationEntities(dim, detail);

        List<MatchResult> results = new ArrayList<>();

        for (Candidate cand : candidates) {
            if (cand == null || cand.retired() || "RETIRED".equalsIgnoreCase(cand.status())) {
                continue;
            }

            int score = 0;
            List<String> reasons = new ArrayList<>();
            String candName = Objects.toString(cand.name(), "").toLowerCase(Locale.ROOT);
            String candScope = Objects.toString(cand.certScope(), "").toLowerCase(Locale.ROOT);

            boolean rejectedGradeMatch = requiredSecurityGrade > 0
                    && matchesStandard(GRADE_PROTECTION, candName, candScope)
                    && certificateGrade(candName, cand.level()) < requiredSecurityGrade;

            // 1. 命中具体标准体系
            boolean standardHit = false;
            for (StandardTarget target : demandedStandards) {
                if (!(rejectedGradeMatch && target.equals(GRADE_PROTECTION))
                        && matchesStandard(target, candName, candScope)) {
                    score = EXACT_STANDARD_MATCH_SCORE;
                    reasons.add("命中" + target.name() + "认证要求");
                    standardHit = true;
                    break;
                }
            }

            // 关键词不得绕过等保等级限制。其他明确标准的命中可以独立保留。
            if (!standardHit && (requiresGradedCertificateOnly || rejectedGradeMatch)) {
                continue;
            }

            // 2. 自由关键词/通用资质匹配（当未命中具体标准，但名称契合条款时）
            if (!standardHit) {
                for (String entity : requirementEntities) {
                    if (entity.length() >= 2 && candName.contains(entity.toLowerCase(Locale.ROOT))) {
                        score = TITLE_KEYWORD_MATCH_SCORE;
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
            boolean expiredByDate = cand.expiryDate() != null && cand.expiryDate().isBefore(today);
            if (expiredByDate || "EXPIRED".equalsIgnoreCase(cand.status())) {
                score = Math.min(score, 45);
                displayStatus = "EXPIRED";
                reasons.add(expiredByDate ? "证书已于 " + cand.expiryDate() + " 过期" : "资质库标记证书已过期");
            } else if (cand.expiryDate() != null) {
                long remainingDays = ChronoUnit.DAYS.between(today, cand.expiryDate());
                if (remainingDays <= EXPIRING_WARN_DAYS) {
                    displayStatus = "EXPIRING";
                    reasons.add("证书即将于 " + remainingDays + " 天后到期");
                } else {
                    reasons.add("证书在库且有效(至 " + cand.expiryDate() + ")");
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

    private static boolean matchesStandard(StandardTarget target, String name, String scope) {
        return target.pattern().matcher(name).find() || target.pattern().matcher(scope).find();
    }

    private static String qualificationRequirement(String dim, String detail) {
        return (Objects.toString(dim, "") + " " + Objects.toString(detail, "")).toLowerCase(Locale.ROOT);
    }

    private static List<StandardTarget> demandedStandards(String requirement) {
        return STANDARD_TARGETS.stream().filter(target -> target.pattern().matcher(requirement).find()).toList();
    }

    private static int certificateGrade(String name, String level) {
        int namedGrade = extractGradeLevel(name);
        int registeredGrade = extractGradeLevel(level);
        // 范围说明可能列出其他系统的等级。证书自身登记不一致时采用较低等级。
        return namedGrade > 0 && registeredGrade > 0
                ? Math.min(namedGrade, registeredGrade)
                : Math.max(namedGrade, registeredGrade);
    }

    private static int extractGradeLevel(String text) {
        if (!hasText(text)) {
            return 0;
        }
        var matcher = GRADE_LEVEL_PATTERN.matcher(text);
        int maxGrade = 0;
        while (matcher.find()) {
            char token = matcher.group(1).charAt(0);
            int numeric = Character.digit(token, 10);
            int grade = numeric >= 0 ? numeric : "一二三四五".indexOf(token) + 1;
            maxGrade = Math.max(maxGrade, grade);
        }
        return maxGrade;
    }
}
