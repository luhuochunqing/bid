package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoreWeightUnitAuthorityTest {
    @ParameterizedTest @NullSource @ValueSource(strings = "40")
    void namedWholeUnitCanContainLocalComponentMaxima(String column) {
        var problems = verify("运输服务（40分）", "规则含局部满分30分和满分10分。", column,
                "运输服务", "40", "运输服务");
        assertThat(problems).isEmpty();
    }

    @Test void aLocatedSubunitDoesNotInheritAllMaximaOfItsSharedCell() {
        var problems = verify("配送服务（40分）", "配送服务满分40分。\n"
                + "1、城市配送（28分）：城市配送满分28分。\n"
                + "2、郊区配送（12分）：郊区配送满分12分。", null,
                "配送服务", "28", "1、城市配送（28分）");
        assertThat(problems).isEmpty();
    }

    @Test void localComponentsDoNotExcuseAnIndependentWholeMaximum() {
        var problems = verify("运输服务（40分）", "局部满分30分和满分10分，运输服务最高得35分。", "40",
                "运输服务", "40", "局部满分30分和满分10分，运输服务最高得35分。");
        assertThat(problems).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_WEIGHT_SOURCE_UNKNOWN");
    }

    @ParameterizedTest @ValueSource(strings = {"运输服务最高得90分。", "运输服务：本项最高得90分。"})
    void aWrongCandidateValueKeepsTheUnambiguousSourceMismatch(String rule) {
        var problems = verify("运输服务", rule, "90", "运输服务", "80", rule);
        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.code()).isEqualTo("ITEM_WEIGHT_SOURCE_MISMATCH");
            assertThat((BigDecimal) problem.expected()).isEqualByComparingTo("90");
            assertThat((BigDecimal) problem.actual()).isEqualByComparingTo("80");
        });
    }

    @ParameterizedTest @ValueSource(strings = {"90", "100"})
    void sourceDisagreementIsBlockedWhicheverValueTheCandidateUses(String candidate) {
        var problems = verify("运输服务", "运输服务最高得90分。", "100",
                "运输服务", candidate, "运输服务最高得90分。");
        assertThat(problems).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_WEIGHT_SOURCE_UNKNOWN");
    }

    @Test void aSourceBoundChildRetainsItsOwnMaximum() {
        var report = sharedRuleReport("28");
        assertThat(report.qualifications()).hasSize(3).allMatch(q -> q.candidateEligible());
        assertThat(report.problems()).noneMatch(p -> "weight".equals(p.field()));
    }

    @Test void aWrongChildValueCannotBorrowTheParentColumn() {
        var report = sharedRuleReport("40");
        assertThat(report.problems()).anySatisfy(p -> {
            assertThat(p.code()).isEqualTo("ITEM_WEIGHT_SOURCE_MISMATCH");
            assertThat((BigDecimal) p.expected()).isEqualByComparingTo("28");
            assertThat((BigDecimal) p.actual()).isEqualByComparingTo("40");
        });
    }

    private OriginalScoreEvidencePolicy.Verification sharedRuleReport(String childWeight) {
        return sharedRuleReport(childWeight, false);
    }

    private OriginalScoreEvidencePolicy.Verification sharedRuleReport(String childWeight, boolean unaryChild) {
        return sharedRuleReport(childWeight, unaryChild, false);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deterministicFactsKeepTheLocatedChildQuote(boolean unaryChild) {
        var report = sharedRuleReport("28", unaryChild, true);
        assertThat(report.qualifications()).hasSize(unaryChild ? 4 : 3).allMatch(q -> q.candidateEligible());
        assertThat(report.problems()).isEmpty();
    }

    @Test void deterministicWrongChildCannotBorrowItsParentMaximum() {
        assertThat(sharedRuleReport("40", false, true).problems()).anySatisfy(p -> {
            assertThat(p.code()).isEqualTo("ITEM_WEIGHT_SOURCE_MISMATCH");
            assertThat((BigDecimal) p.expected()).isEqualByComparingTo("28");
            assertThat((BigDecimal) p.actual()).isEqualByComparingTo("40");
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aConditionalTierDoesNotOverrideTheLocatedChildMaximum(boolean deterministic) {
        var report = sharedRuleReport("28", false, deterministic,
                "仅提供宣传材料的，本项最高得0.2分。");
        assertThat(report.qualifications()).hasSize(3).allMatch(q -> q.candidateEligible());
        assertThat(report.problems()).isEmpty();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aConditionalTierDoesNotExcuseAnIndependentChildConflict(boolean deterministic) {
        var report = sharedRuleReport("28", false, deterministic,
                "仅提供宣传材料的，本项最高得0.2分。本项最高得27分。");
        assertThat(report.problems()).anySatisfy(p -> {
            assertThat(p.code()).isEqualTo("ITEM_WEIGHT_SOURCE_UNKNOWN");
            assertThat((BigDecimal) p.expected()).isEqualByComparingTo("27");
            assertThat((BigDecimal) p.actual()).isEqualByComparingTo("28");
        });
    }

    private OriginalScoreEvidencePolicy.Verification sharedRuleReport(String childWeight, boolean unaryChild,
                                                                     boolean deterministic) {
        return sharedRuleReport(childWeight, unaryChild, deterministic, "");
    }

    private OriginalScoreEvidencePolicy.Verification sharedRuleReport(String childWeight, boolean unaryChild,
                                                                     boolean deterministic, String childSuffix) {
        String rule = "配送服务满分40分。\n1、城市配送（28分）：城市配送最高得28分。"
                + childSuffix + "\n2、郊区配送（12分）：郊区配送最高得12分。";
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"序号", "评审因素", "评分标准", "分值"}, {"1", "配送服务（40分）", rule, "40"}};
        for (int r=0; r<2; r++) for (int c=0; c<4; c++)
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,
                    r,c,rows[r][c],rows[r][c],1,1,false,"pdf:page=1",1,"t-r"+r));
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","sha","PDF","2.2"),
                List.of(new DocumentArtifact.CanonicalTable("t",1,"本项目评分办法",2,4,List.of(),cells,true)),List.of());
        var facts = new ArrayList<>(List.of(
                new MultimodalScoreFact("本项目评分办法","p","","配送服务",new BigDecimal("40"),"配送服务（40分）",
                        "OBJECTIVE",1,"t",List.of("t-r1-c1"),"配送服务（40分）",0.9),
                new MultimodalScoreFact("本项目评分办法","p.1","p","配送服务",new BigDecimal(childWeight),"1、城市配送（28分）",
                        "OBJECTIVE",1,"t",List.of("t-r1-c2"),"1、城市配送（28分）",0.9),
                new MultimodalScoreFact("本项目评分办法","p.2","p","配送服务",new BigDecimal("12"),"2、郊区配送（12分）",
                        "OBJECTIVE",1,"t",List.of("t-r1-c2"),"2、郊区配送（12分）",0.9)));
        if (unaryChild) facts.add(new MultimodalScoreFact("本项目评分办法","p.2.detail","p.2","配送服务",
                new BigDecimal("12"),"郊区配送最高得12分。","OBJECTIVE",1,"t",List.of("t-r1-c2"),"郊区配送最高得12分。",0.9));
        if (deterministic) {
            var sourceFacts = facts.stream().map(f -> new ScoreFact(f.code(), f.tableId(), f.sourceCellIds(),
                    f.quotedText(), f.code(), f.dimension(), f.weight(), null, null, f.parentCode(),
                    "STRUCTURED_CODE", f.confidence(), null, f.childAggregation(), f.sourceMetadata())).toList();
            return OriginalScoreEvidencePolicy.verifyFacts(artifact,"本项目评分总分40分。",sourceFacts);
        }
        return OriginalScoreEvidencePolicy.verifyModel(artifact,"本项目评分总分40分。",facts,new BigDecimal("40"));
    }

    @Test void aTierAwardCannotBeUsedAsTheWholeUnitMaximum() {
        var problems = verify("运输服务", "运输服务分档：优得3分，良得1分。", "10",
                "运输服务", "3", "运输服务分档：优得3分，良得1分。");
        assertThat(problems).singleElement().satisfies(p -> {
            assertThat(p.code()).isEqualTo("ITEM_WEIGHT_SOURCE_MISMATCH");
            assertThat((BigDecimal) p.expected()).isEqualByComparingTo("10");
        });
    }

    @Test void aUnaryDescendantKeepsTheLocatedAncestorUnitBoundary() {
        var report = sharedRuleReport("28", true);
        assertThat(report.qualifications()).hasSize(4).allMatch(q -> q.candidateEligible());
        assertThat(report.problems()).noneMatch(p -> "weight".equals(p.field()));
    }

    @Test void aWrongSourceIdentityKeepsTheScopeDiagnosisFirst() {
        var problems = verify("运输服务", "运输服务最高得90分，分值列注明100分。", "100",
                "其他项目", "100", "不属于此行的内容");
        assertThat(problems).isNotEmpty();
        assertThat(problems.getFirst().code()).isEqualTo("ITEM_SOURCE_SCOPE_MISMATCH");
    }

    private List<OriginalScoreEvidencePolicy.Problem> verify(String name, String rule, String column,
                                                            String dimension, String weight, String quote) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = column == null
                ? new String[][]{{"序号", "评审因素", "评分标准"}, {"1", name, rule}}
                : new String[][]{{"序号", "评审因素", "评分标准", "分值"}, {"1", name, rule, column}};
        for (int r = 0; r < rows.length; r++) for (int c = 0; c < rows[r].length; c++)
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,
                    r, c, rows[r][c], rows[r][c], 1, 1, false, "pdf:page=1", 1, "t-r"+r));
        var table = new DocumentArtifact.CanonicalTable("t", 1, "评分办法", 2, rows[0].length, List.of(), cells, true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "2.2"),
                List.of(table), List.of());
        var ids = cells.stream().filter(c -> Integer.valueOf(1).equals(c.row())).map(c -> c.cellId()).toList();
        var claim = new ScoreEvidenceClaim("fact", "fact", "t", dimension, ids,
                List.of(column == null ? "t-r1-c2" : "t-r1-c3"), new BigDecimal(weight),
                false, true, quote, "", "", "", ChildAggregation.SUM, null);
        var problems = new ArrayList<OriginalScoreEvidencePolicy.Problem>();
        OriginalScoreWeightEvidencePolicy.verifyWeight(artifact, claim, problems);
        return problems;
    }
}
