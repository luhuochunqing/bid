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

    @Test void legacyProjectEstimateMustBeHiddenUntilRecalculatedUnderNewRules() {
        var row=ScoreItem.builder().code("供应商业绩").dim("商务").detail("2023年1月1日至今业绩")
                .scoreType("OBJECTIVE").weight(BigDecimal.TEN).estScore(BigDecimal.TEN).statusStage1("OK").build();
        var day=LocalDate.of(2026,11,17);
        var freshness=new ScoreEstimateFreshness(mock(CertMatchService.class));
        assertThat(freshness.status(row,day)).isEqualTo("STALE");
        EstimateEvidenceSupport.seal(row,day);
        assertThat(freshness.status(row,day)).isEqualTo("FRESH");
        row.setDetail("2024年1月1日至今业绩");
        assertThat(freshness.status(row,day)).isEqualTo("STALE");
    }

    @Test
    void legacyCertificateNameUsesSameFactorForCalculationAndReadFingerprint() {
        var certificates = mock(CertMatchService.class);
        var day = LocalDate.of(2026, 11, 12);
        var row = ScoreItem.builder().code("企业荣誉").dim("商务")
                .detail("获得国家级荣誉得10分").weight(BigDecimal.TEN).scoreType("OBJECTIVE").build();
        when(certificates.estimateSimpleScore("企业荣誉", row.getDetail(), BigDecimal.TEN, day))
                .thenReturn(new CertMatchService.ScoreEstimate(
                        new com.xiyu.bid.scoreparse.domain.CertificateScorePolicy.Assessment(
                                BigDecimal.TEN, "OK", true, "荣誉证据已核验"), "legacy-honor-input"));
        when(certificates.simpleFingerprintForScore("企业荣誉", row.getDetail(), BigDecimal.TEN, day))
                .thenReturn("legacy-honor-input");
        assertThat(CertificateScoreEstimateSupport.applies(row)).isTrue();
        CertificateScoreEstimateSupport.calculate(row, certificates, day);
        assertThat(new ScoreEstimateFreshness(certificates).status(row, day)).isEqualTo("FRESH");
        assertThat(row.getDim()).isEqualTo("商务");
    }

    @Test
    void validCertificateCalculation_remainsFreshAcrossDays_whenQualificationsUnchanged() {
        CertMatchService certificates = mock(CertMatchService.class);
        ScoreEstimateFreshness freshness = new ScoreEstimateFreshness(certificates);

        LocalDate calcDay = LocalDate.of(2026, 11, 10);
        LocalDate nextDay = LocalDate.of(2026, 11, 11);

        String fp = "consistent-fp-123456";
        when(certificates.simpleFingerprintForScore(eq("管理体系"), anyString(), eq(BigDecimal.TEN), eq(calcDay)))
                .thenReturn(fp);
        when(certificates.simpleFingerprintForScore(eq("管理体系"), anyString(), eq(BigDecimal.TEN), eq(nextDay)))
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
