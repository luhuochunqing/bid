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
        assertThat(bound.get(0).quotedText()).isEqualTo("满足条件得 10 分");
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
}
