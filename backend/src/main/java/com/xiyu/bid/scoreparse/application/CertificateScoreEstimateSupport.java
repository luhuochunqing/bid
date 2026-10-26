package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.domain.CertificateScoreRule;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Adapts certificate scoring to estimate columns; rule and stage-2 fields stay untouched. */
final class CertificateScoreEstimateSupport {
    private CertificateScoreEstimateSupport() {}

    static boolean applies(ScoreItem item) {
        if (!"OBJECTIVE".equals(item.getScoreType())) return false;
        String category = new KnowledgeCategoryPolicy().categorize(item.getDim(), item.getDetail());
        return (KnowledgeCategoryPolicy.CATEGORY_CERT.equals(category)
                || KnowledgeCategoryPolicy.CATEGORY_OTHER.equals(category))
                && CertificateScoreRule.isCertificateScore(item.getDim(), item.getDetail());
    }

    static void calculate(ScoreItem item, CertMatchService certificates, LocalDate day) {
        var estimate = certificates.estimateScore(item.getDim(), item.getDetail(), item.getWeight(), day);
        var assessment = estimate.assessment();
        item.setEstScore(assessment.score());
        item.setStatusStage1(assessment.status());
        item.setKbHit(assessment.kbHit());
        item.setEstBasis(assessment.basis());
        item.setEstimateInputFingerprint(sealedFingerprint(item, estimate.inputFingerprint()));
        item.setEstimatedAt(LocalDateTime.now());
    }

    /** Bind the result to its inputs so legacy writers cannot reuse a new calculation stamp. */
    static String sealedFingerprint(ScoreItem item, String inputs) {
        Object[] values = {"certificate-estimate-seal:v1", inputs,
                item.getEstScore()==null?null:item.getEstScore().stripTrailingZeros().toPlainString(),
                item.getStatusStage1(), item.getKbHit(), item.getEstBasis()};
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            if (value == null) canonical.append("-1:");
            else { String text=value.toString(); canonical.append(text.length()).append(':').append(text); }
        }
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
