package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Read-side validation never rewrites historical estimates or adopts extraction candidates. */
@Service
public class ScoreEstimateFreshness {
    private final CertMatchService certificates;
    public ScoreEstimateFreshness(CertMatchService certificates) { this.certificates = certificates; }

    public String status(ScoreItem item, LocalDate day) {
        if (!CertificateScoreEstimateSupport.applies(item)) return "NOT_APPLICABLE";
        if (item.getEstimatedAt() == null || item.getEstimateInputFingerprint() == null) return "STALE";
        String factorName = CertificateScoreEstimateSupport.factorName(item);
        String current = certificates.simpleFingerprintForScore(factorName, item.getDetail(), item.getWeight(), day);
        if (!item.getEstimateInputFingerprint().equals(
                CertificateScoreEstimateSupport.sealedFingerprint(item, current))) {
            return "STALE";
        }
        if (certificates.hasCertificateExpiredSince(factorName, item.getDetail(), item.getEstimatedAt().toLocalDate(), day)) {
            return "STALE";
        }
        return "FRESH";
    }
}
