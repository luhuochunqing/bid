// 金标基线测试（spec 059 Phase 0/2 验收闭环）。
// 每个用例钉死：末级事实数、权重、父子关系、source cell、预期决定状态。
// 口径与 docs/lessons/scoreparse-golden-corpus/index.md 一致；修改抽取语义
// 必须同步回填登记表并更新本类。夹具全部为脱敏合成重建，禁止贴真实文件内容。
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenCorpusBaselineTest {

    private static CanonicalCellDto cell(String id, int row, int column, String text) {
        return new CanonicalCellDto(
                id, row, column, text, text, 1, 1, false,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (column + 1) + "]");
    }

    private static CanonicalCellDto cellWithMerge(
            String id, int row, int column, String text,
            int rowspan, int colspan, boolean isMergedContinue) {
        return new CanonicalCellDto(
                id, row, column, text, text, rowspan, colspan, isMergedContinue,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (column + 1) + "]");
    }

    private static CanonicalTableGrid grid(String tableId, List<CanonicalCellDto> cells,
                                           List<MergedRegionDto> regions, int rows, int columns) {
        return CanonicalTableGridFactory.fromSidecarTable(new CanonicalTable(
                tableId, null, "评分标准", rows, columns, regions, cells, true));
    }

    /** 列语义：编号=0，评分因素=1，评分标准=2，分值=3，表头 1 行。 */
    private static TableColumnMapping mapping() {
        return new TableColumnMapping(0, -1, 1, 2, 3, 1, false);
    }

    private static BigDecimal autoLeafWeightSum(List<ScoreFact> facts) {
        return facts.stream()
                .filter(ScoreFact::isAutoApprovable)
                .filter(f -> ScoreFact.isLeaf(f, facts))
                .map(ScoreFact::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    // 事故 1347：商务部分父项与子项重复计分（父项 20 + 子项 10+10 落库）
    // ------------------------------------------------------------------

    @Test
    void incident_1347_parent_and_children_never_scored_together() {
        CanonicalTableGrid g = grid("t-0001",
                List.of(
                        cell("t-0001-r0-c0", 0, 0, "编号"),
                        cell("t-0001-r0-c1", 0, 1, "评分因素"),
                        cell("t-0001-r0-c2", 0, 2, "评分标准"),
                        cell("t-0001-r0-c3", 0, 3, "分值"),
                        cellWithMerge("t-0001-r1-c0", 1, 0, "A1", 1, 1, false),
                        cellWithMerge("t-0001-r1-c1", 1, 1, "商务部分（20分）", 2, 1, false),
                        cell("t-0001-r1-c2", 1, 2, "售后服务方案齐全"),
                        cell("t-0001-r1-c3", 1, 3, "10"),
                        cellWithMerge("t-0001-r2-c0", 2, 0, "A2", 1, 1, false),
                        cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                        cell("t-0001-r2-c2", 2, 2, "仓储管理方案齐全"),
                        cell("t-0001-r2-c3", 2, 3, "10")),
                List.of(new MergedRegionDto("t-0001-r1-c1", 2, 1)),
                3, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(g, mapping());

        assertEquals(2, facts.size(), "预期末级事实数=2（1 父 + 1 子）");
        ScoreFact parent = facts.get(0);
        ScoreFact child = facts.get(1);
        assertEquals(new BigDecimal("10"), parent.weight());
        assertEquals(new BigDecimal("10"), child.weight());
        assertEquals(parent.factId(), child.parentFactId(), "子项必须指向父项");
        assertFalse(ScoreFact.isLeaf(parent, facts), "被引用的父项不是叶子");
        assertTrue(ScoreFact.isLeaf(child, facts), "未被引用的子项是叶子");
        assertTrue(child.sourceCellIds().stream().noneMatch(id -> id.startsWith("t-0001-r1-")),
                "子项 source cell 不得引用父项行");

        ScoreFactValidator.ValidationResult scoring = ScoreFactValidator.validateScoringSet(facts);
        assertFalse(scoring.factsValid(), "父项被标 AUTO_APPROVED 必须被拦截");
        assertFalse(scoring.scoringComplete());
        assertTrue(scoring.violations().stream()
                .anyMatch(v -> v.contains(parent.factId()) && v.contains("parent fact")));
        assertEquals(0, autoLeafWeightSum(facts).compareTo(new BigDecimal("10")),
                "自动评分只允许末级合计 10 分，不得出现 20/30/40");
    }

    // ------------------------------------------------------------------
    // 事故 1358：LLM 复述导致 detail 丢失原文引用 —— 确定性链路必须
    // 每条事实都携带可定位 source cell 与 weight evidence
    // ------------------------------------------------------------------

    @Test
    void incident_1358_every_fact_keeps_locatable_evidence() {
        CanonicalTableGrid g = grid("t-0001",
                List.of(
                        cell("t-0001-r0-c0", 0, 0, "编号"),
                        cell("t-0001-r0-c1", 0, 1, "评分因素"),
                        cell("t-0001-r0-c2", 0, 2, "评分标准"),
                        cell("t-0001-r0-c3", 0, 3, "分值"),
                        cell("t-0001-r1-c0", 1, 0, "A1"),
                        cell("t-0001-r1-c1", 1, 1, "营业收现比率（6分）"),
                        cell("t-0001-r1-c2", 1, 2, "提供近三年流水及收现率测算说明，未提供不得分"),
                        cell("t-0001-r1-c3", 1, 3, "6分")),
                List.of(), 2, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(g, mapping());
        assertEquals(1, facts.size());

        ScoreFact fact = facts.get(0);
        assertFalse(fact.sourceCellIds().isEmpty(), "事实必须携带 source cell");
        assertTrue(fact.sourceCellIds().contains("t-0001-r1-c2"), "detail 必须锚定到原文 cell");
        assertTrue(fact.weightEvidence().isLocatable(), "权重证据必须可定位");
        assertEquals("t-0001-r1-c3", fact.weightEvidence().cellId());
        assertNotEquals("", fact.sourceText(), "原文引用不得为空");

        ScoreFactValidator.ValidationResult result = ScoreFactValidator.validateScoringSet(facts);
        assertTrue(result.factsValid());
        assertTrue(result.scoringComplete(), "单条叶子 + 可定位证据 → 可自动评分");
        assertEquals(ScoreFact.ValidationStatus.AUTO_APPROVED, fact.validationStatus());
    }

    // ------------------------------------------------------------------
    // 事故 1362：大类 20 分 + 三个子项 5/5/10 被当成四个平级子项建项，
    // 总分爆成 45/35 —— 只允许末级合计，父项拦截
    // ------------------------------------------------------------------

    @Test
    void incident_1362_category_weight_not_summed_with_children() {
        CanonicalTableGrid g = grid("t-0001",
                List.of(
                        cell("t-0001-r0-c0", 0, 0, "编号"),
                        cell("t-0001-r0-c1", 0, 1, "评分因素"),
                        cell("t-0001-r0-c2", 0, 2, "评分标准"),
                        cell("t-0001-r0-c3", 0, 3, "分值"),
                        cellWithMerge("t-0001-r1-c0", 1, 0, "A1", 1, 1, false),
                        cellWithMerge("t-0001-r1-c1", 1, 1, "商务部分（20分）", 3, 1, false),
                        cell("t-0001-r1-c2", 1, 2, "质量管理体系认证"),
                        cell("t-0001-r1-c3", 1, 3, "5"),
                        cellWithMerge("t-0001-r2-c0", 2, 0, "A2", 1, 1, false),
                        cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                        cell("t-0001-r2-c2", 2, 2, "同类项目业绩"),
                        cell("t-0001-r2-c3", 2, 3, "5"),
                        cellWithMerge("t-0001-r3-c0", 3, 0, "A3", 1, 1, false),
                        cellWithMerge("t-0001-r3-c1", 3, 1, "", 1, 1, true),
                        cell("t-0001-r3-c2", 3, 2, "仓储信息化系统"),
                        cell("t-0001-r3-c3", 3, 3, "10")),
                List.of(new MergedRegionDto("t-0001-r1-c1", 3, 1)),
                4, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(g, mapping());

        assertEquals(3, facts.size(), "预期事实数=3（1 父 + 2 子），不得把大类再建成第 4 个平级项");
        ScoreFact parent = facts.get(0);
        List<ScoreFact> children = facts.subList(1, 3);
        assertEquals(parent.factId(), children.get(0).parentFactId());
        assertEquals(parent.factId(), children.get(1).parentFactId());
        assertEquals(2, children.stream().filter(c -> ScoreFact.isLeaf(c, facts)).count(),
                "两个子项都是叶子");

        ScoreFactValidator.ValidationResult scoring = ScoreFactValidator.validateScoringSet(facts);
        assertFalse(scoring.factsValid(), "父项 AUTO_APPROVED 必须拦截");
        assertFalse(scoring.scoringComplete());
        assertEquals(0, autoLeafWeightSum(facts).compareTo(new BigDecimal("15")),
                "自动评分只允许末级合计 15 分；父项 20 分不得与子项相加");
        assertEquals(new BigDecimal("5"), parent.weight(),
                "父项权重取自显式分值列，仅入快照不入评分");
    }

    // ------------------------------------------------------------------
    // SYN-MERGE-V：单列纵向合并 + 延续行分值缺失 → 子项 REVIEW_REQUIRED
    // ------------------------------------------------------------------

    @Test
    void syn_merge_v_blank_continuation_weight_is_review_required() {
        CanonicalTableGrid g = grid("t-0001",
                List.of(
                        cell("t-0001-r0-c0", 0, 0, "编号"),
                        cell("t-0001-r0-c1", 0, 1, "评分因素"),
                        cell("t-0001-r0-c2", 0, 2, "评分标准"),
                        cell("t-0001-r0-c3", 0, 3, "分值"),
                        cellWithMerge("t-0001-r1-c0", 1, 0, "A1", 1, 1, false),
                        cellWithMerge("t-0001-r1-c1", 1, 1, "技术方案（10分）", 2, 1, false),
                        cell("t-0001-r1-c2", 1, 2, "方案完整得满分"),
                        cell("t-0001-r1-c3", 1, 3, "10"),
                        cellWithMerge("t-0001-r2-c0", 2, 0, "A2", 1, 1, false),
                        cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                        cell("t-0001-r2-c2", 2, 2, "提供实施计划"),
                        cell("t-0001-r2-c3", 2, 3, "")),
                List.of(new MergedRegionDto("t-0001-r1-c1", 2, 1)),
                3, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(g, mapping());
        assertEquals(2, facts.size());

        ScoreFact child = facts.get(1);
        assertEquals(ScoreFact.ValidationStatus.REVIEW_REQUIRED, child.validationStatus(),
                "延续行分值缺失 → 子项必须转人工复核");
        assertTrue(child.toCandidate().isEmpty());
        assertFalse(ScoreFactValidator.validateScoringSet(facts).scoringComplete());
    }

    // ------------------------------------------------------------------
    // SYN-MERGE-H：行内 gridSpan 跨列 —— detail 只入文一次，cont 不进 source cell
    // ------------------------------------------------------------------

    @Test
    void syn_merge_h_gridspan_detail_extracted_once() {
        CanonicalTableGrid g = grid("t-0001",
                List.of(
                        cell("t-0001-r0-c0", 0, 0, "编号"),
                        cell("t-0001-r0-c1", 0, 1, "评分因素"),
                        cell("t-0001-r0-c2", 0, 2, "评分标准"),
                        cell("t-0001-r0-c3", 0, 3, "分值"),
                        cell("t-0001-r1-c0", 1, 0, "A1"),
                        cell("t-0001-r1-c1", 1, 1, "技术方案（10分）"),
                        cellWithMerge("t-0001-r1-c2", 1, 2, "方案完整优得10分", 1, 2, false),
                        cellWithMerge("t-0001-r1-c3", 1, 3, "", 1, 1, true)),
                List.of(new MergedRegionDto("t-0001-r1-c2", 1, 2)),
                2, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(g, mapping());
        assertEquals(1, facts.size());
        ScoreFact fact = facts.get(0);

        assertEquals(1, fact.sourceText().split("方案完整优得10分", -1).length - 1,
                "gridSpan 跨列的 detail 文本只允许出现一次");
        assertTrue(fact.sourceCellIds().contains("t-0001-r1-c2"));
        assertFalse(fact.sourceCellIds().contains("t-0001-r1-c3"), "continuation cell 不进 source cell");
        assertEquals(new BigDecimal("10"), fact.weight(), "分值列被跨列覆盖时回退标题括号分值");
        assertEquals(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, fact.weightEvidence().kind());
    }

    // ------------------------------------------------------------------
    // SYN-MERGE-VH：纵横同时合并 —— 延续行父子关联 + 证据隔离
    // ------------------------------------------------------------------

    @Test
    void syn_merge_vh_combined_span_parent_child_linkage() {
        CanonicalTableGrid g = grid("t-0001",
                List.of(
                        cell("t-0001-r0-c0", 0, 0, "编号"),
                        cell("t-0001-r0-c1", 0, 1, "评分因素"),
                        cell("t-0001-r0-c2", 0, 2, "评分标准"),
                        cell("t-0001-r0-c3", 0, 3, "分值"),
                        cellWithMerge("t-0001-r1-c0", 1, 0, "A1", 1, 1, false),
                        cellWithMerge("t-0001-r1-c1", 1, 1, "商务部分（20分）", 2, 2, false),
                        cellWithMerge("t-0001-r1-c2", 1, 2, "", 1, 1, true),
                        cell("t-0001-r1-c3", 1, 3, "20"),
                        cellWithMerge("t-0001-r2-c0", 2, 0, "A2", 1, 1, false),
                        cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                        cellWithMerge("t-0001-r2-c2", 2, 2, "", 1, 1, true),
                        cell("t-0001-r2-c3", 2, 3, "10")),
                List.of(new MergedRegionDto("t-0001-r1-c1", 2, 2)),
                3, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(g, mapping());
        assertEquals(2, facts.size());

        ScoreFact parent = facts.get(0);
        ScoreFact child = facts.get(1);
        assertEquals(parent.factId(), child.parentFactId(), "VH 延续行必须挂到 origin 行");
        assertFalse(ScoreFact.isLeaf(parent, facts));
        assertTrue(ScoreFact.isLeaf(child, facts));
        assertTrue(child.sourceCellIds().stream().noneMatch(id -> id.contains("-r1-")),
                "子项证据不得引用父项行 cell");
        assertTrue(ScoreFactValidator.validateScoringSet(facts).violations().stream()
                .anyMatch(v -> v.contains(parent.factId())));
    }

    // ------------------------------------------------------------------
    // SYN-MULTI-TABLE：多评分表 —— 事实不跨表污染，无法跨表挂父子
    // ------------------------------------------------------------------

    @Test
    void syn_multi_table_facts_do_not_cross_contaminate() {
        CanonicalTableGrid t1 = grid("t-0001",
                List.of(
                        cell("t-0001-r0-c0", 0, 0, "编号"),
                        cell("t-0001-r0-c1", 0, 1, "评分因素"),
                        cell("t-0001-r0-c2", 0, 2, "评分标准"),
                        cell("t-0001-r0-c3", 0, 3, "分值"),
                        cell("t-0001-r1-c0", 1, 0, "A1"),
                        cell("t-0001-r1-c1", 1, 1, "财务状况"),
                        cell("t-0001-r1-c2", 1, 2, "资信良好"),
                        cell("t-0001-r1-c3", 1, 3, "5")),
                List.of(), 2, 4);
        CanonicalTableGrid t2 = grid("t-0002",
                List.of(
                        cell("t-0002-r0-c0", 0, 0, "编号"),
                        cell("t-0002-r0-c1", 0, 1, "评分因素"),
                        cell("t-0002-r0-c2", 0, 2, "评分标准"),
                        cell("t-0002-r0-c3", 0, 3, "分值"),
                        cell("t-0002-r1-c0", 1, 0, "B1"),
                        cell("t-0002-r1-c1", 1, 1, "技术方案"),
                        cell("t-0002-r1-c2", 1, 2, "覆盖全部需求"),
                        cell("t-0002-r1-c3", 1, 3, "10")),
                List.of(), 2, 4);

        List<ScoreFact> facts1 = DeterministicScoreFactExtractor.extract(t1, mapping());
        List<ScoreFact> facts2 = DeterministicScoreFactExtractor.extract(t2, mapping());
        assertEquals(1, facts1.size());
        assertEquals(1, facts2.size());
        assertEquals("t-0001", facts1.get(0).sourceTableId());
        assertEquals("t-0002", facts2.get(0).sourceTableId());
        assertNotEquals(facts1.get(0).factId(), facts2.get(0).factId());

        List<ScoreFact> merged = List.of(facts1.get(0), facts2.get(0));
        assertTrue(merged.stream().allMatch(f -> f.parentFactId().isEmpty()),
                "事实不得跨表挂父子");
        assertTrue(ScoreFactValidator.validateScoringSet(merged).scoringComplete());
    }

    // ------------------------------------------------------------------
    // SYN-HEADER-SPAN：表头跨列 —— 表头内容不得泄漏进事实
    // ------------------------------------------------------------------

    @Test
    void syn_header_span_does_not_leak_into_facts() {
        CanonicalTableGrid g = grid("t-0001",
                List.of(
                        cellWithMerge("t-0001-r0-c0", 0, 0, "编号/大类", 1, 2, false),
                        cellWithMerge("t-0001-r0-c1", 0, 1, "", 1, 1, true),
                        cell("t-0001-r0-c2", 0, 2, "评分标准"),
                        cell("t-0001-r0-c3", 0, 3, "分值"),
                        cell("t-0001-r1-c0", 1, 0, "A1"),
                        cell("t-0001-r1-c1", 1, 1, "财务状况"),
                        cell("t-0001-r1-c2", 1, 2, "资信良好"),
                        cell("t-0001-r1-c3", 1, 3, "5")),
                List.of(new MergedRegionDto("t-0001-r0-c0", 1, 2)),
                2, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(g, mapping());
        assertEquals(1, facts.size(), "headerRows=1，事实只允许来自数据行");
        ScoreFact fact = facts.get(0);
        assertTrue(fact.sourceCellIds().stream().noneMatch(id -> id.contains("-r0-")),
                "表头 cell 不得进入事实证据");
        assertFalse(fact.sourceText().contains("编号/大类"), "表头文本不得泄漏进原文引用");
        assertTrue(ScoreFactValidator.validateScoringSet(facts).scoringComplete());
    }
}
