package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.domain.CertificateScorePolicy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;
import com.xiyu.bid.scoreparse.dto.CertMatchRequest;
import com.xiyu.bid.scoreparse.dto.CertMatchedItem;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 资质证书匹配（POST /api/knowledge/cert/match，FR-009）。
 *
 * <p>规则：name 包含任一关键词 AND（requiredLevel 空 OR level 包含）
 * AND（requireValidUntil 空 OR expiry_date ≥ 该日期 AND status != RETIRED）。
 * 命中行 status=EXPIRED 或 expiry_date < 今天 → expired=true（算命中但标记）。
 */
@Service
@RequiredArgsConstructor
public class CertMatchService {

    private final BusinessQualificationJpaRepository repository;

    public record ScoreEstimate(CertificateScorePolicy.Assessment assessment, String inputFingerprint) {}

    public ScoreEstimate estimateScore(String dim, String detail, BigDecimal weight, LocalDate asOf) {
        var facts = scoreFacts();
        return new ScoreEstimate(CertificateScorePolicy.assess(dim, detail, weight, asOf, facts),
                fingerprint(dim, detail, weight, asOf, facts));
    }

    public ScoreEstimate estimateSimpleScore(String dim, String detail, BigDecimal weight, LocalDate asOf) {
        var facts = scoreFacts();
        return new ScoreEstimate(CertificateScorePolicy.assessSimple(dim, detail, weight, asOf, facts),
                fingerprint("simple-estimate-v1:" + dim, detail, weight, asOf, facts));
    }

    public String simpleFingerprintForScore(String dim, String detail, BigDecimal weight, LocalDate asOf) {
        return fingerprint("simple-estimate-v1:" + dim, detail, weight, asOf, scoreFacts());
    }

    public String fingerprintForScore(String dim, String detail, BigDecimal weight, LocalDate asOf) {
        return fingerprint(dim, detail, weight, asOf, scoreFacts());
    }

    private List<CertificateScorePolicy.Fact> scoreFacts() {
        return repository.findAll().stream().map(row -> new CertificateScorePolicy.Fact(
                row.getId(), row.getName(), row.getLevel(), row.getExpiryDate(),
                row.getStatus() == null ? null : row.getStatus().name(), row.isRetired(), row.getIssueDate(),
                row.getCategory() == null ? null : row.getCategory().name()))
                .sorted(Comparator.comparing(Object::toString)).toList();
    }

    public boolean hasCertificateExpiredSince(String dim, String detail, LocalDate estimatedOn, LocalDate currentDay) {
        if (estimatedOn == null || currentDay == null || !currentDay.isAfter(estimatedOn)) {
            return false;
        }
        return scoreFacts().stream()
                .filter(fact -> fact.expiryDate() != null)
                .filter(fact -> !fact.expiryDate().isBefore(estimatedOn))
                .anyMatch(fact -> fact.expiryDate().isBefore(currentDay));
    }

    private static String fingerprint(String dim, String detail, BigDecimal weight, LocalDate asOf,
                                      List<CertificateScorePolicy.Fact> facts) {
        StringBuilder input = new StringBuilder();
        for (Object value : List.of(CertificateScorePolicy.ENGINE_VERSION,
                String.valueOf(dim), String.valueOf(detail), String.valueOf(weight))) {
            appendFingerprint(input, value);
        }
        for (var fact : facts) {
            appendFingerprint(input, fact.id()); appendFingerprint(input, fact.name()); appendFingerprint(input, fact.level());
            appendFingerprint(input, fact.expiryDate()); appendFingerprint(input, fact.status());
            appendFingerprint(input, fact.retired()); appendFingerprint(input, fact.issueDate());
            appendFingerprint(input, fact.category());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void appendFingerprint(StringBuilder target, Object value) {
        if (value == null) { target.append("-1:"); return; }
        String text = value.toString();
        target.append(text.length()).append(':').append(text);
    }

    public KnowledgeMatchResult match(CertMatchRequest request) {
        if (request == null) {
            return KnowledgeMatchResult.empty("请求参数为空");
        }
        LocalDate today = LocalDate.now();
        LocalDate validCheckDate = request.requireValidUntil() != null ? request.requireValidUntil() : today;
        List<CertMatchedItem> matched = repository.findAll(specificationOf(request)).stream()
                .map(row -> new CertMatchedItem(row.getId(), row.getName(), row.getLevel(),
                        row.getExpiryDate(), isExpired(row, validCheckDate)))
                .toList();
        boolean anyExpired = matched.stream().anyMatch(CertMatchedItem::expired);
        boolean anyMissingExpiry = matched.stream().anyMatch(row -> row.expiryDate() == null);
        MatchTierPolicy.Outcome outcome = MatchTierPolicy.evaluate(
                matched.size(), request.requiredCount(), anyExpired || anyMissingExpiry, false);
        return new KnowledgeMatchResult(outcome.tier(), outcome.matchRatio(), matched,
                buildDetail(matched.size(), anyExpired),
                matched.stream().map(row -> certProof(row, request, validCheckDate)).toList());
    }

    private static KnowledgeMatchResult.RecordProof certProof(
            CertMatchedItem row, CertMatchRequest request, LocalDate validCheckDate) {
        List<KnowledgeMatchResult.ConditionProof> conditions = new ArrayList<>();
        conditions.add(new KnowledgeMatchResult.ConditionProof("证书名称关键词",
                StringUtils.hasText(row.name()) ? "PASS" : "MISSING_EVIDENCE",
                StringUtils.hasText(row.name()) ? row.name() : "证书记录未提供名称"));
        if (StringUtils.hasText(request.requiredLevel())) {
            conditions.add(new KnowledgeMatchResult.ConditionProof("证书等级包含 " + request.requiredLevel().trim(),
                    StringUtils.hasText(row.level()) && row.level().contains(request.requiredLevel().trim()) ? "PASS" : "FAIL",
                    StringUtils.hasText(row.level()) ? row.level() : "证书记录未提供等级"));
        }
        conditions.add(new KnowledgeMatchResult.ConditionProof("有效期不少于 " + validCheckDate,
                row.expired() ? "FAIL" : row.expiryDate() == null ? "MISSING_EVIDENCE" : "PASS",
                row.expiryDate() == null ? "证书记录未提供到期日"
                        : row.expiryDate().toString()));
        return new KnowledgeMatchResult.RecordProof("CERT", String.valueOf(row.id()), row.name(), conditions);
    }

    private Specification<BusinessQualificationEntity> specificationOf(CertMatchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (request.certNameKeywords() != null && !request.certNameKeywords().isEmpty()) {
                predicates.add(likeAny(cb, root.get("name"), request.certNameKeywords()));
            }
            if (StringUtils.hasText(request.requiredLevel())) {
                predicates.add(cb.like(root.get("level"), "%" + request.requiredLevel().trim() + "%"));
            }
            predicates.add(cb.notEqual(root.get("status"), QualificationStatus.RETIRED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isExpired(BusinessQualificationEntity row, LocalDate today) {
        return row.getStatus() == QualificationStatus.EXPIRED
                || (row.getExpiryDate() != null && row.getExpiryDate().isBefore(today));
    }

    private static String buildDetail(int matchedCount, boolean anyExpired) {
        if (matchedCount == 0) {
            return "未命中任何资质证书";
        }
        return anyExpired
                ? "命中 " + matchedCount + " 条资质，其中含过期证书（expired=true，需人工确认）"
                : "命中 " + matchedCount + " 条有效资质";
    }

    private static Predicate likeAny(jakarta.persistence.criteria.CriteriaBuilder cb,
                                     jakarta.persistence.criteria.Path<String> path,
                                     List<String> keywords) {
        List<Predicate> likes = keywords.stream()
                .filter(StringUtils::hasText)
                .map(keyword -> cb.like(path, "%" + keyword.trim() + "%"))
                .toList();
        return cb.or(likes.toArray(new Predicate[0]));
    }
}
