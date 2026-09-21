package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SealVisionFusionPolicyTest {

    @Test
    void documentContentCarriesPageImagesWithoutChangingLegacyConstructor() {
        var image = new PageImage(0, "image/png", "投标函", 5, 1000, 1400, "aGk=");
        var document = new DocumentContent("bid.pdf", "hash", "正文", List.of(
                new DocumentContent.PageContent(5, "投标函", List.of())), List.of(image));

        assertThat(document.images()).containsExactly(image);
        assertThat(DocumentContent.fromText("bid.pdf", "hash", "正文").images()).isEmpty();
    }

    @Test
    void pageLevelVisionEvidence_isPersistedWithPageAndCoordinates() {
        var textResult = SealIntegrityCheckPolicy.check(
                DocumentContent.fromText("bid.pdf", "hash", "投标人：（盖章）\n法定代表人（签字）："));
        var vision = SealVisionResult.available(
                List.of(5),
                List.of(new SealVisionResult.Observation(
                        5, SealVisionResult.SealType.COMPANY_SEAL, true, 0.96,
                        0.72, 0.81, 0.15, 0.10)),
                "seal-model-v1");

        var result = SealVisionFusionPolicy.merge(textResult, vision);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        QualityEvidence evidence = result.evidences().stream()
                .filter(item -> "BID_IMAGE".equals(item.sourceType()))
                .findFirst().orElseThrow();
        assertThat(evidence.pageNumber()).isEqualTo(5);
        assertThat(evidence.quote()).contains("COMPANY_SEAL").contains("x=0.72").contains("y=0.81");
    }

    @Test
    void targetPageWithoutDetectedSeal_downgradesToManualReview() {
        var textResult = SealIntegrityCheckPolicy.check(DocumentContent.fromText(
                "bid.pdf", "hash", "投标人：甲公司\n法定代表人：张三\n已加盖公章"));
        var vision = SealVisionResult.available(
                List.of(8, 9),
                List.of(new SealVisionResult.Observation(
                        8, SealVisionResult.SealType.COMPANY_SEAL, true, 0.91,
                        0.20, 0.30, 0.12, 0.08)),
                "seal-model-v1");

        var result = SealVisionFusionPolicy.merge(textResult, vision);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("第9页");
    }

    @Test
    void unavailableVision_doesNotInventPass() {
        var textResult = SealIntegrityCheckPolicy.check(DocumentContent.fromText(
                "scan.pdf", "hash", "投标函\n投标人：（盖章）\n法定代表人（签字）："));

        var result = SealVisionFusionPolicy.merge(textResult, SealVisionResult.unavailable("AI 未配置"));

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("视觉");
    }

    @Test
    void availableVisionWithoutPositiveObservation_downgradesToManualReview() {
        var textResult = SealIntegrityCheckPolicy.check(DocumentContent.fromText(
                "scan.pdf", "hash", "投标人：甲公司\n法定代表人：张三\n已加盖公章"));
        var vision = SealVisionResult.available(List.of(5), List.of(), "seal-model-v1");

        var result = SealVisionFusionPolicy.merge(textResult, vision);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("未识别");
    }
}
