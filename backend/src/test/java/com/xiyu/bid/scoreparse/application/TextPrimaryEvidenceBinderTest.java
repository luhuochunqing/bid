package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextPrimaryEvidenceBinderTest {
    @Test void markdownWholeRowRestoresPhysicalRuleAndOriginalNameBeforeAdoption() {
        var artifact = markdownArtifact(false, true);
        var fact = markdownFact("| **经营能力** | **5** | 审计报告齐全得5分，否则0分。 |");
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").getFirst();
        assertThat(bound.sourceCellIds()).containsExactly("t-r1-c0", "t-r1-c1", "t-r1-c2");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator().validate(List.of(bound), artifact, BigDecimal.valueOf(5));
        var mapped = TextPrimaryScoreFactMapper.map(bound, artifact, validation);
        assertThat(mapped.sourceText()).isEqualTo("经营能力\n5\n审计报告齐全得5分，否则0分。");
        assertThat(mapped.dimension()).isEqualTo("经营能力");
        assertThat(mapped.weight()).isEqualByComparingTo("5");
        var adopted = new com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy().adopt(List.of(
                new com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.CandidateInput(mapped.factId(), mapped.code(), mapped.dimension(), mapped.weight(), mapped.sourceText(), "")), java.util.Set.of(mapped.factId()), java.util.Map.of());
        assertThat(adopted.success()).isTrue();
        assertThat(adopted.candidates().getFirst().detail()).isEqualTo(mapped.sourceText());
        assertThat(bound.sourceMetadata().modelQuotedText()).isEqualTo(fact.quotedText());
    }
    @Test void markdownWholeRowRejectsAmbiguousAlteredAndUnclosedPhysicalRows() {
        var quote = markdownFact("| 经营能力 | 5 | 审计报告齐全得5分，否则0分。 |");
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(quote), markdownArtifact(true, true), "").getFirst().sourceCellIds()).isEmpty();
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(quote), markdownArtifact(false, false), "").getFirst().sourceCellIds()).isEmpty();
        var altered = markdownFact("| 经营能力 | 6 | 审计报告齐全得5分，否则0分。 |");
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(altered), markdownArtifact(false, true), "").getFirst().sourceCellIds()).isEmpty();
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(quote.withScope("other-scheme", "IN_SCHEME")), markdownArtifact(false, true), "").getFirst().sourceCellIds()).isEmpty();
    }
    private static MultimodalScoreFact markdownFact(String quote) {
        return new MultimodalScoreFact("第三章 评审办法", "business_financial", "", "商务", BigDecimal.valueOf(5),
                "模型摘要", "SUBJECTIVE", null, "", List.of(), quote, .9);
    }
    private static DocumentArtifact markdownArtifact(boolean repeated, boolean closed) {
        var cells = new java.util.ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"评审项目", "分值", "评审标准"}, {"经营能力", "5", "审计报告齐全得5分，否则0分。"}};
        for (int r = 0; r < (repeated ? 3 : 2); r++) for (int c = 0; c < 3; c++) {
            String raw = rows[Math.min(r, 1)][c];
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c, r, c, raw, raw, 1, 1, false,
                    closed ? "/w:tbl[1]/w:tr["+(r+1)+"]/w:tc["+(c+1)+"]" : "", null, "t-r"+r));
        }
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "DOCX", "v2"),
                List.of(new DocumentArtifact.CanonicalTable("t", null, "第三章 评审办法", repeated ? 3 : 2, 3, List.of(), cells, true)), List.of());
    }

    @Test void bindsPriceMarkdownEscapeToOriginalScoringLineWithoutChangingQualificationRules() {
        String raw = "# 第三章 评审办法\n| 评分项 | 分值 | 规则 |\n| 价格部分 | 30 | 商城价\\*折扣率，满分30分。 |\n# 第四章 合同条款";
        String quote = "| 价格部分 | 30 | 商城价*折扣率，满分30分。 |";
        var fact = new MultimodalScoreFact("第三章 评审办法", "F_PRICE", "", "价格", BigDecimal.valueOf(30),
                quote, "OBJECTIVE", 1, "", List.of(), quote, .9);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), null, raw).getFirst();
        assertThat(bound.sourceCellIds()).containsExactly("text:line[2]");
        assertThat(bound.quotedText()).contains("商城价\\*折扣率");
        var context = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(null, raw);
        var scoped = MultimodalExtractionReviewPolicy.assignScope(bound, context, null, raw,
                com.xiyu.bid.scoreparse.domain.OriginalScoringContext.schemes(null, raw));
        assertThat(scoped.scopeStatus()).isEqualTo("IN_SCHEME");
    }
    @Test
    void bindsChineseLayoutBreakAndRestoresActualRawQuote() {
        var cell = new DocumentArtifact.CanonicalTable.CanonicalCellDto("rule", 1, 2,
                "供应商提供一条有效可行的增值服\n务方案得1分，满分4分。", "", 1, 1, false, "pdf:page=2", 2, "t-r1");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "v2"),
                List.of(new DocumentArtifact.CanonicalTable("t", 2, "第三章 评审办法", 2, 3,
                        List.of(), List.of(cell), true)), List.of());
        var fact = new MultimodalScoreFact("第三章 评审办法", "增值服务", "", "增值服务", BigDecimal.valueOf(4),
                "模型概要", "OBJECTIVE", 2, "", List.of(), "供应商提供一条有效可行的增值服务方案得1分，满分4分。", .9);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").getFirst();
        assertThat(bound.sourceCellIds()).containsExactly("rule");
        assertThat(bound.quotedText()).isEqualTo(cell.rawText());
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator().validate(
                List.of(bound), artifact, BigDecimal.valueOf(4));
        assertThat(TextPrimaryScoreFactMapper.map(bound, artifact, validation).sourceText()).isEqualTo(cell.rawText());
    }

    @Test void modelOrdinalAndAsciiComparisonGlyphStillBindOriginalRuleRow() {
        // 项目1120 task126：模型引文把「﹤」写成「<」并自加「1.」序号，导致财务状况 6 分整条无法回指。
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "DOCX", "table-v2"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 1, "第三章 评审办法", 1, 3, List.of(), List.of(
                        cell("t1-name", 0, 0, "财务状况", "t1-r1"),
                        cell("t1-rule", 0, 1, "速动比率=速动资产/流动负债，速动比﹤1，得40分。数据取值来源：①速动资产", "t1-r1"),
                        cell("t1-weight", 0, 2, "6%", "t1-r1")), true)), List.of());
        var fact = new MultimodalScoreFact("第三章 评审办法", "business_financial", "", "财务状况", BigDecimal.valueOf(6),
                "模型摘要", "OBJECTIVE", null, "", List.of(),
                "1. 速动比率=速动资产/流动负债，速动比<1，得40分。\n数据取值来源：\n①速动资产", .9);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").getFirst();

        assertThat(bound.sourceCellIds()).containsExactly("t1-name", "t1-rule", "t1-weight");
        assertThat(bound.quotedText()).isEqualTo("速动比率=速动资产/流动负债，速动比﹤1，得40分。数据取值来源：①速动资产");
        var report = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(List.of(bound), artifact, BigDecimal.valueOf(6));
        assertThat(report.violations()).noneMatch(v -> "QUOTE_NOT_VERBATIM".equals(v.code()));
        assertThat(bound.sourceMetadata().modelQuotedText()).isEqualTo(fact.quotedText());
    }

    @Test
    void bindsPdfNameAndRuleQuoteAcrossCellsInSamePhysicalRowWithoutLogicalRowId() {
        var table = new DocumentArtifact.CanonicalTable("pdf-p34-t1", 34, "第三章 评审标准", 2, 3,
                List.of(), List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("name", 1, 0, "商务资信", "商务资信",
                        1, 1, false, "pdf:page=34;row=1;cell=0", 34, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("rule", 1, 1, "信用记录良好得7分", "信用记录良好得7分",
                        1, 1, false, "pdf:page=34;row=1;cell=1", 34, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("weight", 1, 2, "7分", "7分",
                        1, 1, false, "pdf:page=34;row=1;cell=2", 34, "")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "商务资信", "", "商务", BigDecimal.valueOf(7),
                "信用记录良好得7分", "OBJECTIVE", 34, "", List.of(),
                "商务资信 信用记录良好得7分", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.tableId()).isEqualTo("pdf-p34-t1");
        assertThat(bound.sourceCellIds()).containsExactly("name", "rule");
        assertThat(bound.dimension()).isEqualTo("商务资信");
    }

    @Test
    void mixedBundleUsesPerTableFileTypeInsteadOfFirstArtifactFileType() {
        var docxTable = new DocumentArtifact.CanonicalTable("doc1-t1", 34, "第三章 评审标准", 2, 2,
                List.of(), List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("doc1-name", 1, 0, "商务资信", "商务资信",
                        1, 1, false, "word/document.xml", 34, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("doc1-rule", 1, 1, "信用记录良好得7分", "信用记录良好得7分",
                        1, 1, false, "word/document.xml", 34, "")), true, "DOCX");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bundle", "sha", "PDF", "bundle"),
                List.of(docxTable), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "1", "", "商务资信", BigDecimal.valueOf(7),
                "信用记录良好得7分", "OBJECTIVE", 34, "", List.of(),
                "商务资信 信用记录良好得7分", 0.9, ChildAggregation.SUM);

        var unbound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(unbound.tableId()).isBlank();
        assertThat(unbound.sourceCellIds()).isEmpty();
    }

    @Test
    void pdfRuleCellBindingAddsUniqueSameRowItemNameWhenDimensionIsOnlyCategory() {
        var table = new DocumentArtifact.CanonicalTable("pdf-p35-t1", 35, "第三章 评审标准", 2, 3,
                List.of(), List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("code", 1, 1, "服务网点及仓储（3分）", "服务网点及仓储（3分）",
                        1, 1, false, "pdf:page=35;row=1;cell=1", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("rule", 1, 2, "在全国省份设有服务网点得分。", "在全国省份设有服务网点得分。",
                        1, 1, false, "pdf:page=35;row=1;cell=2", 35, "")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "服务网点及仓储", "parent", "商务", BigDecimal.valueOf(3),
                "在全国省份设有服务网点得分。", "OBJECTIVE", 35, "", List.of(),
                "在全国省份设有服务网点得分。", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.dimension()).isEqualTo("服务网点及仓储");
        assertThat(bound.sourceCellIds()).containsExactly("code", "rule");
    }

    @Test
    void bindsPdfQuoteAcrossColumnGapInSamePhysicalRow() {
        var table = new DocumentArtifact.CanonicalTable("pdf-p35-t1", 35, "第三章 评审标准", 2, 3,
                List.of(), List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("name", 1, 0, "服务网点", "服务网点",
                        1, 1, false, "pdf:page=35;row=1;cell=0", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("rule", 1, 2, "服务网点覆盖得3分", "服务网点覆盖得3分",
                        1, 1, false, "pdf:page=35;row=1;cell=2", 35, "")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "服务网点", "", "商务", BigDecimal.valueOf(3),
                "服务网点覆盖得3分", "OBJECTIVE", 35, "", List.of(),
                "服务网点 服务网点覆盖得3分", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.tableId()).isEqualTo("pdf-p35-t1");
        assertThat(bound.sourceCellIds()).containsExactly("name", "rule");
        assertThat(bound.dimension()).isEqualTo("服务网点");
    }

    @Test
    void bindsPdfQuoteCombiningNameAndRuleAcrossIntermediateScoreColumn() {
        var table = new DocumentArtifact.CanonicalTable("pdf-p35-t1", 35, "第三章 评审标准", 2, 4,
                List.of(), List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("id", 1, 0, "1", "1",
                        1, 1, false, "pdf:page=35;row=1;cell=0", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("name", 1, 1, "售后服务", "售后服务",
                        1, 1, false, "pdf:page=35;row=1;cell=1", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("score", 1, 2, "5分", "5分",
                        1, 1, false, "pdf:page=35;row=1;cell=2", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("rule", 1, 3, "提供7*24小时响应", "提供7*24小时响应",
                        1, 1, false, "pdf:page=35;row=1;cell=3", 35, "")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "1", "", "技术", BigDecimal.valueOf(5),
                "提供7*24小时响应", "OBJECTIVE", 35, "", List.of(),
                "售后服务 提供7*24小时响应", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.tableId()).isEqualTo("pdf-p35-t1");
        assertThat(bound.sourceCellIds()).containsExactly("name", "rule");
        assertThat(bound.dimension()).isEqualTo("售后服务");
    }

    @Test
    void bindsPdfQuoteWithIdAndNameAndRuleAndResolvesItemNameWhenCategoryDimensionGiven() {
        var table = new DocumentArtifact.CanonicalTable("pdf-p35-t1", 35, "第三章 评审标准", 2, 4,
                List.of(), List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("id", 1, 0, "1", "1",
                        1, 1, false, "pdf:page=35;row=1;cell=0", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("name", 1, 1, "（1）售后服务方案", "（1）售后服务方案",
                        1, 1, false, "pdf:page=35;row=1;cell=1", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("score", 1, 2, "5分", "5分",
                        1, 1, false, "pdf:page=35;row=1;cell=2", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("rule", 1, 3, "提供7*24小时响应", "提供7*24小时响应",
                        1, 1, false, "pdf:page=35;row=1;cell=3", 35, "")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "1", "", "技术部分", BigDecimal.valueOf(5),
                "提供7*24小时响应", "OBJECTIVE", 35, "", List.of(),
                "1 （1）售后服务方案 提供7*24小时响应", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.tableId()).isEqualTo("pdf-p35-t1");
        assertThat(bound.sourceCellIds()).contains("id", "name", "rule");
        assertThat(bound.dimension()).isEqualTo("售后服务方案");
    }

    @Test
    void fullyCitedCandidateSpanIsPreferredWhenDisambiguatingSpans() {
        var table1 = new DocumentArtifact.CanonicalTable("pdf-p35-t1", 35, "第三章 评审标准", 2, 3,
                List.of(), List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("n1", 1, 0, "资质要求", "资质要求",
                        1, 1, false, "pdf:page=35;row=1;cell=0", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("s1", 1, 1, "2分", "2分",
                        1, 1, false, "pdf:page=35;row=1;cell=1", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("r1", 1, 2, "具备相关认证", "具备相关认证",
                        1, 1, false, "pdf:page=35;row=1;cell=2", 35, "")), true);
        var table2 = new DocumentArtifact.CanonicalTable("pdf-p35-t2", 35, "第三章 评审标准", 2, 3,
                List.of(), List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("n2", 1, 0, "资质要求", "资质要求",
                        1, 1, false, "pdf:page=35;row=2;cell=0", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("s2", 1, 1, "2分", "2分",
                        1, 1, false, "pdf:page=35;row=2;cell=1", 35, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("r2", 1, 2, "具备相关认证", "具备相关认证",
                        1, 1, false, "pdf:page=35;row=2;cell=2", 35, "")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(table1, table2), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "1", "", "商务", BigDecimal.valueOf(2),
                "具备相关认证", "OBJECTIVE", 35, "", List.of("n2", "r2"),
                "资质要求 具备相关认证", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.tableId()).isEqualTo("pdf-p35-t2");
        assertThat(bound.sourceCellIds()).containsExactly("n2", "r2");
    }

    @Test
    void bindsVerbatimQuoteAcrossOrderedCellsInOneLogicalContinuationRow() throws Exception {
        String json = """
                {
                  "artifact":{"documentId":"doc","sha256":"sha","fileType":"DOCX","extractionVersion":"table-v2"},
                  "tables":[
                    {"tableId":"t-0008","sourceSection":"第三章 评审标准","rows":2,"columns":3,"mergedRegions":[],"isScoreTable":true,
                     "cells":[
                       {"cellId":"t-0008-r1-c0","row":1,"column":0,"rawText":"3","normalizedText":"3","rowspan":1,"colspan":1,"isMergedContinue":false,"sourceXmlPath":"/a","logicalRowId":"t-0008-r1"},
                       {"cellId":"t-0008-r1-c1","row":1,"column":1,"rawText":"实施方案","normalizedText":"实施方案","rowspan":1,"colspan":1,"isMergedContinue":false,"sourceXmlPath":"/b","logicalRowId":"t-0008-r1"},
                       {"cellId":"t-0008-r1-c2","row":1,"column":2,"rawText":"方案编制合理，","normalizedText":"方案编制合理，","rowspan":1,"colspan":1,"isMergedContinue":false,"sourceXmlPath":"/c","logicalRowId":"t-0008-r1"}]},
                    {"tableId":"t-0009","sourceSection":"第三章 评审标准","rows":1,"columns":3,"mergedRegions":[],"isScoreTable":false,
                     "cells":[
                       {"cellId":"t-0009-r0-c0","row":0,"column":0,"rawText":"","normalizedText":"","rowspan":1,"colspan":1,"isMergedContinue":false,"sourceXmlPath":"/d","logicalRowId":"t-0008-r1"},
                       {"cellId":"t-0009-r0-c1","row":0,"column":1,"rawText":"","normalizedText":"","rowspan":1,"colspan":1,"isMergedContinue":false,"sourceXmlPath":"/e","logicalRowId":"t-0008-r1"},
                       {"cellId":"t-0009-r0-c2","row":0,"column":2,"rawText":"组织保障措施完整。","normalizedText":"组织保障措施完整。","rowspan":1,"colspan":1,"isMergedContinue":false,"sourceXmlPath":"/f","logicalRowId":"t-0008-r1"}]}
                  ],"warnings":[]
                }
                """;
        var artifact = new ObjectMapper().readValue(json, DocumentArtifact.class);
        var fact = new MultimodalScoreFact("第三章 评审标准", "3", "", "实施方案", BigDecimal.TEN,
                "方案编制合理，组织保障措施完整。", "OBJECTIVE", null, "", List.of(),
                "方案编制合理，组织保障措施完整。", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.tableId()).isEqualTo("t-0008");
        assertThat(bound.sourceCellIds()).containsExactly("t-0008-r1-c2", "t-0009-r0-c2");
        assertThat(bound.quotedText()).isEqualTo("方案编制合理，组织保障措施完整。");
    }

    @Test
    void rebuildsNonVerbatimQuoteOnlyFromExactDimensionAndScoreInOneUniqueLogicalRow() {
        String row = "t-1-r1";
        var table = new DocumentArtifact.CanonicalTable("t-1", 3, "第三章 评审标准", 2, 4,
                List.of(), List.of(cell("h1", 0, 0, "序号", "t-1-r0"),
                cell("h2", 0, 1, "评分因素", "t-1-r0"), cell("h3", 0, 2, "评分规则", "t-1-r0"),
                cell("h4", 0, 3, "分值", "t-1-r0"), cell("id", 1, 0, "3", row),
                cell("dimension", 1, 1, "整体方案", row),
                cell("rule", 1, 2, "整体方案应完整、合理和可行。", row),
                cell("score", 1, 3, "10分", row)), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "DOCX", "table-v2"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "3", "", "整体方案", BigDecimal.TEN,
                "整体方案应完整、合理 and 可行。", "OBJECTIVE", null, "", List.of(),
                "整体方案应完整、合理 and 可行。", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.tableId()).isEqualTo("t-1");
        assertThat(bound.sourceCellIds()).containsExactly("rule");
        assertThat(bound.quotedText()).isEqualTo("整体方案应完整、合理和可行。");

        var unsupported = new MultimodalScoreFact("第三章 评审标准", "3", "", "整体方案补充", BigDecimal.TEN,
                "整体方案应完整 and 可行。", "OBJECTIVE", null, "", List.of(),
                "整体方案应完整 and 可行。", 0.9, ChildAggregation.SUM);
        var unbound = TextPrimaryEvidenceBinder.bind(List.of(unsupported), artifact, "").get(0);
        assertThat(unbound.tableId()).isBlank();
        assertThat(unbound.sourceCellIds()).isEmpty();
    }

    @Test
    void leavesQuoteUnboundWhenMultiplePhysicalRowsMatchExactly() {
        var first = new DocumentArtifact.CanonicalTable("t-1", 3, "本项目评分办法", 1, 1, List.of(),
                List.of(cell("first", 0, 0, "唯一引用得10分", "row-1")), true);
        var second = new DocumentArtifact.CanonicalTable("t-2", 3, "本项目评分办法", 1, 1, List.of(),
                List.of(cell("second", 0, 0, "唯一引用得10分", "row-2")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "DOCX", "table-v2"),
                List.of(first, second), List.of());
        var fact = new MultimodalScoreFact("本项目评分办法", "1", "", "唯一引用", BigDecimal.TEN,
                "唯一引用得10分", "OBJECTIVE", 3, "", List.of(), "唯一引用得10分", 0.9);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.tableId()).isBlank();
        assertThat(bound.sourceCellIds()).isEmpty();
    }

    @Test
    void doesNotDeserializeCrossTableIdentityFromLegacyV20AsProvenContinuation() throws Exception {
        String json = """
                {"schemaVersion":"2.0","artifact":{"documentId":"doc","sha256":"sha","fileType":"DOCX","extractionVersion":"table-v2"},
                 "tables":[{"tableId":"t-1","sourceSection":"本项目评分办法","rows":1,"columns":1,
                 "cells":[{"cellId":"c1","row":0,"column":0,"rawText":"规则得10分","normalizedText":"规则得10分",
                 "rowspan":1,"colspan":1,"isMergedContinue":false,"sourceXmlPath":"/c1"}],"isScoreTable":true}],"warnings":[]}
                """;
        var artifact = new ObjectMapper().readValue(json, DocumentArtifact.class);
        assertThat(artifact.tables().get(0).cells().get(0).logicalRowId()).isBlank();
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, int row, int column,
                                                                          String text, String logicalRowId) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column, text, text,
                1, 1, false, "/" + id, 3, logicalRowId);
    }

    @Test
    void bindsWhitespaceVariantQuoteToOriginalTextLocator() {
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩", BigDecimal.TEN,
                "满足条件得10分", "OBJECTIVE", null, "", List.of(),
                "满足条件得 10 分", 0.9, ChildAggregation.SUM);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), null,
                "业绩：满足条件得\n10\u00a0分。\n");

        assertThat(bound).hasSize(1);
        assertThat(bound.get(0).sourceCellIds()).containsExactly("text:line[0]");
        assertThat(bound.get(0).quotedText()).isEqualTo("满足条件得\n10\u00a0分");
        assertThat(bound.get(0).sourceMetadata().sourceSpans()).singleElement().satisfies(span -> {
            assertThat(span.startOffset()).isEqualTo(3);
            assertThat(span.rawText()).isEqualTo("满足条件得\n10\u00a0分");
            assertThat(span.fileType()).isEqualTo("TEXT");
        });
    }

    @Test
    void bindsPlainQuoteAgainstMarkItDownBoldWeightAndKeepsOriginalLine() {
        var fact = new MultimodalScoreFact("评审标准", "1", "", "响应报价", BigDecimal.valueOf(30),
                "报价评审", "OBJECTIVE", null, "", List.of(),
                "（一）商务部分（响应报价）30 分", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), null,
                "第三章 评审标准\n（一）商务部分（响应报价）**30** 分\n");

        assertThat(bound.get(0).sourceCellIds()).containsExactly("text:line[1]");
    }

    @Test
    void locatesTheWholeQuoteWhenItsFirstTokenAppearsEarlier() {
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩", BigDecimal.TEN,
                "满足条件得10分", "OBJECTIVE", null, "", List.of(),
                "满足条件得 10 分", 0.9, ChildAggregation.SUM);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), null,
                "满足条件得。\n评分要求：满足条件得\n10\u00a0分。\n");

        assertThat(bound.get(0).sourceCellIds()).containsExactly("text:line[1]");
    }

    @Test
    void rejectsWhitespaceOnlyQuote() {
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩", BigDecimal.TEN,
                "满足条件得10分", "OBJECTIVE", null, "", List.of(), "\u00a0", 0.9, ChildAggregation.SUM);

        assertThat(TextPrimaryEvidenceBinder.bind(List.of(fact), null, "评分要求：10分").get(0).sourceCellIds())
                .isEmpty();
    }

    @Test
    void tableBindingAlsoAllowsWhitespaceVariantQuote() {
        var cell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "t-r1-c1", 1, 1, "满足条件得\n10\u00a0分", "满足条件得 10 分", 1, 1,
                false, "word/document.xml", 3);
        var table = new DocumentArtifact.CanonicalTable("t-1", 3, "评分办法", 2, 2,
                List.of(), List.of(cell), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "docx", "2.0"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩", BigDecimal.TEN,
                "满足条件得10分", "OBJECTIVE", 3, "", List.of(), "满足条件得 10 分", 0.9);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "");

        assertThat(bound.get(0).tableId()).isEqualTo("t-1");
        assertThat(bound.get(0).sourceCellIds()).containsExactly("t-r1-c1");
    }

    @Test
    void bindsUniqueVerbatimQuoteToPhysicalPageWhenFactUsesPrintedPageNumber() {
        var cell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "pdf-p34-r2-c1", 2, 1, "独特来源证据完整保留。", "独特来源证据完整保留。", 1, 1,
                false, "pdf:page=34;line=12", 34, "");
        var table = new DocumentArtifact.CanonicalTable("pdf-p34-t1", 34, "第三章 评审标准", 3, 2,
                List.of(), List.of(cell), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "1", "", "技术方案", BigDecimal.TEN,
                "独特来源证据完整保留。", "SUBJECTIVE", 29, "", List.of(),
                "独特来源证据完整保留。", 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.pageNumber()).isEqualTo(34);
        assertThat(bound.tableId()).isEqualTo("pdf-p34-t1");
        assertThat(bound.sourceCellIds()).containsExactly("pdf-p34-r2-c1");
    }

    @Test
    void doesNotUseUnmappedPageHintToChooseBetweenDuplicateVerbatimQuotes() {
        var page29 = new DocumentArtifact.CanonicalTable("pdf-p29-t1", 29, "第三章 评审标准", 1, 1,
                List.of(), List.of(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "pdf-p29-r1-c1", 1, 1, "重复的原文句子。", "重复的原文句子。", 1, 1,
                false, "pdf:page=29;line=4", 29, "")), true);
        var page34 = new DocumentArtifact.CanonicalTable("pdf-p34-t1", 34, "第三章 评审标准", 1, 1,
                List.of(), List.of(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "pdf-p34-r1-c1", 1, 1, "重复的原文句子。", "重复的原文句子。", 1, 1,
                false, "pdf:page=34;line=4", 34, "")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(page29, page34), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "1", "", "技术方案", BigDecimal.TEN,
                "重复的原文句子。", "SUBJECTIVE", 29, "", List.of(),
                "重复的原文句子。", 0.9, ChildAggregation.SUM);

        var unbound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(unbound.tableId()).isBlank();
        assertThat(unbound.sourceCellIds()).isEmpty();
    }

    @Test
    void doesNotTreatPunctuationDifferenceAsVerbatimEvidence() {
        var cell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "pdf-p34-r1-c1", 1, 1, "独特评分规则，满足条件得10分。", "独特评分规则，满足条件得10分。",
                1, 1, false, "pdf:page=34;line=4", 34, "");
        var table = new DocumentArtifact.CanonicalTable("pdf-p34-t1", 34, "第三章 评审标准", 1, 1,
                List.of(), List.of(cell), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "table-v2"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "1", "", "技术方案", BigDecimal.TEN,
                "独特评分规则 满足条件得10分", "SUBJECTIVE", 29, "", List.of(),
                "独特评分规则 满足条件得10分", 0.9, ChildAggregation.SUM);

        var unbound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(unbound.tableId()).isBlank();
        assertThat(unbound.sourceCellIds()).isEmpty();
    }

    @Test
    void bindsUniqueExactOcrTextToPhysicalPageAndBoundingBoxOnlyInsideItsScheme() {
        String section = "第三章 评审标准";
        String schemeId = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.schemeId(section);
        var block = new DocumentArtifact.TextBlockEvidence("p34-b7", 34, section,
                "原文规则完整保留并逐字匹配。", 94.0,
                new DocumentArtifact.BoundingBox(120, 300, 420, 65, 1190, 1684));
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "pdf-ocr"),
                List.of(), List.of(), List.of(block), "2.2");
        var fact = new MultimodalScoreFact(section, "1", "", "技术方案", BigDecimal.TEN,
                "原文规则完整保留并逐字匹配。", "SUBJECTIVE", 29, "", List.of(),
                "原文规则完整保留并逐字匹配。", 0.9, ChildAggregation.SUM, schemeId, "IN_SCHEME");

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.pageNumber()).isEqualTo(34);
        assertThat(bound.tableId()).isBlank();
        assertThat(bound.sourceCellIds()).singleElement()
                .asString().contains("pdf:page=34", "bbox=120,300,420,65");
    }

    @Test
    void bindsContinuousChineseQuoteToTesseractTextWithTokenSpaces() {
        String section = "第三章 评审标准";
        String quote = "技术评分标准";
        var block = new DocumentArtifact.TextBlockEvidence("p34-b8", 34, section,
                "技术 评分 标准", 94.0,
                new DocumentArtifact.BoundingBox(120, 300, 420, 65, 1190, 1684));
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "pdf-ocr"),
                List.of(), List.of(), List.of(block), "2.2");
        var schemeId = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.schemeId(section);
        var fact = new MultimodalScoreFact(section, "1", "", "技术", BigDecimal.TEN,
                quote, "SUBJECTIVE", 29, "", List.of(), quote, 0.9, ChildAggregation.SUM,
                schemeId, "IN_SCHEME");

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(bound.pageNumber()).isEqualTo(34);
        assertThat(bound.sourceCellIds()).singleElement()
                .asString().contains("pdf:page=34", "bbox=120,300,420,65");
    }

    @Test
    void doesNotEraseMeaningfulLatinWordSpacesWhenMatchingOcrText() {
        String section = "第三章 评审标准";
        var block = new DocumentArtifact.TextBlockEvidence("p34-b9", 34, section,
                "No bid", 94.0,
                new DocumentArtifact.BoundingBox(120, 300, 420, 65, 1190, 1684));
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "pdf-ocr"),
                List.of(), List.of(), List.of(block), "2.2");
        var fact = new MultimodalScoreFact(section, "1", "", "技术", BigDecimal.TEN,
                "Nobid", "SUBJECTIVE", 29, "", List.of(), "Nobid", 0.9, ChildAggregation.SUM,
                com.xiyu.bid.scoreparse.domain.OriginalScoringContext.schemeId(section), "IN_SCHEME");

        var unbound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(unbound.pageNumber()).isEqualTo(29);
        assertThat(unbound.sourceCellIds()).isEmpty();
    }

    @Test
    void refusesLowConfidenceOrOutOfSchemeOcrText() {
        var box = new DocumentArtifact.BoundingBox(10, 20, 100, 30, 1190, 1684);
        var lowConfidence = new DocumentArtifact.TextBlockEvidence(
                "p34-b1", 34, "第三章 评审标准", "只差一个字的OCR内容", 42.0, box);
        var otherScheme = new DocumentArtifact.TextBlockEvidence(
                "p35-b1", 35, "第四章 合同履约考核", "完全相同的来源句子。", 97.0, box);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "pdf-ocr"),
                List.of(), List.of(), List.of(lowConfidence, otherScheme), "2.2");
        var fact = new MultimodalScoreFact("第三章 评审标准", "1", "", "技术方案", BigDecimal.TEN,
                "完全相同的来源句子。", "SUBJECTIVE", 29, "", List.of(),
                "完全相同的来源句子。", 0.9, ChildAggregation.SUM,
                com.xiyu.bid.scoreparse.domain.OriginalScoringContext.schemeId("第三章 评审标准"), "IN_SCHEME");

        var unbound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(unbound.pageNumber()).isEqualTo(29);
        assertThat(unbound.sourceCellIds()).isEmpty();
    }

    @Test
    void refusesDuplicateExactOcrQuotesEvenWhenPageHintMatchesOne() {
        String section = "第三章 评审标准";
        String quote = "多页重复的OCR原文句子。";
        var box = new DocumentArtifact.BoundingBox(10, 20, 100, 30, 1190, 1684);
        var first = new DocumentArtifact.TextBlockEvidence("p29-b1", 29, section, quote, 94.0, box);
        var second = new DocumentArtifact.TextBlockEvidence("p34-b1", 34, section, quote, 95.0, box);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "pdf-ocr"),
                List.of(), List.of(), List.of(first, second), "2.2");
        var schemeId = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.schemeId(section);
        var fact = new MultimodalScoreFact(section, "1", "", "技术方案", BigDecimal.TEN,
                quote, "SUBJECTIVE", 29, "", List.of(), quote, 0.9, ChildAggregation.SUM,
                schemeId, "IN_SCHEME");

        var unbound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);

        assertThat(unbound.pageNumber()).isEqualTo(29);
        assertThat(unbound.sourceCellIds()).isEmpty();
    }

    @Test
    void bindsMultiRowQuoteByUniqueSignificantPrefix() {
        var cell1 = new DocumentArtifact.CanonicalTable.CanonicalCellDto("r16-c3", 16, 3,
                "第一部分：一级集采商品（合计28分）\n1、一级集采商品上架折扣率（28分）：供应商在响应文件格式响应函附件1：报价表-1.1一级集采商品上架折扣率报价表的“三级分类品类上架折扣率”填报折扣率...",
                "", 1, 1, false, "pdf:page=38", 38, "");
        var cell2 = new DocumentArtifact.CanonicalTable.CanonicalCellDto("r17-c3", 17, 3,
                "2、一级集采商品清单报价：供应商在响应文件格式响应函附件1：报价表-1.2一级集采商品清单报价中按要求进行报价...",
                "", 1, 1, false, "pdf:page=39", 39, "");
        var table = new DocumentArtifact.CanonicalTable("p34-t0010", 34, "第三章 评审办法", 20, 4,
                List.of(), List.of(cell1, cell2), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "v2"),
                List.of(table), List.of());

        // Quote is longer than cell1 because it continues into cell2
        String longQuote = "第一部分：一级集采商品（合计28分）\n1、一级集采商品上架折扣率（28分）：供应商在响应文件格式响应函附件1：报价表-1.1一级集采商品上架折扣率报价表的“三级分类品类上架折扣率”填报折扣率... 2、一级集采商品清单报价：供应商在响应文件格式响应函附件1：报价表-1.2一级集采商品清单报价中按要求进行报价...";
        var fact = new MultimodalScoreFact("第三章 评审办法", "3.2.3(3)_1", "", "报价", BigDecimal.valueOf(28),
                "一级集采", "OBJECTIVE", 38, "", List.of(), longQuote, 0.9, ChildAggregation.SUM);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "").get(0);
        assertThat(bound.tableId()).isEqualTo("p34-t0010");
        assertThat(bound.sourceCellIds()).containsExactly("r16-c3");
        assertThat(bound.pageNumber()).isEqualTo(38);
    }
}
