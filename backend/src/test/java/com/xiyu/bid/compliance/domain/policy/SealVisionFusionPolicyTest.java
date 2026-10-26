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

    @Test
    void textLayerPassWithUnavailableVision_doesNotStayPass() {
        // 回归 1382：文本层强证据 PASS + 视觉通道未跑（抽图失败/无页图）不得直接落库为通过
        var textResult = SealIntegrityCheckPolicy.check(DocumentContent.fromText(
                "bid.pdf", "hash", "本文件已完成电子签章防伪码校验，验签有效。"));
        assertThat(textResult.status()).isEqualTo(QualityCheckStatus.PASS);

        var result = SealVisionFusionPolicy.merge(textResult, SealVisionResult.unavailable("未抽取到页面图像"));

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("视觉");
        assertThat(result.suggestion()).contains("未抽取到页面图像");
    }

    @Test
    void availableVisionWithoutPageHint_cannotConfirmPageCoverage() {
        // .docx 只抽到内嵌图片（pageHint 为空）时，视觉无法做页面级覆盖判定，不得维持 PASS
        var textResult = SealIntegrityCheckPolicy.check(DocumentContent.fromText(
                "bid.pdf", "hash", "本文件已完成电子签章防伪码校验，验签有效。"));
        assertThat(textResult.status()).isEqualTo(QualityCheckStatus.PASS);
        var vision = SealVisionResult.available(
                List.of(),
                List.of(new SealVisionResult.Observation(
                        1, SealVisionResult.SealType.COMPANY_SEAL, true, 0.93,
                        0.5, 0.5, 0.2, 0.2)),
                "seal-model-v1");

        var result = SealVisionFusionPolicy.merge(textResult, vision);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("页面级");
    }

    @Test
    void unknownVisualTypeMustNotProveSeal() {
        var text = SealIntegrityCheckPolicy.check(DocumentContent.fromText("bid.pdf", "hash", "投标人：甲公司 法定代表人：张三 已加盖公章"));
        var vision = SealVisionResult.available(java.util.List.of(5), java.util.List.of(new SealVisionResult.Observation(
                5, SealVisionResult.SealType.UNKNOWN, true, 0.99, null, null, null, null)), "model");
        assertThat(SealVisionFusionPolicy.merge(text, vision).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

}
