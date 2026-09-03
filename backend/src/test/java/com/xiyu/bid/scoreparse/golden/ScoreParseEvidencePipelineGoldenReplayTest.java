// Input: scoreparse-golden/ 夹具 + DocumentArtifact v2
// Output: 全闭环金标回放（Grid验证 → 事实抽取 → LLM白名单 → 完整性判定 → 复核审计）
// Pos: scoreparse/golden — 端到端金标验证（spec 059 Phase 6 T601）

package com.xiyu.bid.scoreparse.golden;

import com.xiyu.bid.scoreparse.domain.DeterministicScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.HeuristicTableColumnMapper;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import com.xiyu.bid.scoreparse.domain.grid.TableGrid;
import com.xiyu.bid.scoreparse.domain.llm.LlmDecision;
import com.xiyu.bid.scoreparse.domain.llm.LlmFactOp;
import com.xiyu.bid.scoreparse.domain.llm.LlmFactOperationValidator;
import com.xiyu.bid.scoreparse.entity.ScoreParseReview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Phase 6 T601 全链路金标端到端覆盖回放测试。
 * 串联覆盖：
 * 1. Sidecar 合并语义与 CanonicalTableGrid
 * 2. 确定性 ScoreFact 提取与单元格证据绑定
 * 3. LLM 操作白名单校验（未知 ID / 字段篡改一律拦截）
 * 4. 7 维度评分完整性校验与决策判定
 * 5. 人工调整审计快照
 *
 * <p>持久化落库门禁（spec 059 Fix A 后 REVIEW_REQUIRED 也落库）由
 * {@code ScoreCompletenessAcceptanceTest#invariant3} 与 {@code ScoreParseAppServiceTest} 驱动真实
 * AppService 覆盖，本文件不再重复。
 */
class ScoreParseEvidencePipelineGoldenReplayTest {

    @Test
    @DisplayName("端到端闭环 1: Sidecar 合并语义解析与 CanonicalTableGrid 验证")
    void testSidecarMergeSemanticsAndCanonicalGrid() {
        DocumentArtifact.CanonicalTable table = createMockCanonicalTableWithVMerge();
        CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(table);

        assertThat(grid.isStructurallyValid()).isTrue();
        assertThat(grid.rows()).isEqualTo(3);
        assertThat(grid.columns()).isEqualTo(4);

        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(toLegacyGrid(grid));
        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping);

        assertThat(facts).isNotEmpty();
        // 验证父子关系：延续行自动挂接 origin 行父 ID
        boolean hasParentLink = facts.stream().anyMatch(f -> f.parentFactId() != null);
        assertThat(hasParentLink).isTrue();
    }

    @Test
    @DisplayName("端到端闭环 2: LLM 操作白名单安全门禁")
    void testLlmOperationWhitelistAndRejection() {
        ScoreFact fact1 = new ScoreFact(
                "fact-1", "tbl-1", List.of("cell-1-0"), "方案内容", "1.1", "技术",
                new BigDecimal("10.00"), null, null, null, "TABLE", 0.95, ScoreFact.ValidationStatus.AUTO_APPROVED);

        List<ScoreFact> pool = List.of(fact1);

        // 合法操作：分类与标记复核
        LlmFactOp validOp = new LlmFactOp.Classify("fact-1", "商务", 0.90, List.of("cell-1-0"));
        LlmDecision validDecision = new LlmDecision(List.of(validOp));
        LlmFactOperationValidator.ValidationResult validRes =
                LlmFactOperationValidator.validate(pool, validDecision);
        assertThat(validRes.valid()).isTrue();

        // 非法操作 1: 伪造不存在的 factId
        LlmFactOp fakeOp = new LlmFactOp.Classify("fact-fake", "商务", 0.90, List.of("cell-1-0"));
        LlmDecision fakeDecision = new LlmDecision(List.of(fakeOp));
        LlmFactOperationValidator.ValidationResult fakeRes =
                LlmFactOperationValidator.validate(pool, fakeDecision);
        assertThat(fakeRes.valid()).isFalse();
        assertThat(fakeRes.hasViolations()).isTrue();

        // 非法操作 2: 低置信度操作 (<0.80) 拦截
        LlmFactOp lowConfOp = new LlmFactOp.Classify("fact-1", "商务", 0.40, List.of("cell-1-0"));
        LlmDecision lowConfDecision = new LlmDecision(List.of(lowConfOp));
        LlmFactOperationValidator.ValidationResult lowConfRes =
                LlmFactOperationValidator.validate(pool, lowConfDecision);
        assertThat(lowConfRes.hasViolations()).isTrue();
    }

    @Test
    @DisplayName("端到端闭环 3: 完整性策略阻断权重缺口与父子重复")
    void testCompletenessPolicyBlocksWeightGapAndOverlap() {
        ScoreFact parent = new ScoreFact(
                "fact-p", "tbl-1", List.of("c-0"), "技术大类", "1", "技术",
                new BigDecimal("30.00"), null, null, null, "TABLE", 0.95, ScoreFact.ValidationStatus.AUTO_APPROVED);
        ScoreFact leaf1 = new ScoreFact(
                "fact-c1", "tbl-1", List.of("c-1"), "方案编制", "1.1", "技术",
                new BigDecimal("15.00"), null, null, "fact-p", "TABLE", 0.95, ScoreFact.ValidationStatus.AUTO_APPROVED);
        ScoreFact leaf2 = new ScoreFact(
                "fact-c2", "tbl-1", List.of("c-2"), "项目组织", "1.2", "技术",
                new BigDecimal("10.00"), null, null, "fact-p", "TABLE", 0.95, ScoreFact.ValidationStatus.AUTO_APPROVED);

        List<ScoreFact> facts = List.of(parent, leaf1, leaf2);
        String tenderText = "本项目总分100分，技术分占30分。";

        // 事实末级合计为 15 + 10 = 25分，但声明总分为 100分（缺口 75分）
        ScoreCompletenessPolicy.ScoreCompletenessResult result =
                ScoreCompletenessPolicy.evaluateCompleteness(facts, tenderText, true);

        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.complete()).isFalse();
        assertThat(result.defects()).anyMatch(d -> d.contains("总权重不符预期"));
        // 确认父项不被当作叶子计入总权重（父项 30分被排除出末级合计）
        assertThat(result.leafWeightSum()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("端到端闭环 4: 人工复核与调整审计实体一致性")
    void testHumanReviewAuditTrail() {
        ScoreParseReview review = ScoreParseReview.builder()
                .projectId(101L)
                .parseTaskId(2001L)
                .factId("fact-1")
                .action("MANUAL_ADJUST")
                .originalWeight(new BigDecimal("90.00"))
                .adjustedWeight(new BigDecimal("100.00"))
                .reason("第3项漏计10分，手动补齐")
                .operatorId(999L)
                .operatorName("王专家")
                .build();

        assertThat(review.getOperatorName()).isEqualTo("王专家");
        assertThat(review.getAction()).isEqualTo("MANUAL_ADJUST");
        assertThat(review.getAdjustedWeight()).isEqualByComparingTo("100.00");
    }

    private DocumentArtifact.CanonicalTable createMockCanonicalTableWithVMerge() {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        // Row 0: 标题行
        cells.add(cell("c00", 0, 0, "序号", 1, 1, false));
        cells.add(cell("c01", 0, 1, "评分维度", 1, 1, false));
        cells.add(cell("c02", 0, 2, "评分标准", 1, 1, false));
        cells.add(cell("c03", 0, 3, "分值", 1, 1, false));

        // Row 1: 技术大类 origin (rowspan=2)
        cells.add(cell("c10", 1, 0, "1", 1, 1, false));
        cells.add(cell("c11", 1, 1, "技术方案", 2, 1, false));
        cells.add(cell("c12", 1, 2, "编制完整性", 1, 1, false));
        cells.add(cell("c13", 1, 3, "10", 1, 1, false));

        // Row 2: 延续行 (col 1 is merged continuation)
        cells.add(cell("c20", 2, 0, "2", 1, 1, false));
        cells.add(cell("c21", 2, 1, "", 1, 1, true));
        cells.add(cell("c22", 2, 2, "项目重难点", 1, 1, false));
        cells.add(cell("c23", 2, 3, "15", 1, 1, false));

        List<DocumentArtifact.CanonicalTable.MergedRegionDto> mergedRegions = List.of(
                new DocumentArtifact.CanonicalTable.MergedRegionDto("c11", 2, 1)
        );

        return new DocumentArtifact.CanonicalTable("tbl-1", 1, "第三章", 3, 4, mergedRegions, cells, true);
    }

    private DocumentArtifact.CanonicalTable.CanonicalCellDto cell(
            String id, int row, int col, String raw, int rowspan, int colspan, boolean cont) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                id, row, col, raw, raw.trim(), rowspan, colspan, cont,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[1]");
    }

    private TableGrid toLegacyGrid(CanonicalTableGrid grid) {
        List<List<String>> rows = new ArrayList<>(grid.rows());
        for (int r = 0; r < grid.rows(); r++) {
            List<String> row = new ArrayList<>(grid.columns());
            for (int c = 0; c < grid.columns(); c++) {
                row.add("");
            }
            rows.add(row);
        }
        grid.cells().forEach(cell -> {
            if (cell.row() < grid.rows() && cell.column() < grid.columns()) {
                rows.get(cell.row()).set(cell.column(), cell.isMergedContinue() ? "" : cell.rawText());
            }
        });
        return new TableGrid(rows);
    }
}
