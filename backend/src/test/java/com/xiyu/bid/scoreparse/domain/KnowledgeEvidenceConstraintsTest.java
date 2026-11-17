package com.xiyu.bid.scoreparse.domain;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class KnowledgeEvidenceConstraintsTest {
    static final LocalDate DAY=LocalDate.of(2026,11,17);
    @Test void knowledgeEstimateStillDisclosesUnverifiedMaterials() {
        for (String material : new String[]{"合同关键页","合同复印件","发票"}) {
            var constraints=KnowledgeEvidenceConstraints.parse("PROJECT","近三年内供货业绩，须提供"+material,DAY);
            assertThat(constraints.pendingChecks()).as(material).contains("业绩材料尚待核验");
            assertThat(constraints.description()).contains("待核验");
        }
    }
    @Test void allSupportedProjectTypesExcludeOtherTypesBeforeCounting() {
        for (String required : new String[]{"办公", "综合", "集采", "工业品", "其他"}) {
            var constraints=KnowledgeEvidenceConstraints.parse("PROJECT","需提供2项"+required+"业绩",DAY);
            for (String actual : new String[]{"办公", "综合", "集采", "工业品", "其他"}) {
                assertThat(constraints.acceptsProject(null,null,actual,null))
                        .as("%s业绩要求与%s记录",required,actual).isEqualTo(required.equals(actual));
            }
            assertThat(constraints.acceptsProject(null,null,null,null)).isFalse();
        }
    }
    @Test void existingProjectAliasesUseCanonicalTypes() {
        for (String[] alias : new String[][]{{"办公用品","办公"},{"办公类","办公"},{"MRO","工业品"},{"mro","工业品"}}) {
            var constraints=KnowledgeEvidenceConstraints.parse("PROJECT","需提供2项"+alias[0]+"业绩",DAY);
            assertThat(constraints.acceptsProject(null,null,alias[1],null)).isTrue();
            assertThat(constraints.acceptsProject(null,null,"综合",null)).isFalse();
        }
        var unconstrained=KnowledgeEvidenceConstraints.parse("PROJECT","需提供2项业绩及其他证明材料",DAY);
        assertThat(unconstrained.acceptsProject(null,null,"集采",null)).isTrue();
    }
    @Test void privateCustomerAliasesMatchCanonicalLibraryLabel() {
        for (String alias : new String[]{"民企","民营企业","民营"}) {
            var constraints=KnowledgeEvidenceConstraints.parse("PROJECT","需提供2项"+alias+"业绩",DAY);
            assertThat(constraints.acceptsProject(null,null,"办公","民企")).isTrue();
            assertThat(constraints.acceptsProject(null,null,"办公","央企")).isFalse();
            assertThat(constraints.acceptsProject(null,null,"办公",null)).isFalse();
        }
    }
    @Test void yearAndMonthRangesHaveRealUpperBounds() {
        var years=KnowledgeEvidenceConstraints.parse("PROJECT","提供2023年至2024年签订的业绩合同",DAY);
        assertThat(years.signedBefore()).isEqualTo(LocalDate.of(2024,12,31));
        assertThat(years.acceptsDate(LocalDate.of(2025,1,1))).isFalse();
        var months=KnowledgeEvidenceConstraints.parse("PROJECT","提供2023年2月至2024年2月签订的业绩合同",DAY);
        assertThat(months.signedBefore()).isEqualTo(LocalDate.of(2024,2,29));
        assertThat(months.acceptsDate(null)).isFalse();
    }
    @Test void untilNowAndRecentYearsExcludeFutureRecords() {
        for(String text:new String[]{"提供2023年1月1日至今签订的业绩","提供近三年业绩"}) {
            var constraints=KnowledgeEvidenceConstraints.parse("PROJECT",text,DAY);
            assertThat(constraints.acceptsDate(DAY.plusDays(1))).isFalse();
            assertThat(constraints.acceptsDate(DAY)).isTrue();
        }
    }
    @Test void numericDateLowerBoundsApplyToEstimatesAndFormalQuotes() {
        for (String start : new String[]{"2023-01-01", "2023/01/01", "2023.01.01"}) {
            for (String suffix : new String[]{"至今", "以来", "之后"}) {
                String detail=start+suffix+"签订的业绩合同";
                var estimate=KnowledgeEvidenceConstraints.parse("PROJECT",detail,DAY);
                var scoring=KnowledgeEvidenceConstraints.parseForScoring(detail,DAY);
                for (var constraints : new KnowledgeEvidenceConstraints[]{estimate,scoring}) {
                    assertThat(constraints.signedAfter()).as(detail).isEqualTo(LocalDate.of(2023,1,1));
                    assertThat(constraints.acceptsDate(LocalDate.of(2020,5,27))).as(detail).isFalse();
                    assertThat(constraints.acceptsDate(null)).as(detail).isFalse();
                    assertThat(constraints.acceptsDate(LocalDate.of(2023,1,1))).as(detail).isTrue();
                    assertThat(constraints.quoteFailure("合同签订日期2020-05-27")).as(detail).contains("日期");
                    assertThat(constraints.quoteFailure("合同签订日期2023-01-01")).as(detail).isEmpty();
                }
                if (suffix.equals("至今")) {
                    assertThat(estimate.signedBefore()).isEqualTo(DAY);
                    assertThat(estimate.acceptsDate(DAY.plusDays(1))).isFalse();
                }
            }
        }
    }
    @Test void invalidNumericLowerBoundCannotSilentlyBecomeUnbounded() {
        assertThatThrownBy(()->KnowledgeEvidenceConstraints.parse("PROJECT","2023-02-30至今签订的业绩合同",DAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->KnowledgeEvidenceConstraints.parseForScoring("2023-02-30至今签订的业绩合同",DAY))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void namedBrandAndProvinceMustBeVerifiedByTheMatchingFields() {
        var brands=KnowledgeEvidenceConstraints.parse("BRAND","提供施耐德品牌授权",DAY);
        assertThat(brands.acceptsBrand("西门子",null)).isFalse();
        assertThat(brands.acceptsBrand("施耐德",null)).isTrue();
        var warehouse=KnowledgeEvidenceConstraints.parse("WAREHOUSE","须具有广东省仓库",DAY);
        assertThat(warehouse.acceptsWarehouse("华北",null,BigDecimal.TEN)).isFalse();
        assertThat(warehouse.acceptsWarehouse("华南","广西",BigDecimal.TEN)).isFalse();
        assertThat(warehouse.acceptsWarehouse("华南","广东省",BigDecimal.TEN)).isTrue();
    }
    @Test void quantityModifiersDoNotSilentlyTurnTwoRequiredRecordsIntoOne() {
        assertThat(ScoreItemConstraintExtractor.requiredForEstimate("需提供2项央企业绩","PROJECT",BigDecimal.TEN).count()).isEqualTo(2);
        assertThat(ScoreItemConstraintExtractor.requiredForEstimate("至少3名注册建造师","PERSON",BigDecimal.TEN).count()).isEqualTo(3);
    }
    @Test void explicitUnknownDateDoesNotBecomeAnUnboundedSearch() {
        assertThatThrownBy(()->KnowledgeEvidenceConstraints.parse("PROJECT","提供2023年以前签订的业绩合同",DAY)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void dotDatesAndMissingAmountCannotPassFormalChecks() {
        var constraints=KnowledgeEvidenceConstraints.parse("PROJECT","2023年1月1日至今，合同金额不少于100万元",DAY);
        assertThat(constraints.quoteFailure("合同签订日期2020.05.27，合同金额100万元")).contains("日期");
        assertThat(constraints.quoteFailure("合同签约日期2024.05.27，合同金额50万元")).contains("金额");
        assertThat(constraints.quoteFailure("合同签订时间2024-05-27")).contains("金额");
        assertThat(constraints.quoteFailure("合同签订日期2024.05.27，合同金额100万元")).isEmpty();
    }
}
