package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalRuleRestorationTest {

    @Test
    @DisplayName("非表格正文段落事实（text:line）带有 sourceSpans 时，应穿透还原规则文本")
    void shouldRestoreRuleTextFromTextParagraphSourceSpans() {
        String locator = "text:line[693]";
        String rawText = "最终评审折扣率等于评标基准价，得 30 分；每高于评标基准价 1%扣 0.25 分，每低于评标基准价 1%扣 0.5 分，最低得 20 分。";
        String quote = "最终评审折扣率等于评标基准价，得 30 分；";

        var span = new SourceSpan("doc-1", "hash-1", "TEXT", "scheme-1", 21,
                "", locator, 100, 100 + rawText.length(), rawText, locator);
        var claim = new ModelSourceClaim("第三章 评审标准", 21, "", List.of(locator), "scheme-1", "IN_SCHEME");
        var metadata = new ScoreSourceMetadata(rawText, quote, List.of(span), List.of(), "价格", "OBJECTIVE", claim);

        var fact = new MultimodalScoreFact("第三章 评审标准", "shangwu_baojia_defen", null, "价格",
                new BigDecimal("30.00"), rawText, "OBJECTIVE", 21, "", List.of(locator), quote,
                1.0, ChildAggregation.UNKNOWN, "scheme-1", "IN_SCHEME", metadata);

        var artifact = new DocumentArtifact(new ArtifactMeta("doc.pdf", "hash", "PDF", "2.2"), List.of(), List.of());

        var result = OriginalRuleRestoration.restore(fact, artifact, List.of(fact));

        assertThat(result.text()).isNotBlank();
        assertThat(result.text()).contains("最终评审折扣率等于评标基准价，得 30 分");
        assertThat(result.spans()).isNotEmpty();
        assertThat(result.spans().get(0).locator()).isEqualTo(locator);
    }

    @Test
    @DisplayName("同行多子项携带恢复后的评审因素名称列单元格时，应仅使用评审标准列恢复各自完整原文")
    void shouldExcludeNameColumnAndRestoreAllInlineSiblingsInSharedLogicalRow() {
        String tableId = "doc0-baidu_tbl_13_1";
        String firstRule = "1.商品品种：低于20万种得0分，20万种(含)\n得5分，每增加10万种加1分，满分10分。提\n供网站查询截图或其他证明材料证明。";
        String secondRule = "2。投标人\n能够提供项目解决方案(2分)、能够配合开展物\n料信息及品类整合归纳(3分)。上述两项内容需\n提供对应承诺书，承诺书格式自拟，盖章有效。";
        String sharedRuleCell = firstRule + secondRule;

        var cells = List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "_r0_c0", 0, 0, "条款号", "条款号", 1, 1, false, "", 13, tableId + "-r0"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "_r0_c1", 0, 1, "评审因素", "评审因素", 1, 1, false, "", 13, tableId + "-r0"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "_r0_c2", 0, 2, "评审标准", "评审标准", 1, 1, false, "", 13, tableId + "-r0"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "_r0_c3", 0, 3, "分值", "分值", 1, 1, false, "", 13, tableId + "-r0"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "_r2_c0", 1, 0, "2", "2", 1, 1, false, "", 13, tableId + "-r2"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "_r2_c1", 1, 1, "商品类目和商品\n品种及保障能力\n和方案(15分)", "商品类目和商品品种及保障能力和方案(15分)", 1, 1, false, "", 13, tableId + "-r2"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "_r2_c2", 1, 2, sharedRuleCell, sharedRuleCell, 1, 1, false, "", 13, tableId + "-r2"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "_r2_c3", 1, 3, "10\n5", "10 5", 1, 1, false, "", 13, tableId + "-r2")
        );
        var table = new DocumentArtifact.CanonicalTable(tableId, 13, "第四章 评审办法", 2, 4, List.of(), cells, true, "PDF");
        var artifact = new DocumentArtifact(new ArtifactMeta("doc0", "hash", "PDF", "2.2"), List.of(table), List.of());

        List<String> citedWithName = List.of(tableId + "_r2_c2", tableId + "_r2_c1");
        var f1 = new MultimodalScoreFact("第四章 评审办法", "2.1", "2", "商品类目和商品品种及保障能力和方案",
                new BigDecimal("10"), firstRule, "SUBJECTIVE", 13, tableId, citedWithName, firstRule,
                0.95, ChildAggregation.UNKNOWN, "scheme-1", "IN_SCHEME");
        var f2 = new MultimodalScoreFact("第四章 评审办法", "2.2", "2", "商品类目和商品品种及保障能力和方案",
                new BigDecimal("5"), secondRule, "OBJECTIVE", 13, tableId, citedWithName, secondRule,
                0.95, ChildAggregation.UNKNOWN, "scheme-1", "IN_SCHEME");

        var r1 = OriginalRuleRestoration.restore(f1, artifact, List.of(f1, f2));
        var r2 = OriginalRuleRestoration.restore(f2, artifact, List.of(f1, f2));

        assertThat(r1.text()).isEqualTo(firstRule);
        assertThat(r2.text()).isEqualTo(secondRule);
    }
}
