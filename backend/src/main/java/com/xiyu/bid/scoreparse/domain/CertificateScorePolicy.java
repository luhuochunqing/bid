package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure certificate scoring with explicit corporate kind, grade and validity evidence. */
public final class CertificateScorePolicy {
    public static final String ENGINE_VERSION = "certificate-score-v2";
    private static final Pattern PERSONNEL_CERTIFICATE_NAME = Pattern.compile(".*(内审员|审核员|培训|人员|合格证|资格证).*");
    private CertificateScorePolicy() {}
    public record Fact(Long id, String name, String level, LocalDate expiryDate, String status,
                       boolean retired, LocalDate issueDate, String category) {
        public Fact(Long id, String name, String level, LocalDate expiryDate, String status, boolean retired, LocalDate issueDate) {
            this(id, name, level, expiryDate, status, retired, issueDate, null);
        }
        public Fact(String name, String level, LocalDate expiryDate, String status, boolean retired, LocalDate issueDate) {
            this(null, name, level, expiryDate, status, retired, issueDate, null);
        }
    }
    public record Assessment(BigDecimal score, String status, boolean kbHit, String basis) {}
    private enum Evidence { PASS, FAIL, UNKNOWN }

    // REUSE-OK: backend/src/main/java/com/xiyu/bid/scoreparse/domain/ProjectEvidenceRequirementPolicy.java — certificate composition, validity and level semantics are distinct.
    public static Assessment assess(String dim, String detail, BigDecimal weight, LocalDate asOf, List<Fact> facts) {
        var rule = CertificateScoreRule.parse(dim, detail, weight);
        if (!rule.supported() || asOf == null) return new Assessment(null, "PENDING", false,
                "证书评分规则暂不支持或存在未解释条件，需人工确认");
        int confirmed = 0, unknown = 0;
        boolean hit = false;
        List<String> proofs = new ArrayList<>();
        for (var requirement : rule.requirements()) {
            List<Fact> candidates = facts.stream().filter(f -> CertificateScoreRule.identifies(f.name(), requirement.identity())).toList();
            hit |= !candidates.isEmpty();
            boolean uncertain = false;
            Fact selected = null;
            for (var fact : candidates) {
                if (fact.retired() || List.of("RETIRED", "EXPIRED").contains(fact.status() == null ? "" : fact.status())
                        || fact.expiryDate() != null && fact.expiryDate().isBefore(asOf)
                        || fact.issueDate() != null && fact.issueDate().isAfter(asOf)) continue;
                Evidence kind = corporateKind(fact, requirement.identity());
                Evidence grade = grade(fact, requirement);
                if (kind == Evidence.FAIL || grade == Evidence.FAIL) continue;
                if (kind == Evidence.UNKNOWN || grade == Evidence.UNKNOWN || fact.expiryDate() == null
                        || fact.status() == null || !List.of("IN_STOCK", "EXPIRING", "VALID").contains(fact.status())) uncertain = true;
                else selected = fact;
            }
            if (selected != null) confirmed++; else if (uncertain) unknown++;
            String evidence = selected != null ? "满足（证书" + (selected.id() == null ? "" : " #" + selected.id())
                    + "：" + selected.name() + "，有效至 " + selected.expiryDate() + "）"
                    : uncertain ? "证书种类、等级或有效期证据待确认"
                    : candidates.isEmpty() ? "缺少证书" : "证书种类/等级/有效期/状态不满足";
            proofs.add(requirement.identity() + (requirement.level().isEmpty() ? "" : "/" + requirement.level()) + "：" + evidence);
        }
        BigDecimal score = rule.scores().getOrDefault(confirmed, BigDecimal.ZERO);
        boolean knownMissingMandatory = rule.scores().size() == 1
                && rule.scores().containsKey(rule.requirements().size())
                && confirmed + unknown < rule.requirements().size();
        if (unknown > 0 && !knownMissingMandatory)
            return new Assessment(null, "PENDING", hit, String.join("；", proofs));
        return new Assessment(score, score.signum() == 0 ? "DANGER" : score.compareTo(weight) == 0 ? "OK" : "PENDING", hit, String.join("；", proofs));
    }

    /** Stage-1 rough estimate only; the strict assess method and its proof semantics stay unchanged. */
    public static Assessment assessSimple(String dim, String detail, BigDecimal weight, LocalDate asOf, List<Fact> facts) {
        if (weight == null || asOf == null) return new Assessment(null, "PENDING", false, "粗估输入不完整，未计算");
        List<Fact> eligible = facts.stream().filter(f -> f.name() != null && !f.name().isBlank())
                .filter(f -> !f.retired() && List.of("IN_STOCK", "EXPIRING", "VALID").contains(f.status() == null ? "" : f.status()))
                .filter(f -> f.expiryDate() == null || !f.expiryDate().isBefore(asOf))
                .filter(f -> f.issueDate() == null || !f.issueDate().isAfter(asOf))
                .filter(f -> !List.of("PERSONNEL", "PRODUCT").contains(f.category() == null ? "" : f.category()))
                .filter(f -> !PERSONNEL_CERTIFICATE_NAME.matcher(CertificateScoreRule.normalize(f.name())).matches())
                .toList();
        var awards = CertificateScoreRule.independentAwards(dim, detail, weight);
        if (!awards.isEmpty()) return assessIndependentAwards(weight, asOf, eligible, awards);

        List<Fact> usable = eligible.stream().filter(f -> f.expiryDate() != null).toList();
        var identities = CertificateScoreRule.identitiesForEstimate(dim, detail);
        var target = identities.isEmpty() ? ScoreItemConstraintExtractor.requiredForEstimate(detail, "CERT", weight)
                : new ScoreItemConstraintExtractor.EstimateCount(identities.size(), true);
        List<String> hits = identities.isEmpty()
                ? usable.stream().flatMap(f -> {
                    var known = CertificateScoreRule.identitiesForEstimate(f.name(), "");
                    return (known.isEmpty() ? List.of(CertificateScoreRule.normalize(f.name())) : known).stream();
                }).distinct().toList()
                : identities.stream().filter(id -> usable.stream().anyMatch(f -> CertificateScoreRule.matchesEstimateIdentity(f.name(), id)
                        && grade(f, CertificateScoreRule.estimateRequirement(id, detail)) == Evidence.PASS)).toList();
        int count = hits.size();
        BigDecimal score = new PartialScorePolicy().estimateByCount(weight, count, target.count());
        String status = new ScoreStatusPolicy().evaluate(score, weight, "OBJECTIVE", false);
        String basis = "知识库粗估（资质证书）：要求" + target.count() + "项，命中" + count + "项，满足比例"
                + BigDecimal.valueOf(Math.min(count, target.count()) * 100L).divide(BigDecimal.valueOf(target.count()), 2, RoundingMode.HALF_UP)
                + "%，预计" + score + "/" + weight + "；" + (target.explicit() ? "按明确要求数量" : "未指定明确数量，按有无粗估")
                + "；命中资料：" + (hits.isEmpty() ? "无" : String.join("、", hits))
                + "；仅计有效且未退役的企业证书，有效期不明不计入；未核验正式评审附加条件";
        return new Assessment(score, status, count > 0, basis);
    }

    private static Assessment assessIndependentAwards(
            BigDecimal weight, LocalDate asOf, List<Fact> eligible, Map<String, BigDecimal> awards) {
        BigDecimal score = BigDecimal.ZERO;
        int hits = 0;
        boolean unknownExpiry = false;
        List<String> proofs = new ArrayList<>();
        for (var award : awards.entrySet()) {
            String identity = award.getKey();
            Fact candidate = null;
            for (Fact fact : eligible) {
                if (!identity.equals(CertificateScoreRule.ledgerSystemIdentity(fact.name()))) continue;
                candidate = fact;
                if (fact.expiryDate() != null) break;
            }
            if (candidate != null && candidate.expiryDate() != null) {
                hits++;
                score = score.add(award.getValue());
                proofs.add(identity + " +" + award.getValue() + "分（"
                        + (candidate.id() == null ? "" : "#" + candidate.id() + " ") + candidate.name()
                        + "，有效至 " + candidate.expiryDate() + "）");
            } else if (candidate != null) {
                unknownExpiry = true;
                proofs.add(identity + " 有效期待核验");
            } else {
                proofs.add(identity + " 无有效记录");
            }
        }
        String status = new ScoreStatusPolicy().evaluate(score, weight, "OBJECTIVE", unknownExpiry);
        // Use persisted precision for the estimate and its evidence.
        BigDecimal estimate = score.scale() > 2 ? score.setScale(2, RoundingMode.HALF_UP) : score;
        String basis = "知识库粗估（资质证书）：要求" + awards.size() + "项，命中" + hits
                + "项，按证书分值预计" + estimate + "/" + weight + "分；"
                + String.join("；", proofs) + "；核验日 " + asOf + "；投标人持证主体待核验";
        return new Assessment(estimate, status, hits > 0, basis);
    }

    private static Evidence corporateKind(Fact fact, String identity) {
        String name = CertificateScoreRule.normalize(fact.name());
        if ("PERSONNEL".equals(fact.category()) || PERSONNEL_CERTIFICATE_NAME.matcher(name).matches()) return Evidence.FAIL;
        if ("PRODUCT".equals(fact.category())) return Evidence.UNKNOWN;
        if (identity.startsWith("ISO")) {
            return name.matches(CertificateScoreRule.isoCertificatePattern()) ? Evidence.PASS : Evidence.UNKNOWN;
        }
        String grade = identity.equals("品牌信誉") || identity.equals("售后服务")
                ? "(?:[0-9一二三四五六七八九十百]+星(?:级)?)?" : "(?:(?:[0-9]+A|A+)(?:级)?)?";
        String pattern = CertificateScoreRule.honorBase(identity) + "(?:认证)?" + grade + "(?:认证(?:证书)?|证书)?";
        return name.matches(pattern) ? Evidence.PASS : Evidence.UNKNOWN;
    }

    private static Evidence grade(Fact fact, CertificateScoreRule.Requirement requirement) {
        if (requirement.level().isEmpty()) return Evidence.PASS;
        String token = CertificateScoreRule.normalize(fact.level());
        if (token.isEmpty()) {
            String name = CertificateScoreRule.normalize(fact.name());
            String base = CertificateScoreRule.honorBase(requirement.identity());
            var matcher = Pattern.compile(base + "(?:认证)?(.*?)(?:认证证书|认证|证书)?").matcher(name);
            if (!matcher.matches()) return Evidence.UNKNOWN;
            token = matcher.group(1);
        }
        if (requirement.level().equals("五星")) {
            if (token.matches("(?:五星|5星)(?:级)?")) return Evidence.PASS;
            if (token.matches("(?:[1-4一二三四]星)(?:级)?")) return Evidence.FAIL;
        } else {
            if (token.matches("(?:3A|AAA)(?:级)?")) return Evidence.PASS;
            if (token.matches("(?:1A|2A|A|AA)(?:级)?")) return Evidence.FAIL;
        }
        return Evidence.UNKNOWN;
    }
}
