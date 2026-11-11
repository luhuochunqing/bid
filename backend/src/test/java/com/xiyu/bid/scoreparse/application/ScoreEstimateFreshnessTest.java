package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ScoreEstimateFreshnessTest {

    @Test
    void validCertificateCalculation_remainsFreshAcrossDays_whenQualificationsUnchanged() {
        CertMatchService certificates = mock(CertMatchService.class);
        ScoreEstimateFreshness freshness = new ScoreEstimateFreshness(certificates);

        LocalDate calcDay = LocalDate.of(2026, 11, 10);
        LocalDate nextDay = LocalDate.of(2026, 11, 11);

        String fp = "consistent-fp-123456";
        when(certificates.fingerprintForScore(eq("管理体系"), anyString(), eq(BigDecimal.TEN), eq(calcDay)))
                .thenReturn(fp);
        when(certificates.fingerprintForScore(eq("管理体系"), anyString(), eq(BigDecimal.TEN), eq(nextDay)))
                .thenReturn(fp);

        ScoreItem item = ScoreItem.builder()
                .dim("管理体系")
                .detail("ISO9001质量管理体系认证")
                .weight(BigDecimal.TEN)
                .scoreType("OBJECTIVE")
                .statusStage1("OK")
                .estScore(BigDecimal.TEN)
                .estimatedAt(LocalDateTime.of(2026, 11, 10, 10, 0, 0))
                .build();
        item.setEstimateInputFingerprint(CertificateScoreEstimateSupport.sealedFingerprint(item, fp));

        assertThat(freshness.status(item, calcDay)).isEqualTo("FRESH");
        assertThat(freshness.status(item, nextDay)).isEqualTo("FRESH");
    }
}
