package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class CertificateScorePolicyTest {
    static final LocalDate DAY = LocalDate.of(2026,10,26);
    static final String ISO = "具有 ISO9001 质量管理体系认证、ISO14001环境管理体系认证、ISO45001职业健康安\n全管理体系认证的得10分。三项齐全得满分，缺少一项，不得分";
    static final String HONOR = "具有中国品牌信誉评价认证五星级证书；企业质量信用等级评价认证3A级证书；商品售后五星级认证；企业标准化管理体系认证3A级证书；以上４项，具备任意４项则得5分，具备任意３项得3分，具备任意２项得2分，具备任意１项的得1分，不满足任意1项或未提供证明文件者，不得分";
    static CertificateScorePolicy.Fact fact(String name) { return new CertificateScorePolicy.Fact(name,null,DAY.plusYears(1),"IN_STOCK",false,null); }
    CertificateScorePolicy.Assessment assess(String text,List<CertificateScorePolicy.Fact> facts) { return CertificateScorePolicy.assess("管理体系",text,BigDecimal.TEN,DAY,facts); }
    @Test
    void simpleEstimateKeepsNamedCertificateRelevanceAndCanonicalizesGenericAliases() {
        var day=LocalDate.of(2026,11,12);
        assertThat(CertificateScoreRule.identitiesForEstimate("资质", "具有中国品牌信誉评价认证五星级证书"))
                .containsExactly("品牌信誉");
        var facts=List.of(simpleFact(1L,"ISO9001",day),simpleFact(2L,"GB/T19001质量管理体系认证证书",day),
                simpleFact(3L,"ISO9001质量管理体系认证证书",day));
        for(String dim:List.of("安全生产许可证","资质"))
            assertThat(CertificateScorePolicy.assessSimple(dim,"提供安全生产许可证",BigDecimal.TEN,day,facts).score()).isZero();
        assertThat(CertificateScorePolicy.assessSimple("管理体系","需提供三项认证",BigDecimal.TEN,day,facts).score()).isEqualByComparingTo("3.33");
        assertThat(CertificateScorePolicy.assessSimple("资质","需提供3项证书",new BigDecimal("15"),day,facts).score()).isEqualByComparingTo("5.00");
    }

    @Test
    void simpleEstimateCountsIndependentIdentitiesRatherThanUploadsOrClosedGrammar() {
        var day = LocalDate.of(2026,11,12);
        var facts = List.of(simpleFact(1L,"ISO9001",day),simpleFact(2L,"ISO9001",day),simpleFact(3L,"ISO14001",day));
        var result = CertificateScorePolicy.assessSimple("管理体系", "ISO9001、ISO14001、ISO45001；资料按日期排序，缺一不得分",new BigDecimal("15"),day,facts);
        assertThat(result.score()).isEqualByComparingTo("10.00");
        assertThat(result.basis()).contains("要求3项", "命中2项", "知识库粗估");
        assertThat(CertificateScorePolicy.assessSimple("企业资质", "ISO20000认证",BigDecimal.TEN,day,facts).score()).isZero();
    }
    @Test
    void simpleEstimateReadsCertificateIdentitiesOnlyFromScoringClause() {
        var day = LocalDate.of(2026,11,12);
        String detail = "具有ISO9001质量管理体系认证证书得10分。2、证明文件：投标人须提供ISO14001环境管理体系认证证书复印件。";
        assertThat(CertificateScoreRule.identitiesForEstimate("管理体系", detail)).containsExactly("ISO9001");
        var facts = List.of(simpleFact(1L,"ISO9001质量管理体系认证证书",day),
                simpleFact(2L,"ISO14001环境管理体系认证证书",day));
        var estimate = CertificateScorePolicy.assessSimple("管理体系", detail, BigDecimal.TEN, day, facts);
        assertThat(estimate.score()).isEqualByComparingTo("10.00");
        assertThat(estimate.basis()).contains("要求1项", "命中1项");
    }
    @Test
    void simpleEstimateTruncatesUnrecognizedProofMaterialHeading() {
        String detail = "具有ISO9001质量管理体系认证证书得10分。2、证明材料：另附ISO14001环境管理体系认证证书复印件。";
        assertThat(CertificateScoreRule.identitiesForEstimate("管理体系", detail)).containsExactly("ISO9001");
    }
    @Test
    void simpleEstimateExcludesExpiredRetiredAndPersonalCertificates() {
        var day=LocalDate.of(2026,11,12);
        var facts=List.of(new CertificateScorePolicy.Fact(1L,"ISO9001","",day.minusDays(1),"IN_STOCK",false,null,"MANAGEMENT"),
                new CertificateScorePolicy.Fact(2L,"ISO9001","",day.plusYears(1),"IN_STOCK",true,null,"MANAGEMENT"),
                new CertificateScorePolicy.Fact(3L,"ISO9001内审员证书","",day.plusYears(1),"IN_STOCK",false,null,"PERSONNEL"),
                new CertificateScorePolicy.Fact(4L,"ISO9001","",null,"IN_STOCK",false,null,"MANAGEMENT"));
        assertThat(CertificateScorePolicy.assessSimple("资质","ISO9001证书",BigDecimal.TEN,day,facts).score()).isZero();
    }
    private static final String PROJECT_1133 = """
            商务
            体系认证
            6
            投标人具有有效期内的质量管理体系认证证书得1分；具有
            有效期内的环境管理体系认证证书得1分；具有有效期内的
            职业健康安全管理体系认证证书得1分；具有有效期内的信
            息安全管理体系认证证书得3分；满分6分。
            """;
    private static final LocalDate REPLAY_DAY = LocalDate.of(2026, 11, 18);

    @Test
    void estimatesProject1133ByStandardIdentityAndSeparatelyAwardedPoints() {
        var quality = simpleFact(1L, "ISO9001质量管理体系认证证书", REPLAY_DAY);
        var environment = simpleFact(45L, "ISO14001环境管理体系认证", REPLAY_DAY);
        var occupational = simpleFact(44L, "ISO 45001职业健康管理认证证书", REPLAY_DAY);
        var security = simpleFact(4L, "ISO/IEC27001信息安全管理体系认证证书", REPLAY_DAY);
        var cloudSecurity = simpleFact(17L, "ISO/IEC 27017云服务信息安全管理评价证书", REPLAY_DAY);

        var full = estimate1133(List.of(quality, environment, occupational, security, cloudSecurity));
        assertThat(full.score()).isEqualByComparingTo("6");
        assertThat(full.basis()).contains("按证书分值", "#1", "#45", "#44", "#4 ",
                "投标人持证主体待核验");
        assertThat(estimate1133(List.of(quality, security)).score()).isEqualByComparingTo("4");
        assertThat(estimate1133(List.of(quality, environment, occupational, cloudSecurity)).score())
                .isEqualByComparingTo("3");
    }

    @Test
    void project1133RecognizesQualityCertificateWithTrailingStandardAndRejectsConflicts() {
        var environment = simpleFact(45L, "ISO14001环境管理体系认证", REPLAY_DAY);
        var occupational = simpleFact(44L, "ISO45001职业健康管理认证证书", REPLAY_DAY);
        var security = simpleFact(4L, "ISO/IEC27001信息安全管理体系认证证书", REPLAY_DAY);

        for (String name : List.of("质量管理体系认证证书ISO14001",
                "ISO14001质量管理体系认证证书ISO9001")) {
            var conflict = simpleFact(1L, name, REPLAY_DAY);
            var partial = estimate1133(List.of(conflict, environment, occupational, security));
            assertThat(partial.score()).as(name).isEqualByComparingTo("5");
            assertThat(partial.status()).isEqualTo("PENDING");
        }

        var quality = simpleFact(1L, "质量管理体系认证证书ISO9001", REPLAY_DAY);
        var full = estimate1133(List.of(quality, environment, occupational, security));
        assertThat(full.score()).isEqualByComparingTo("6");
        assertThat(full.status()).isEqualTo("OK");
        assertThat(full.basis()).contains("ISO9001 +1分（#1 " + quality.name());
    }

    @Test
    void project1133ExcludesIneligibleRecordsAndIdentifiesMissingExpiry() {
        var expired = new CertificateScorePolicy.Fact(2L, "ISO14001环境管理体系认证", "",
                REPLAY_DAY.minusDays(1), "EXPIRED", false, REPLAY_DAY.minusYears(3), "OTHER");
        var unknown = new CertificateScorePolicy.Fact(4L, "ISO/IEC27001信息安全管理体系认证证书", "",
                null, "IN_STOCK", false, REPLAY_DAY.minusYears(1), "OTHER");
        var conflictingCode = simpleFact(17L, "ISO/IEC 27017信息安全管理体系认证证书", REPLAY_DAY);
        var future = new CertificateScorePolicy.Fact(44L, "ISO 45001职业健康管理认证证书", "",
                REPLAY_DAY.plusYears(1), "IN_STOCK", false, REPLAY_DAY.plusDays(1), "OTHER");
        var result = estimate1133(List.of(expired, unknown, conflictingCode, future));
        assertThat(result.score()).isZero();
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.basis()).contains("ISO27001 有效期待核验");
    }

    @Test
    void project1133UsesFirstValidRecordAndIgnoresUnverifiedDuplicates() {
        var unknown = new CertificateScorePolicy.Fact(10L, "ISO27001", "", null, "IN_STOCK", false, null);
        var expired = new CertificateScorePolicy.Fact(11L, "ISO27001", "", REPLAY_DAY.minusDays(1),
                "IN_STOCK", false, null);
        var validOnExpiry = new CertificateScorePolicy.Fact(12L, "ISO27001", "", REPLAY_DAY,
                "IN_STOCK", false, null);
        var laterRecord = simpleFact(13L, "ISO27001", REPLAY_DAY);

        var result = estimate1133(List.of(unknown, expired, validOnExpiry, laterRecord));
        assertThat(result.score()).isEqualByComparingTo("3");
        assertThat(result.basis()).contains("ISO27001 +3分（#12 ISO27001，有效至 " + REPLAY_DAY + "）")
                .doesNotContain("ISO27001 有效期待核验", "#13");
    }

    @Test
    void roundsIndependentCertificateTotalOnlyAfterSummingMatchedAwards() {
        String detail = "具有ISO9001得0.125分；具有ISO14001得0.125分；具有ISO45001得0.25分；满分0.50分。";
        var quality = simpleFact(1L, "ISO9001", REPLAY_DAY);
        var environment = simpleFact(2L, "ISO14001", REPLAY_DAY);
        var weight = new BigDecimal("0.50");

        var partial = CertificateScorePolicy.assessSimple("体系认证", detail, weight, REPLAY_DAY, List.of(quality));
        assertThat(partial.score()).isEqualByComparingTo("0.13");
        assertThat(partial.status()).isEqualTo("PENDING");
        assertThat(partial.basis()).contains("预计0.13/0.50分", "ISO9001 +0.125分");
        var twoMatches = CertificateScorePolicy.assessSimple("体系认证", detail, weight, REPLAY_DAY,
                List.of(quality, environment));
        assertThat(twoMatches.score()).isEqualByComparingTo("0.25");
    }

    @Test
    void roundedFullScoreRequiresAllCertificateAwardsForOkStatus() {
        String detail = "具有ISO9001得0.497分；具有ISO27001得0.003分；满分0.50分。";
        var quality = simpleFact(1L, "ISO9001", REPLAY_DAY);
        var security = simpleFact(2L, "ISO27001", REPLAY_DAY);
        var weight = new BigDecimal("0.50");

        var partial = CertificateScorePolicy.assessSimple("体系认证", detail, weight, REPLAY_DAY, List.of(quality));
        assertThat(partial.score()).isEqualByComparingTo("0.50");
        assertThat(partial.status()).isEqualTo("PENDING");
        assertThat(partial.basis()).contains("命中1项", "ISO27001 无有效记录");

        var full = CertificateScorePolicy.assessSimple("体系认证", detail, weight, REPLAY_DAY, List.of(quality, security));
        assertThat(full.score()).isEqualByComparingTo("0.50");
        assertThat(full.status()).isEqualTo("OK");
    }

    @Test
    void project1133RecognizesShortInformationSecurityNameWithMatchingStandard() {
        var security = simpleFact(4L, "ISO27001 信息安全认证", REPLAY_DAY);
        var otherStandard = simpleFact(17L, "ISO/IEC27017 信息安全认证", REPLAY_DAY);

        assertThat(estimate1133(List.of(security)).score()).isEqualByComparingTo("3");
        assertThat(estimate1133(List.of(otherStandard)).score()).isZero();
    }

    private static CertificateScorePolicy.Assessment estimate1133(List<CertificateScorePolicy.Fact> facts) {
        return CertificateScorePolicy.assessSimple("体系认证", PROJECT_1133, new BigDecimal("6"), REPLAY_DAY, facts);
    }

    private static CertificateScorePolicy.Fact simpleFact(Long id,String name,LocalDate day) {
        return new CertificateScorePolicy.Fact(id,name,"",day.plusYears(1),"IN_STOCK",false,day.minusYears(1),"MANAGEMENT");
    }

    @Test void explicitStandardEditionsRequireHumanReview() {
        for (String edition : List.of("2015", "2099")) {
            for (String certificate : List.of("ISO9001:2008质量管理体系认证证书", "ISO9001")) {
                var result = assess("具有ISO9001:" + edition + "质量管理体系认证证书得10分", List.of(fact(certificate)));
                assertThat(result.status()).isEqualTo("PENDING");
                assertThat(result.score()).isNull();
            }
        }
    }
    @Test void allStandardsAreRequiredAndDuplicatesDoNotFillMissingStandard() {
        var all=List.of(fact("ISO 9001认证"),fact("ISO14001环境认证"),fact("ISO45001职业健康管理认证证书"));
        assertThat(assess(ISO,all).score()).isEqualByComparingTo("10");
        for(int missing=0;missing<3;missing++) { var subset=new ArrayList<>(all);subset.remove(missing);assertThat(assess(ISO,subset).score()).isZero(); }
        assertThat(assess(ISO,List.of(fact("ISO20000"),fact("ISO27001"))).score()).isZero();
        assertThat(assess(ISO,List.of(fact("ISO9001"),fact("ISO9001"),fact("ISO14001"))).score()).isZero();
    }
    @Test void aliasesAndUnknownExpiryAndInvalidFacts() {
        var facts=new ArrayList<>(List.of(fact("GB/T19001"),fact("GB/T24001"),fact("GB/T45001")));
        assertThat(assess(ISO,facts).status()).isEqualTo("OK");
        facts.set(2,new CertificateScorePolicy.Fact("ISO45001",null,null,"IN_STOCK",false,null));
        assertThat(assess(ISO,facts).status()).isEqualTo("PENDING");
        for(String status:List.of("EXPIRED","RETIRED")) { facts.set(2,new CertificateScorePolicy.Fact("ISO45001",null,DAY.plusYears(1),status,false,null));assertThat(assess(ISO,facts).status()).isEqualTo("DANGER"); }
        facts.set(2,new CertificateScorePolicy.Fact("ISO45001",null,DAY.minusDays(1),"IN_STOCK",false,null));
        assertThat(assess(ISO,facts).score()).isZero();
    }
    @Test void honorStaircasePreservesIdentityAndLevel() {
        var all=List.of(fact("中国品牌信誉评价认证五星级证书"),fact("企业质量信用等级评价认证3A级证书"),fact("商品售后五星级认证"),fact("企业标准化管理体系认证3A级证书"));
        String[] scores={"0","1","2","3","5"};
        for(int n=0;n<=4;n++) {
            var result=CertificateScorePolicy.assess("企业荣誉",HONOR,new BigDecimal("5"),DAY,all.subList(0,n));
            assertThat(result.status()).isEqualTo(n==0?"DANGER":n==4?"OK":"PENDING");
        }
        for(int n=0;n<=4;n++) assertThat(assess(HONOR,all.subList(0,n)).score()).isEqualByComparingTo(scores[n]);
        assertThat(assess(HONOR,List.of(fact("中国品牌信誉评价认证三星级证书"))).score()).isZero();
        assertThat(assess(HONOR,List.of(fact("中国品牌信誉评价认证证书"))).status()).isEqualTo("PENDING");
    }
    @Test void knownMissingRequiredStandardDeterminesZeroDespiteAnotherUnknown() {
        var unknown=new CertificateScorePolicy.Fact("ISO14001",null,null,"IN_STOCK",false,null);
        var result=assess(ISO,List.of(unknown,fact("ISO45001")));
        assertThat(result.score()).isZero();
        assertThat(result.status()).isEqualTo("DANGER");
    }
    @Test void originalMarkdownAndNumberedProofWrapperHaveNoUnexplainedResidue() {
        String raw="1 | 管理体系 | 1、评审标准：1）具有 ISO9001 质量管理体系认证、ISO14001环境管理体系认证、ISO45001职业健康安\\n全管理体系认证的得10分。2）三项齐全得满分，缺少一项，不得分3）不满足或未提供证明文件者，不\\n得分2、证明文件：投标人须提供在有效期内的认证证书复印件或扫描件加盖投标人公章。 | 10.0 | 否";
        var facts=List.of(fact("ISO9001"),fact("ISO14001"),fact("ISO45001"));
        assertThat(assess(raw,facts).score()).isEqualByComparingTo("10");
        assertThat(assess(raw.replace("加盖投标人公章", "加盖投标人公章且认证范围必须覆盖特定业务"),facts).status()).isEqualTo("PENDING");
        var personnel=new CertificateScorePolicy.Fact(1L,"ISO9001",null,DAY.plusYears(1),"IN_STOCK",false,null,"PERSONNEL");
        assertThat(assess("具有ISO9001质量管理体系认证证书",List.of(personnel)).score()).isZero();
        assertThat(assess("具有ISO9001质量管理体系认证证书",List.of(fact("ISO9001认证情况说明"))).status()).isEqualTo("PENDING");
    }
    @Test void unexplainedSubstantiveConditionsNeverFallThroughToFullScore() {
        var iso=List.of(fact("ISO9001"),fact("ISO14001"),fact("ISO45001"));
        for(String text:List.of("具有ISO9001认证且获得CMMI5认证得10分", "具有ISO9001认证证书且认证有效期不少于一年得10分", "具有ISO9001认证证书且认证范围包含医疗器械得10分", ISO+"。同时还须具有安全生产许可证，否则不得分", ISO+"。每种标准须提供两张不同主体的认证证书"))
            assertThat(assess(text,iso).status()).as(text).isEqualTo("PENDING");
        var honor=List.of(fact("中国品牌信誉评价认证五星级证书"),fact("企业质量信用等级评价认证3A级证书"),fact("商品售后五星级认证"),fact("企业标准化管理体系认证3A级证书"));
        for(String text:List.of(HONOR+"以上等级均须达到六星以上及5A级以上",HONOR+"任意1项得2分"))
            assertThat(assess(text,honor).status()).as(text).isEqualTo("PENDING");
    }
    @Test void materialReferencesAndPersonnelTrainingAreNotEnterpriseCertification() {
        assertThat(CertificateScoreRule.isCertificateScore("服务承诺","提供ISO9001证书管理说明，材料完整得10分")).isFalse();
        assertThat(assess("具有ISO9001质量管理体系认证证书",List.of(fact("ISO9001质量管理体系内审员培训合格证书"))).score()).isZero();
    }
    @Test void gradeTokensAreExactAndDoNotPermitSubstringUpgrades() {
        for(String grade:List.of("13A","5A","十三星级","十五星级","六星级")) {
            var candidate=new CertificateScorePolicy.Fact("中国品牌信誉评价认证证书",grade,DAY.plusYears(1),"IN_STOCK",false,null);
            assertThat(assess(HONOR,List.of(candidate)).status()).as(grade).isEqualTo("PENDING");
        }
        var candidate=new CertificateScorePolicy.Fact("企业质量信用等级评价认证证书","13A",DAY.plusYears(1),"IN_STOCK",false,null);
        assertThat(assess(HONOR,List.of(candidate)).status()).isEqualTo("PENDING");
    }
    @Test void explicitSingleStandardKeepsExistingHardRequirementBehavior() {
        String detail="具有ISO9001质量管理体系认证证书";
        assertThat(CertificateScoreRule.isCertificateScore("资质",detail)).isTrue();
        assertThat(CertificateScorePolicy.assess("资质",detail,new BigDecimal("8"),DAY,List.of(fact("ISO9001"))).score()).isEqualByComparingTo("8");
        assertThat(CertificateScorePolicy.assess("资质",detail,new BigDecimal("8"),DAY,List.of()).score()).isZero();
        assertThat(CertificateScorePolicy.assess("资质",detail,new BigDecimal("8"),DAY,List.of(new CertificateScorePolicy.Fact("ISO9001",null,null,"IN_STOCK",false,null))).status()).isEqualTo("PENDING");
    }
    @Test void conflictingOrUnrecognizedRequirementsRemainManual() {
        var all=List.of(fact("ISO9001"),fact("ISO14001"),fact("ISO45001"));
        assertThat(assess(ISO+"并具有ISO27001证书",all).status()).isEqualTo("PENDING");
        assertThat(CertificateScorePolicy.assess("管理体系",ISO,new BigDecimal("20"),DAY,all).status()).isEqualTo("PENDING");
        assertThat(assess(ISO.replace("缺少一项", "缺少一项每项扣2分"),all).status()).isEqualTo("PENDING");
        assertThat(assess(HONOR.replace("任意４项则得5分", "任意４项则得1分"),List.of()).status()).isEqualTo("PENDING");
        var conflict=new CertificateScorePolicy.Fact("中国品牌信誉评价认证五星级证书","三星级",DAY.plusYears(1),"IN_STOCK",false,null);
        assertThat(assess(HONOR,List.of(conflict)).score()).isZero();
    }
    @Test void unsupportedIsPendingAndMaterialMentionIsNotCertificateRule() {
        assertThat(assess("具有ISO9001证书酌情得分",List.of(fact("ISO9001"))).status()).isEqualTo("PENDING");
        assertThat(CertificateScoreRule.isCertificateScore("实施方案","方案应说明人员证书管理及培训措施，根据方案完整性评分")).isFalse();
        assertThat(CertificateScoreRule.isCertificateScore("管理体系",ISO)).isTrue();
        for(String dim:List.of("服务方案","技术方案","团队方案"))
            assertThat(CertificateScoreRule.isCertificateScore(dim,"提供ISO9001证书说明，方案完整得10分")).isFalse();
    }
    @Test void originalHonorMarkdownWithOriginalsAvailableProofRemainsSupported() {
        String original="5 | 企业荣誉 | 1、评审标准：1）具有中国品牌信誉评价认证五星级证书；企业质量信用等级评价认证3A级证书；商品\n售后五星级认证；企业标准化管理体系认证3A级证书；2）以上４项，具备任意４项则得5分，具备任\n意３项得3分，具备任意２项得2分，具备任意１项的得1分，不满足任意1项或未提供证明文件者，\n不得分。2、证明文件：投标人须提供在有效期内的认证证书复印件或扫描件加盖投标人公章，原件备查\n。 | 5.0 | 否";
        assertThat(CertificateScoreRule.parse("企业荣誉",original,new BigDecimal("5")).supported()).isTrue();
        assertThat(CertificateScoreRule.parse("企业荣誉",original.replace("原件备查", "原件备查并须另具CMMI证书"),new BigDecimal("5")).supported()).isFalse();
    }

    @Test
    void testProject1117TrailingPipeAndTripleStandardClause() {
        String raw = "ISO45001职业健康安全管理体系认证的得10分。2）三项齐全得满分，缺少一项，不得分3）不满足或未提供证明文件者，不得分2、证明文件：投标人须提供在有效期内的认证证书复印件或扫描件加盖投标人公章。| 10.0 | 否";
        var all = List.of(fact("ISO9001"), fact("ISO14001"), fact("ISO45001"));
        var result = assess(raw, all);
        assertThat(result.score()).isEqualByComparingTo("10");
        assertThat(result.status()).isEqualTo("OK");
    }

}
