package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogicalRuleSourceUnitTest {

    @Test
    @DisplayName("4列表格中评审标准列跨行折行（序号和分值列为空），应识别为续行并合并进同一逻辑行")
    void shouldRecognizeMiddleColumnContinuationRowInTable() {
        // Table with 4 columns: 序号, 评审内容, 评审标准, 评分\n分值
        var r0c0 = new CanonicalCellDto("t1-r0-c0", 0, 0, "序号", "序号", 1, 1, false, "pdf:p24", 24, "t1-r0");
        var r0c1 = new CanonicalCellDto("t1-r0-c1", 0, 1, "评审内容", "评审内容", 1, 1, false, "pdf:p24", 24, "t1-r0");
        var r0c2 = new CanonicalCellDto("t1-r0-c2", 0, 2, "评审标准", "评审标准", 1, 1, false, "pdf:p24", 24, "t1-r0");
        var r0c3 = new CanonicalCellDto("t1-r0-c3", 0, 3, "评分\n分值", "评分分值", 1, 1, false, "pdf:p24", 24, "t1-r0");

        // Row 1: Item 1 (业绩)
        var r1c0 = new CanonicalCellDto("t1-r1-c0", 1, 0, "1", "1", 1, 1, false, "pdf:p24", 24, "t1-r1");
        var r1c1 = new CanonicalCellDto("t1-r1-c1", 1, 1, "业绩", "业绩", 1, 1, false, "pdf:p24", 24, "t1-r1");
        var r1c2 = new CanonicalCellDto("t1-r1-c2", 1, 2, "供应商近3年具备央企电子商城运营业绩得5分。", "供应商近3年具备央企电子商城运营业绩得5分。", 1, 1, false, "pdf:p24", 24, "t1-r1");
        var r1c3 = new CanonicalCellDto("t1-r1-c3", 1, 3, "5", "5", 1, 1, false, "pdf:p24", 24, "t1-r1");

        // Row 2: Continuation row (col 0, 1, 3 are blank; col 2 has remaining rule text)
        var r2c0 = new CanonicalCellDto("t1-r2-c0", 2, 0, "", "", 1, 1, false, "pdf:p24", 24, "t1-r2");
        var r2c1 = new CanonicalCellDto("t1-r2-c1", 2, 1, "", "", 1, 1, false, "pdf:p24", 24, "t1-r2");
        var r2c2 = new CanonicalCellDto("t1-r2-c2", 2, 2, "其中有效业绩30个及以上得5分，25-29个得4分，14个以下不得分。", "其中有效业绩30个及以上得5分，25-29个得4分，14个以下不得分。", 1, 1, false, "pdf:p24", 24, "t1-r2");
        var r2c3 = new CanonicalCellDto("t1-r2-c3", 2, 3, "", "", 1, 1, false, "pdf:p24", 24, "t1-r2");

        // Row 3: Item 2 (仓储能力)
        var r3c0 = new CanonicalCellDto("t1-r3-c0", 3, 0, "2", "2", 1, 1, false, "pdf:p24", 24, "t1-r3");
        var r3c1 = new CanonicalCellDto("t1-r3-c1", 3, 1, "仓储能力", "仓储能力", 1, 1, false, "pdf:p24", 24, "t1-r3");
        var r3c2 = new CanonicalCellDto("t1-r3-c2", 3, 2, "仓库资源匹配得10分。", "仓库资源匹配得10分。", 1, 1, false, "pdf:p24", 24, "t1-r3");
        var r3c3 = new CanonicalCellDto("t1-r3-c3", 3, 3, "10", "10", 1, 1, false, "pdf:p24", 24, "t1-r3");

        var table = new CanonicalTable("t1", 24, "第三章 评审标准", 4, 4, List.of(),
                List.of(r0c0, r0c1, r0c2, r0c3, r1c0, r1c1, r1c2, r1c3, r2c0, r2c1, r2c2, r2c3, r3c0, r3c1, r3c2, r3c3),
                true, "PDF");
        var artifact = new DocumentArtifact(new ArtifactMeta("doc.pdf", "hash", "PDF", "2.2"), List.of(table), List.of());

        var fact = new MultimodalScoreFact("第三章 评审标准", "shangwu_shili_yeji", null, "商务",
                new BigDecimal("5.00"), "供应商近3年具备央企电子商城运营业绩得5分。", "OBJECTIVE", 24, "t1",
                List.of("t1-r1-c2"), "供应商近3年具备央企电子商城运营业绩得5分。", 1.0, ChildAggregation.UNKNOWN,
                "scheme-1", "IN_SCHEME");

        var resolution = LogicalRuleSourceUnit.resolve(artifact, fact);

        assertThat(resolution.applicable()).isTrue();
        assertThat(resolution.closed()).isTrue();
        // Spans should contain both r1-c2 and the continuation r2-c2
        List<String> spanIds = resolution.spans().stream().map(SourceSpan::sourceId).toList();
        assertThat(spanIds).contains("t1-r1-c2", "t1-r2-c2");
    }
}
