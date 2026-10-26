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
        String current = certificates.fingerprintForScore(item.getDim(), item.getDetail(), item.getWeight(), day);
        return item.getEstimateInputFingerprint().equals(
                CertificateScoreEstimateSupport.sealedFingerprint(item,current)) ? "FRESH" : "STALE";
    }
}
