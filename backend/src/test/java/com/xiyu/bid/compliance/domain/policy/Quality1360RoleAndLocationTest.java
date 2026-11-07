package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

/** R2: requirement role and evidence location through the real quality engine. */
class Quality1360RoleAndLocationTest {
    @ParameterizedTest
    @ValueSource(strings = {"须提供服务协议", "须填写服务协议", "须提供服务协议并加盖公章"})
    void requiredAgreementCannotBeExemptedByItsMaterialCategory(String criteria) {
        var bid = paged("售后服务方案\n服务协议：_____。");
        var req = requirements(criteria);
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, req).orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(engine(bid, req, "SCORING_RESPONSE").status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"须提供服务协议模板", "须提供服务协议样本"})
    void requestedSampleDoesNotRequireFilledExecutionFields(String criteria) {
        assertThat(ScoringResponsePolicy.checkFirstStage(paged("服务协议：_____。"), requirements(criteria)).stream())
                .noneMatch(r -> r.status() == QualityCheckStatus.FAIL);
    }

    @Test
    void conflictingRolesRequireHumanReviewEvenWhenModelPasses() {
        var req = requirements("须提供服务协议；须提供服务协议模板");
        assertThat(engine(paged("服务协议：_____。"), req, "SCORING_RESPONSE").status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void ambiguousEarlierFieldMustNotMaskLaterDefiniteDefect() {
        var req = requirements("须提供服务协议；须提供服务协议模板；须填写响应时间");
        assertThat(engine(paged("服务协议：_____。\n响应时间：_____。"), req, "SCORING_RESPONSE").status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"资格声明\n失信被执行人核查结果：_____。", "拟将本项目分包给第三方。"})
    void unpaginatedQualificationHasNoInventedPage(String text) {
        var result = engine(unpaged(text), TenderRequirements.empty(), "QUALIFICATION_VETO");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).isNotEmpty().allSatisfy(e -> {
            assertThat(e.pageNumber()).isNull();
            assertThat(text).contains(e.quote());
        });
    }

    @Test
    void unpaginatedScoringRequiresConfirmationWithoutInventingPage() {
        String text = "服务协议：_____。";
        var result = engine(unpaged(text), requirements("须提供服务协议"), "SCORING_RESPONSE");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).isNotEmpty().allSatisfy(e -> {
            assertThat(e.pageNumber()).isNull();
            assertThat(text).contains(e.quote());
        });
    }

    @Test
    void providedPageIsRetainedForRequiredAgreement() {
        var result = engine(paged("服务协议：_____。"), requirements("须提供服务协议"), "SCORING_RESPONSE");
        assertThat(result.evidences()).isNotEmpty().allSatisfy(e -> assertThat(e.pageNumber()).isEqualTo(80));
    }

    @Test
    void completedAgreementDoesNotBecomeHardFailure() {
        assertThat(ScoringResponsePolicy.checkFirstStage(paged("服务协议：我司承诺7×24小时服务。"),
                requirements("须提供服务协议")).stream()).noneMatch(r -> r.status() == QualityCheckStatus.FAIL);
    }

    @Test
    void samplePurposeAfterObjectMustNotBeLost() {
        var req = requirements("须提供服务协议；须提供服务协议模板供参考");
        assertThat(engine(paged("服务协议：_____。"), req, "SCORING_RESPONSE").status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var sampleReq = new TenderRequirements(List.of(new TenderRequirements.ScoringItem("技术方案", 5.0,
                "仅须提供技术方案模板供参考，无需填写模板字段")), List.of(), List.of(), List.of(), List.of(), false);
        assertThat(engine(paged("技术方案：_____。"), sampleReq, "SCORING_RESPONSE").status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"无需提供服务协议", "不必填写服务协议"})
    void negatedObligationCannotBecomeRequiredBlank(String criteria) {
        assertThat(engine(paged("服务协议：_____。"), requirements(criteria), "SCORING_RESPONSE").status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void parentheticalReferencePurposeIsPartOfMaterialRole() {
        assertThat(engine(paged("服务协议：_____。"), requirements("仅须提供服务协议（参考样例，无需填写字段）"),
                "SCORING_RESPONSE").status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"须提供技术方案模板并须填写服务协议", "须提供技术方案模板并填写服务协议"})
    void coordinatedActionsDoNotInheritUnprovenModality(String criteria) {
        assertThat(engine(paged("服务协议：_____。"), requirements(criteria), "SCORING_RESPONSE").status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void completedFirstObjectDoesNotProveLaterActionsModality() {
        assertThat(engine(paged("服务协议：已签署并承诺全天服务。\n响应时间：_____小时。"),
                requirements("须提供服务协议并填写响应时间"), "SCORING_RESPONSE").status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void unpaginatedCatalogDoesNotHideActualBody() {
        String prefix = "目录\n售后服务方案......80\n\n正文\n";
        assertThat(engine(unpaged(prefix + "服务协议：_____。"), requirements("须提供服务协议"),
                "SCORING_RESPONSE").status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var qual = engine(unpaged(prefix + "资格声明\n失信核查结果：_____。"), TenderRequirements.empty(), "QUALIFICATION_VETO");
        assertThat(qual.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(qual.evidences()).allSatisfy(e -> assertThat(e.pageNumber()).isNull());
    }

    @Test
    void parsedPagesDoNotDependOnRedundantFullText() {
        var bid = new DocumentContent("bid.docx", "bh", "",
                List.of(new DocumentContent.PageContent(80, "资格声明\n失信核查结果：_____。", List.of())));
        var result = engine(bid, TenderRequirements.empty(), "QUALIFICATION_VETO");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).allSatisfy(e -> assertThat(e.pageNumber()).isEqualTo(80));
    }

    private static TenderRequirements requirements(String criteria) {
        return new TenderRequirements(List.of(new TenderRequirements.ScoringItem("售后服务方案", 5.0, criteria)),
                List.of(), List.of(), List.of(), List.of(), false);
    }

    private static DocumentContent paged(String text) {
        return new DocumentContent("bid.docx", "bh", text,
                List.of(new DocumentContent.PageContent(80, text, List.of())));
    }

    private static DocumentContent unpaged(String text) {
        return new DocumentContent("bid.docx", "bh", text, List.of());
    }

    private static QualityCheckItemResult engine(DocumentContent bid, TenderRequirements req, String code) {
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict("PASS", "", "模型认为响应完整", "")));
        return BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), req, Set.of(), llm,
                (p, s) -> { }).stream().filter(r -> r.itemCode().equals(code)).findFirst().orElseThrow();
    }
}
