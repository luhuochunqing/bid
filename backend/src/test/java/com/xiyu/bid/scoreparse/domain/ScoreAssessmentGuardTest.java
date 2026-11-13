// Input: LLM 阶段2打分原始输出 + 权重 + 评分类别
// Output: 守卫结果断言（超区间置空/主观零泄漏/quoteMissing 置空）
// Pos: scoreparse/domain — spec 041 US4 守卫测试（contracts/llm-output-schema.md §5）
package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 2 打分结果守卫测试（spec 041 FR-016 / SC-003）。
 * <p>不信任模型数值：超区间置 null、主观项数字强制丢弃、quoteMissing 时引用置 null。
 */
class ScoreAssessmentGuardTest {

    private final ScoreAssessmentGuard guard = new ScoreAssessmentGuard();

    @Test void quotePresentOnlyOutsideActualExcerptHasDistinctReason() {
        String quote="已建设并上线运行OMS订单管理系统";
        assertThat(guard.verifyQuote(quote,"企业说明："+quote,"其他投标内容").reason()).isEqualTo("QUOTE_NOT_IN_EXCERPT");
        assertThat(guard.verifyQuote(null,quote,quote).reason()).isEqualTo("MODEL_QUOTE_MISSING");
    }
    @Test void locatorAlreadyInSourceIsPreserved() {
        String source="第1页：已建设并上线运行OMS订单管理系统";
        assertThat(guard.verifyQuote(source,source,source).quote()).isEqualTo(source);
    }
    @Test void returnedQuoteIsOriginalSourceAndForgedExcerptCannotPublish() {
        String source="已建设并上线运行OMS订单管理系统";
        assertThat(guard.verifyQuote("第1页："+source,source,source).quote()).isEqualTo(source);
        assertThat(guard.verifyQuote(source,"其他文档",source).reason()).isEqualTo("QUOTE_NOT_IN_DOCUMENT");
    }
    @Test void quoteSpanningExcerptSeparatorIsVerifiedAgainstDocument() {
        String first="证明一：质量管理体系认证证书 ISO9001，证书编号 00123Q45678R0M。";
        String second="证明二：环境管理体系认证证书 ISO14001，证书编号 00123E45678R0M。";
        String document=first+"\n"+second;
        String excerpt=first+"\n\n---\n\n"+second;
        var proof=guard.verifyQuote(first+second,document,excerpt);
        assertThat(proof.verified()).isTrue();
        assertThat(proof.quote()).isEqualTo(document);
    }
    @Test void englishQuoteSpanningExcerptSeparatorPreservesWordBoundaryAndOriginalSource() {
        String first="Quality Management Certificate ISO9001";
        String second="Environmental Management Certificate ISO14001";
        String document=first+"\n\n"+second;
        String excerpt=first+"\n\n---\n\n"+second;
        var proof=guard.verifyQuote(document,"Company credentials:\n"+document+"\nEnd of credentials.",excerpt);
        assertThat(proof.verified()).isTrue();
        assertThat(proof.quote()).isEqualTo(document);
    }
    @Test void englishExcerptSeparatorCannotProveQuoteMissingFromDocument() {
        String first="Quality Management Certificate ISO9001";
        String second="Environmental Management Certificate ISO14001";
        var proof=guard.verifyQuote(first+"\n\n"+second,first+" Other credentials. "+second,
                first+"\n\n---\n\n"+second);
        assertThat(proof.verified()).isFalse();
        assertThat(proof.quote()).isNull();
        assertThat(proof.reason()).isEqualTo("QUOTE_NOT_IN_DOCUMENT");
    }
    @Test void excerptSeparatorCannotProveQuoteMissingFromDocument() {
        String first="已建设并上线运行OMS订单管理系统。";
        String second="已建设并上线运行WMS仓储管理系统。";
        String excerpt=first+"\n\n---\n\n"+second;
        var proof=guard.verifyQuote(first+second,first+"其他投标内容。"+second,excerpt);
        assertThat(proof.verified()).isFalse();
        assertThat(proof.quote()).isNull();
        assertThat(proof.reason()).isEqualTo("QUOTE_NOT_IN_DOCUMENT");
    }
    @Test void rawQuoteEndingWithDigitPeriodSurvivesContextSensitiveNormalization() {
        String quote="证明五：供应链安全管理体系认证证书，证书编号 SCS-2024-0912，有效期至 2027-12-31。";
        String document=quote+"\n第四章 服务方案";
        var proof=guard.verifyQuote(quote,document,document);
        assertThat(proof.verified()).isTrue();
        assertThat(proof.quote()).isEqualTo(quote);
    }
    @Test void quoteJoinedAcrossLineWrappedIdentifierIsVerified() {
        String document="已取得OMS订单管理系统软件著作权登记证书，登记号 2022S\nR0445566，有效期至 2027-06-30。";
        String quote="已取得OMS订单管理系统软件著作权登记证书，登记号 2022SR0445566，有效期至 2027-06-30。";
        var proof=guard.verifyQuote(quote,document,document);
        assertThat(proof.verified()).isTrue();
        assertThat(proof.quote()).isEqualTo(document);
    }
    @Test void joinedQuoteWithDigitPeriodTailIsVerified() {
        String source="已取得OMS订单管理系统软件著作权登记证书，登记号 2022S\nR0445566，有效期至 2027-12-31。";
        String document=source+"\n第四章 服务方案";
        String quote="已取得OMS订单管理系统软件著作权登记证书，登记号 2022SR0445566，有效期至 2027-12-31。";
        var proof=guard.verifyQuote(quote,document,document);
        assertThat(proof.verified()).isTrue();
        assertThat(proof.quote()).isEqualTo(source);
    }
    @Test void joinedQuoteCannotBypassExcerptMembership() {
        String document="已取得OMS订单管理系统软件著作权登记证书，登记号 2022S\nR0445566。";
        String quote="已取得OMS订单管理系统软件著作权登记证书，登记号 2022SR0445566。";
        var proof=guard.verifyQuote(quote,document,"其他投标内容");
        assertThat(proof.quote()).isNull();
        assertThat(proof.reason()).isEqualTo("QUOTE_NOT_IN_EXCERPT");
    }
    @Test void joinedQuoteCannotBypassDocumentMembership() {
        String excerpt="已取得OMS订单管理系统软件著作权登记证书，登记号 2022S\nR0445566。";
        String quote="已取得OMS订单管理系统软件著作权登记证书，登记号 2022SR0445566。";
        var proof=guard.verifyQuote(quote,"其他投标内容",excerpt);
        assertThat(proof.quote()).isNull();
        assertThat(proof.reason()).isEqualTo("QUOTE_NOT_IN_DOCUMENT");
    }
    @Test void joinedQuoteAcrossExcerptSeparatorRestoresOriginalSource() {
        String first="已取得OMS订单管理系统软件著作权登记证书，登记号 2022S\nR0445566，";
        String second="有效期至 2027-12-31。";
        String source=first+"\n"+second;
        String excerpt=first+"\n\n---\n\n"+second+"\n第四章 服务方案";
        String quote="已取得OMS订单管理系统软件著作权登记证书，登记号 2022SR0445566，"+second;
        var proof=guard.verifyQuote(quote,"企业资质：\n"+source+"\n第四章 服务方案",excerpt);
        assertThat(proof.verified()).isTrue();
        assertThat(proof.quote()).isEqualTo(source);
    }
    @Test void joinedQuoteAcrossExcerptSeparatorStillRequiresContinuousDocumentSource() {
        String first="已取得OMS订单管理系统软件著作权登记证书，登记号 2022S\nR0445566，";
        String second="有效期至 2027-12-31。";
        String excerpt=first+"\n\n---\n\n"+second;
        String quote="已取得OMS订单管理系统软件著作权登记证书，登记号 2022SR0445566，"+second;
        var proof=guard.verifyQuote(quote,first+"其他投标内容。"+second,excerpt);
        assertThat(proof.quote()).isNull();
        assertThat(proof.reason()).isEqualTo("QUOTE_NOT_IN_DOCUMENT");
    }
    private ScoreAssessmentGuard.Input.InputBuilder objectiveInput() {
        return ScoreAssessmentGuard.Input.builder()
                .actualScore(new BigDecimal("6"))
                .matchRatio(60)
                .evidence("标书已提供 CMMI 3 级证书说明，部分满足")
                .quote("第 3.2 节 P15：我方已通过 CMMI 3 级认证")
                .quoteMissing(false)
                .missedReason("CMMI 5 级认证未找到匹配证书")
                .suggestion("建议启动 CMMI 5 级评估");
    }

    @Test
    void objectiveScoreWithinRange_passesThrough() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().build(), new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.actualScore()).isEqualByComparingTo("6");
        assertThat(result.matchRatio()).isEqualTo(60);
        assertThat(result.quote()).isEqualTo("第 3.2 节 P15：我方已通过 CMMI 3 级认证");
        assertThat(result.rangeInvalid()).isFalse();
        assertThat(result.subjectiveDropped()).isFalse();
    }

    @Test
    void objectiveScoreAboveWeight_nullifiedAndFlagged() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().actualScore(new BigDecimal("11")).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.actualScore()).isNull();
        assertThat(result.rangeInvalid()).isTrue();
    }

    @Test
    void objectiveScoreNegative_nullifiedAndFlagged() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().actualScore(new BigDecimal("-1")).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.actualScore()).isNull();
        assertThat(result.rangeInvalid()).isTrue();
    }

    @Test
    void subjectiveNumericScore_forceDropped() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().build(), new BigDecimal("10"), "SUBJECTIVE");

        assertThat(result.actualScore()).isNull();
        assertThat(result.subjectiveDropped()).isTrue();
    }

    @Test
    void subjectiveMatchRatio_alsoDropped() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().build(), new BigDecimal("10"), "SUBJECTIVE");

        assertThat(result.matchRatio()).isNull();
        assertThat(result.subjectiveDropped()).isTrue();
    }

    @Test
    void subjectiveSuggestion_preserved() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().build(), new BigDecimal("10"), "SUBJECTIVE");

        assertThat(result.suggestion()).isEqualTo("建议启动 CMMI 5 级评估");
        assertThat(result.missedReason()).isNull();
        assertThat(result.evidence()).isNull();
    }

    @Test
    void quoteMissing_nullifiesQuote() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().quote("模型违规输出的引用").quoteMissing(true).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.quote()).isNull();
    }

    @Test
    void quoteMissingNull_treatedAsFalse_keepsQuote() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().quoteMissing(null).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.quote()).isEqualTo("第 3.2 节 P15：我方已通过 CMMI 3 级认证");
    }

    @Test
    void objectiveNullScore_passesAsPendingSemantics() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().actualScore(null).matchRatio(null).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.actualScore()).isNull();
        assertThat(result.rangeInvalid()).isFalse();
        assertThat(result.subjectiveDropped()).isFalse();
    }
}
