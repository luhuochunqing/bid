package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Pure certificate scoring with explicit corporate kind, grade and validity evidence. */
public final class CertificateScorePolicy {
    public static final String ENGINE_VERSION = "certificate-score-v2";
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

    private static Evidence corporateKind(Fact fact, String identity) {
        String name = CertificateScoreRule.normalize(fact.name());
        if ("PERSONNEL".equals(fact.category()) || name.matches(".*(内审员|审核员|培训|人员|合格证|资格证).*")) return Evidence.FAIL;
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
