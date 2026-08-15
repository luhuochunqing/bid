// Input: ScoreItemMergePolicy（merge 方法）
// Output: 候选池合并去重行为验证（spec 041 FR-004）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemMergePolicyTest {

    private final ScoreItemMergePolicy policy = new ScoreItemMergePolicy();

    private ScoreCandidate candidate(String code, String dim, String detail, String weight) {
        return new ScoreCandidate(code, dim, detail,
                weight == null ? null : new BigDecimal(weight),
                null, null, detail, "P47", null);
    }

    @Test
    void merge_duplicateCodeAndDim_keepsOne() {
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("A1", "技术方案", "方案完整性 10 分", "10"),
                candidate("A1", "技术方案", "方案完整性 10 分（重复召回）", "10")
        ));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).code()).isEqualTo("A1");
    }

    @Test
    void merge_duplicateMergesPreferRicherDetail() {
        // 第二次召回携带 contextNote，合并时应保留更丰富的信息
        ScoreCandidate first = new ScoreCandidate("A1", "技术方案", "方案完整性",
                new BigDecimal("10"), null, null, "方案完整性", "P47", null);
        ScoreCandidate second = new ScoreCandidate("A1", "技术方案", "方案完整性 10 分",
                new BigDecimal("10"), null, "注：得分不超过 10 分", "方案完整性 10 分", "P48", "GRADE_TO_SCORE");
        List<ScoreCandidate> merged = policy.merge(List.of(first, second));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).contextNote()).isEqualTo("注：得分不超过 10 分");
        assertThat(merged.get(0).semanticPattern()).isEqualTo("GRADE_TO_SCORE");
    }

    @Test
    void merge_duplicateCodeKeepsFirstOccurrence() {
        // 编号重复但名称不同：保留首次出现（data-model item_index 语义）
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("B2", "项目管理机构", "项目经理具备 PMP 证书 5 分", "5"),
                candidate("B2", "售后服务", "售后响应时间 2 小时内 3 分", "3")
        ));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).dim()).isEqualTo("项目管理机构");
    }

    @Test
    void merge_semanticSimilarButDifferentCode_notDeleted() {
        // 语义相似（同名不同编号）不误删：两路召回各识别一个编号，都保留
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("D2", "资质业绩", "具备 CMMI 5 级证书得 5 分", "5"),
                candidate("D3", "资质业绩", "具备 CMMI 5 级证书得 5 分", "5")
        ));
        assertThat(merged).hasSize(2);
    }

    @Test
    void merge_dimNormalizedBeforeCompare() {
        // 名称空白/全半角差异视为同一项
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("A1", "技术方案", "方案完整性 10 分", "10"),
                candidate("A1", " 技术方案 ", "方案完整性 10 分", "10")
        ));
        assertThat(merged).hasSize(1);
    }

    @Test
    void merge_nullWeightCandidate_keptForValidationLayer() {
        // weight 缺失的候选保留，由后续 Validation 层丢弃并记日志（契约 §1）
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("C1", "实施方案", "实施方案完整性", null)
        ));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).weight()).isNull();
    }

    @Test
    void merge_emptyInput_returnsEmpty() {
        assertThat(policy.merge(List.of())).isEmpty();
    }

    @Test
    void merge_preservesFirstOccurrenceOrder() {
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("A2", "技术方案", "先进性 5 分", "5"),
                candidate("A1", "技术方案", "完整性 10 分", "10"),
                candidate("A2", "技术方案", "先进性 5 分", "5")
        ));
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).code()).isEqualTo("A2");
        assertThat(merged.get(1).code()).isEqualTo("A1");
    }
}
