// 影子组装管道端到端测试：Sidecar Artifact → CanonicalTableGrid → ScoreFact。
// 审核修复（P1-4）：Phase 1/2 的领域类不再是孤岛——本测试证明三者可以
// 组装成只读管道并产出可校验的事实池。
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreFactShadowPipelineTest {

    @Test
    void merged_origin_fixture_preserves_parent_total_and_all_leaf_weights() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/scoreparse-golden/merged-parent-first-leaf-table-v2.json")) {
            assertNotNull(input, "sanitized DocumentArtifact fixture must be available");
            DocumentArtifact fixture = new ObjectMapper().readValue(input, DocumentArtifact.class);

            ScoreFactShadowPipeline.ShadowResult result = ScoreFactShadowPipeline.run(fixture);

            assertEquals(1, result.tableCompletenesses().size());
            var completeness = result.tableCompletenesses().get(0).completeness();
            assertEquals(ScoreParseDecision.AUTO_APPROVED, completeness.decision());
            assertEquals(0, completeness.declaredTotalScore().compareTo(new BigDecimal("100")));
            assertEquals(0, completeness.leafWeightSum().compareTo(new BigDecimal("100")));
            assertEquals(3, completeness.leafCount());
            assertEquals(1, completeness.parentCount());
            assertEquals(4, result.tables().get(0).facts().size());
            assertTrue(result.scoring().factsValid());
            assertTrue(result.scoring().scoringComplete());
        }
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(
            String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                id, row, column, text, text, 1, 1, false,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (column + 1) + "]");
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cellWithMerge(
            String id, int row, int column, String text,
            int rowspan, int colspan, boolean isMergedContinue) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                id, row, column, text, text, rowspan, colspan, isMergedContinue,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (column + 1) + "]");
    }

    private static DocumentArtifact artifact(List<DocumentArtifact.CanonicalTable> tables) {
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:test", "abc", "DOCX", "table-v2"),
                tables,
                List.of());
    }

    /** 评分主表：大类"商务部分"纵向合并 2 行，子项显式分值 10+10。 */
    private static DocumentArtifact.CanonicalTable scoreTable() {
        return new DocumentArtifact.CanonicalTable(
                "t-0000", null, "评分标准", 3, 4,
                List.of(new DocumentArtifact.CanonicalTable.MergedRegionDto("t-0000-r1-c1", 2, 1)),
                List.of(
                        cell("t-0000-r0-c0", 0, 0, "编号"),
                        cell("t-0000-r0-c1", 0, 1, "评分因素"),
                        cell("t-0000-r0-c2", 0, 2, "评分标准"),
                        cell("t-0000-r0-c3", 0, 3, "分值"),
                        cellWithMerge("t-0000-r1-c0", 1, 0, "A1", 1, 1, false),
                        cellWithMerge("t-0000-r1-c1", 1, 1, "商务部分", 2, 1, false),
                        cell("t-0000-r1-c2", 1, 2, "售后服务方案齐全"),
                        cell("t-0000-r1-c3", 1, 3, "10"),
                        cellWithMerge("t-0000-r2-c0", 2, 0, "A2", 1, 1, false),
                        cellWithMerge("t-0000-r2-c1", 2, 1, "", 1, 1, true),
                        cell("t-0000-r2-c2", 2, 2, "仓储管理方案齐全"),
                        cell("t-0000-r2-c3", 2, 3, "10")),
                true);
    }

    @Test
    void end_to_end_artifact_to_grid_to_facts() {
        ScoreFactShadowPipeline.ShadowResult result =
                ScoreFactShadowPipeline.run(artifact(List.of(scoreTable())));

        assertEquals(1, result.tables().size());
        assertTrue(result.tables().get(0).structurallyValid());
        assertEquals(2, result.tables().get(0).facts().size(), "2 个并列叶子，无汇总父项");

        List<ScoreFact> facts = result.tables().get(0).facts();
        ScoreFact first = facts.get(0);
        ScoreFact second = facts.get(1);
        assertEquals("", first.parentFactId(), "无汇总分值的合并起点是计分明细，不充当父项");
        assertEquals("", second.parentFactId(), "续行不把前序叶子当父项，版式继承与业务父子分离");
        assertTrue(ScoreFact.isLeaf(first, facts), "起点 = 未被引用");
        assertTrue(ScoreFact.isLeaf(second, facts), "续行 = 未被引用");
        assertEquals(0, second.weight().compareTo(new BigDecimal("10")));

        // 列映射由表头文本推断：评分因素=1、评分标准=2、分值=3。
        assertEquals(1, result.tables().get(0).mapping().dimCol());
        assertEquals(3, result.tables().get(0).mapping().weightCol());

        // 两叶子均可自动计分 → 全池校验通过且无父项违规。
        assertTrue(result.scoring().factsValid());
        assertTrue(result.scoring().violations().stream().noneMatch(v -> v.contains("parent fact")));
    }

    @Test
    void non_score_tables_are_skipped() {
        DocumentArtifact.CanonicalTable noiseTable = new DocumentArtifact.CanonicalTable(
                "t-0001", null, "商务条款一览", 1, 1, List.of(),
                List.of(cell("t-0001-r0-c0", 0, 0, "交付期：30 天")),
                false);

        ScoreFactShadowPipeline.ShadowResult result =
                ScoreFactShadowPipeline.run(artifact(List.of(scoreTable(), noiseTable)));

        assertEquals(1, result.tables().size(), "非评分表不进管道");
        assertEquals(1, result.skippedTables());
        assertEquals(2, result.tables().get(0).facts().size(), "评分表事实不受非评分表影响");
    }

    @Test
    void structurally_incomplete_table_yields_no_facts_and_is_reported() {
        // 数据行缺 (2,3) 分值 cell → 网格覆盖校验失败 → 事实为空。
        DocumentArtifact.CanonicalTable broken = new DocumentArtifact.CanonicalTable(
                "t-0000", null, "评分标准", 2, 4, List.of(),
                List.of(
                        cell("t-0000-r0-c0", 0, 0, "编号"),
                        cell("t-0000-r0-c1", 0, 1, "评分因素"),
                        cell("t-0000-r0-c2", 0, 2, "评分标准"),
                        cell("t-0000-r0-c3", 0, 3, "分值"),
                        cell("t-0000-r1-c0", 1, 0, "A1"),
                        cell("t-0000-r1-c1", 1, 1, "技术方案"),
                        cell("t-0000-r1-c2", 1, 2, "覆盖全部需求")),
                true);

        ScoreFactShadowPipeline.ShadowResult result =
                ScoreFactShadowPipeline.run(artifact(List.of(broken)));

        assertEquals(1, result.tables().size());
        assertFalse(result.tables().get(0).structurallyValid(),
                "结构缺口必须原样暴露，不得回退旧字符串表格");
        assertTrue(result.tables().get(0).facts().isEmpty());
        assertFalse(result.scoring().scoringComplete());
    }

    @Test
    void completeness_uses_declared_total_from_tender_text() {
        DocumentArtifact.CanonicalTable eightyPointTable = new DocumentArtifact.CanonicalTable(
                "t-0000", null, "评分标准", 3, 4, List.of(),
                List.of(
                        cell("t-0000-r0-c0", 0, 0, "编号"),
                        cell("t-0000-r0-c1", 0, 1, "评分因素"),
                        cell("t-0000-r0-c2", 0, 2, "评分标准"),
                        cell("t-0000-r0-c3", 0, 3, "分值"),
                        cell("t-0000-r1-c0", 1, 0, "A1"),
                        cell("t-0000-r1-c1", 1, 1, "商务部分"),
                        cell("t-0000-r1-c2", 1, 2, "商务能力完整"),
                        cell("t-0000-r1-c3", 1, 3, "60"),
                        cell("t-0000-r2-c0", 2, 0, "A2"),
                        cell("t-0000-r2-c1", 2, 1, "技术部分"),
                        cell("t-0000-r2-c2", 2, 2, "技术方案完整"),
                        cell("t-0000-r2-c3", 2, 3, "20")),
                true);

        ScoreFactShadowPipeline.ShadowResult result = ScoreFactShadowPipeline.run(
                artifact(List.of(eightyPointTable)), "本项目评分总分：80分。");

        assertEquals(ScoreParseDecision.AUTO_APPROVED, result.completeness().decision());
        assertEquals(new BigDecimal("80.00"), result.completeness().declaredTotalScore());
        assertEquals(new BigDecimal("80.00"), result.completeness().leafWeightSum());
    }

    @Test
    void null_artifact_is_reported_not_thrown() {
        ScoreFactShadowPipeline.ShadowResult result = ScoreFactShadowPipeline.run(null);
        assertTrue(result.tables().isEmpty());
        assertFalse(result.scoring().factsValid());
        assertTrue(result.scoring().violations().stream().anyMatch(v -> v.contains("artifact is null")));
    }
}
