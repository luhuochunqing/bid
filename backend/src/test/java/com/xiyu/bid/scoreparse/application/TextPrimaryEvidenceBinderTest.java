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
}
