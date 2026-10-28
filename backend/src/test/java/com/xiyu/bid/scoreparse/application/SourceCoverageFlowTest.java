package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceCoverageFlowTest {
    @Test void omittedRowRemainsDiagnosableAfterFailedRetry() throws Exception {
        var result = omittedRowResult();
        assertThat(result.sourceDiagnostics()).anyMatch(d -> d.startsWith("SOURCE_ROW_RECOVERED_FOR_REVIEW:"));
        assertThat(result.sourceDiagnostics()).noneMatch(d -> d.startsWith("SOURCE_SCORE_ROW_UNCOVERED:"));
        assertThat(result.originalVerified()).isFalse();
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");
        assertThat(result.completeness().defects()).noneMatch(d -> d.contains("WEIGHT_SUM_MISMATCH") && d.contains("actual=95"));
        assertThat(result.facts()).allSatisfy(f -> assertThat(f.weightEvidence().explanation())
                .doesNotContain("WEIGHT_SUM_MISMATCH[field=weight, expected=null, actual=95"));

        assertThat(result.facts()).filteredOn(f -> "3".equals(f.code())).singleElement()
                .satisfies(f -> {
                    assertThat(f.weight()).isEqualByComparingTo("5");
                    assertThat(f.sourceText()).isEqualTo("丙项依据年报综合评审。");
                    assertThat(f.validationStatus()).isEqualTo(com.xiyu.bid.scoreparse.domain.ScoreFact.ValidationStatus.REVIEW_REQUIRED);
                });
        assertThat(result.qualifications()).anySatisfy(q -> {
            assertThat(q.candidateEligible()).isTrue();
            assertThat(result.facts()).anyMatch(f -> "3".equals(f.code()) && f.factId().equals(q.factId()));
        });

    }
    static StructuredTableResult omittedRowResult() throws Exception { return omittedRowResult(false); }
    static StructuredTableResult omittedRowAndDeductionResult() throws Exception { return omittedRowResult(true); }
    private static StructuredTableResult omittedRowResult(boolean deduction) throws Exception {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"条款号","评审因素","评审标准","分值"}, {"1","甲","甲项得50分。","50"},
                {"2","乙","乙项得45分。","45"}, {"3","丙","丙项依据年报综合评审。","5"}};
        for (int r=0;r<rows.length;r++) for (int c=0;c<4;c++)
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,r,c,rows[r][c],rows[r][c],1,1,false,"pdf:page=1",1,"t-r"+r));
        if (deduction) {
            String[] values = {"4", "质量扣分", "未通过验收按规定扣分。", "0"};
            for (int c=0;c<4;c++) cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                    "t-r4-c"+c,4,c,values[c],values[c],1,1,false,"pdf:page=1",1,"t-r4"));
        }
        var table = new DocumentArtifact.CanonicalTable("t",1,"第四章 评审办法",deduction ? 5 : 4,4,List.of(),cells,true,"PDF");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","sha","PDF","2.2"),List.of(table),List.of());
        var sidecar = new SidecarDocumentArtifactExtractor("http://localhost:9","",1,new ObjectMapper()) {
            public DocumentArtifact extract(byte[] bytes,String name) { return artifact; }
        };
        var extractor = new TextAwareScoreFactExtractorTestAdapter() {
            public boolean supportsTextOnly() { return true; }
            public ExtractionResult extractWithTextAndProgress(DocumentArtifact a,String text,Runnable heartbeat) {
                return new ExtractionResult(null,List.of(fact("1","甲",50),fact("2","乙",45)),"first",0);
            }
            public ExtractionResult recheckWithTextAndProgress(DocumentArtifact a,String text,Runnable heartbeat) {
                return new ExtractionResult(null,List.of(),"retry",0);
            }
        };
        return MultimodalStructuredExtractionHandler.extract(new TenderTextSource("bid.pdf","memory:0","# 第四章 评审办法\n"),
                new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","application/pdf"),sidecar,extractor);
    }

    @Test void recoveryRechecksAgainstKnownOriginalTotalWithoutAutoApproval() {
        var result = recoveredValidationWithDeclaredTotal("100");
        assertThat(result.originalTotal()).isEqualByComparingTo("100");
        assertThat(result.leafTotal()).isEqualByComparingTo("100");
        assertThat(result.violations()).noneMatch(v -> "WEIGHT_SUM_MISMATCH".equals(v.code()));
        assertThat(result.autoApprovable()).isFalse();
    }

    @Test void recoveryKeepsRealMismatchWithUpdatedActualSum() {
        var result = recoveredValidationWithDeclaredTotal("110");
        assertThat(result.leafTotal()).isEqualByComparingTo("100");
        assertThat(result.violations()).anySatisfy(v -> {
            assertThat(v.code()).isEqualTo("WEIGHT_SUM_MISMATCH");
            assertThat(v.expected()).isEqualTo(new BigDecimal("110"));
            assertThat(v.actual()).isEqualTo(new BigDecimal("100"));
        });
        assertThat(result.autoApprovable()).isFalse();
    }

    private static com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator.ValidationReport
            recoveredValidationWithDeclaredTotal(String total) {
        var source = recoveryArtifact(true);
        String text = "# 第四章 评审办法\n本项目总分" + total + "分。";
        var context = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(source, text);
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator().validate(
                List.of(fact("1", "甲", 50), fact("2", "乙", 45)), source, context, text, new BigDecimal(total));
        var recovery = SourceReviewRowRecovery.recover(source, context, text, validation);
        assertThat(recovery.facts()).hasSize(1);
        return SourceReviewRowRecovery.reviewValidation(validation, recovery, source);
    }

    @Test void sourceRecoveryDoesNotDuplicateAnUnboundSameCodeCandidate() {
        var source = recoveryArtifact(true);
        var ambiguous = new MultimodalScoreFact("第四章 评审办法", "3", "", "丙", BigDecimal.valueOf(5),
                "待核对", "OBJECTIVE", 1, "", List.of(), "无法验证的引用", .9);
        var facts = List.of(fact("1", "甲", 50), fact("2", "乙", 45), ambiguous);
        var context = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(source, "# 第四章 评审办法\n");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(facts, source, context, "# 第四章 评审办法\n", null);
        var result = SourceReviewRowRecovery.recover(source, context, "# 第四章 评审办法\n", validation);
        assertThat(result.facts()).noneMatch(f -> "3".equals(f.code()));
    }

    @Test void sourceRecoveryDoesNotGuessWhichRowAnUnboundCandidateBelongsTo() {
        var source = recoveryArtifact(true);
        var uncertain = new MultimodalScoreFact("第四章 评审办法", "03", "", "未知名称", BigDecimal.valueOf(5),
                "待核对", "OBJECTIVE", 1, "", List.of(), "无法验证的引用", .9);
        var facts = List.of(fact("1", "甲", 50), fact("2", "乙", 45), uncertain);
        var context = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(source, "# 第四章 评审办法\n");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(facts, source, context, "# 第四章 评审办法\n", null);
        var result = SourceReviewRowRecovery.recover(source, context, "# 第四章 评审办法\n", validation);
        assertThat(result.facts()).noneMatch(f -> "3".equals(f.code()));
    }

    @Test void sourceRecoveryDoesNotDuplicateAnUnboundChild() {
        var source = recoveryArtifact(true);
        var child = new MultimodalScoreFact("第四章 评审办法", "3.1", "3", "丙细项", BigDecimal.valueOf(5),
                "待核对", "OBJECTIVE", 1, "", List.of(), "无法验证的引用", .9);
        var facts = List.of(fact("1", "甲", 50), fact("2", "乙", 45), child);
        var context = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(source, "# 第四章 评审办法\n");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(facts, source, context, "# 第四章 评审办法\n", null);
        var result = SourceReviewRowRecovery.recover(source, context, "# 第四章 评审办法\n", validation);
        assertThat(result.facts()).noneMatch(f -> "3".equals(f.code()));
    }

    @Test void sourceRecoveryDoesNotDuplicateFactBoundToContinuationRow() {
        var base = recoveryArtifact(true);
        var table = base.tables().getFirst();
        var cells = new ArrayList<>(table.cells());
        String[] values = {"", "", "续行评分依据。", ""};
        for (int c = 0; c < values.length; c++) cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "t-r4-c"+c, 4, c, values[c], values[c], 1, 1, false, "pdf:page=1", 1, "t-r3"));
        var source = new DocumentArtifact(base.artifact(), List.of(new DocumentArtifact.CanonicalTable(
                "t", 1, "第四章 评审办法", 5, 4, List.of(), cells, true, "PDF")), List.of());
        var continuation = new MultimodalScoreFact("第四章 评审办法", "aux", "", "丙", BigDecimal.valueOf(5),
                "续行评分依据。", "OBJECTIVE", 1, "t", List.of("t-r4-c2"), "续行评分依据。", .9);
        var facts = List.of(fact("1", "甲", 50), fact("2", "乙", 45), continuation);
        var context = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(source, "# 第四章 评审办法\n");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(facts, source, context, "# 第四章 评审办法\n", null);
        var result = SourceReviewRowRecovery.recover(source, context, "# 第四章 评审办法\n", validation);
        assertThat(result.facts()).noneMatch(f -> "3".equals(f.code()));
    }

    @Test void sourceRecoveryPreservesAnOmittedZeroPointDeductionRule() {
        var base = recoveryArtifact(true);
        var table = base.tables().getFirst();
        var cells = new ArrayList<>(table.cells());
        String[] values = {"4", "质量扣分", "未通过验收按规定扣分。", "0"};
        for (int c = 0; c < values.length; c++) cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "t-r4-c"+c, 4, c, values[c], values[c], 1, 1, false, "pdf:page=1", 1, "t-r4"));
        var source = new DocumentArtifact(base.artifact(), List.of(new DocumentArtifact.CanonicalTable(
                "t", 1, "第四章 评审办法", 5, 4, List.of(), cells, true, "PDF")), List.of());
        var row3 = new MultimodalScoreFact("第四章 评审办法", "3", "", "丙", BigDecimal.valueOf(5),
                "丙项依据年报综合评审。", "OBJECTIVE", 1, "t", List.of("t-r3-c2"), "丙项依据年报综合评审。", .9);
        var facts = List.of(fact("1", "甲", 50), fact("2", "乙", 45), row3);
        var context = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(source, "# 第四章 评审办法\n");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(facts, source, context, "# 第四章 评审办法\n", null);
        var result = SourceReviewRowRecovery.recover(source, context, "# 第四章 评审办法\n", validation);
        assertThat(result.facts()).filteredOn(f -> "4".equals(f.code())).singleElement().satisfies(f -> {
            assertThat(f.weight()).isEqualByComparingTo("0");
            assertThat(f.sourceText()).isEqualTo("未通过验收按规定扣分。");
        });
    }

    @Test void sourceRecoveryRequiresPhysicalLocation() {
        var source = recoveryArtifact(false);
        var facts = List.of(fact("1", "甲", 50), fact("2", "乙", 45));
        var context = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(source, "# 第四章 评审办法\n");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(facts, source, context, "# 第四章 评审办法\n", null);
        var result = SourceReviewRowRecovery.recover(source, context, "# 第四章 评审办法\n", validation);
        assertThat(result.facts()).isEmpty();
    }

    private static DocumentArtifact recoveryArtifact(boolean located) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"条款号","评审因素","评审标准","分值"}, {"1","甲","甲项得50分。","50"},
                {"2","乙","乙项得45分。","45"}, {"3","丙","丙项依据年报综合评审。","5"}};
        for (int r=0;r<rows.length;r++) for (int c=0;c<4;c++) cells.add(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,r,c,rows[r][c],rows[r][c],1,1,false,
                        located ? "pdf:page=1" : "", located ? 1 : null,"t-r"+r));
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","sha","PDF","2.2"),
                List.of(new DocumentArtifact.CanonicalTable("t",located ? 1 : null,"第四章 评审办法",4,4,List.of(),cells,true,"PDF")),List.of());
    }

    private static MultimodalScoreFact fact(String code,String name,int weight) {
        String quote=name+"项得"+weight+"分。";
        return new MultimodalScoreFact("第四章 评审办法",code,"",name,BigDecimal.valueOf(weight),quote,"OBJECTIVE",1,
                "t",List.of("t-r"+code+"-c2"),quote,.9,ChildAggregation.SUM);
    }
}
