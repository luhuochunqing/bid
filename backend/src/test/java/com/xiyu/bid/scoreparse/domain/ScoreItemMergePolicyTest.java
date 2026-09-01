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
    void merge_sourceBackedLongerDetail_replacesTruncatedFirstDetail() {
        // 正则召回先入（跨行单元格被拆碎的残缺片段），LLM 召回后入（对齐回填的完整原文）：
        // later 有原文背书（sourceText 包含 detail）且更完整 → 覆盖首次版本
        ScoreCandidate regexLane = new ScoreCandidate("A1", "技术方案", "方案完整性",
                new BigDecimal("10"), null, null, "方案完整性", "P47", null);
        String fullDetail = "技术方案的内容包括：方案完整性、方案先进性与合理性、实施组织方案。";
        ScoreCandidate llmLane = new ScoreCandidate("A1", "技术方案", fullDetail,
                new BigDecimal("10"), null, null, fullDetail, "P47 评分表", "GRADE_TO_SCORE");
        List<ScoreCandidate> merged = policy.merge(List.of(regexLane, llmLane));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).detail()).isEqualTo(fullDetail);
        assertThat(merged.get(0).sourceText()).isEqualTo(fullDetail);
    }

    @Test
    void merge_unbackedLongerDetail_keepsFirstDetail() {
        // later 无原文背书（sourceText 不包含 detail，LLM 概括改写）：
        // 即使更长也不覆盖首次版本，防止幻觉文本污染
        ScoreCandidate regexLane = new ScoreCandidate("A1", "技术方案", "方案完整性 10 分",
                new BigDecimal("10"), null, null, "方案完整性 10 分", "P47", null);
        ScoreCandidate llmLane = new ScoreCandidate("A1", "技术方案",
                "要求投标人方案完整、先进且合理，同时提供实施组织安排（概括）",
                new BigDecimal("10"), null, null, "技术方案 完整性 先进性 合理性", "P47", null);
        List<ScoreCandidate> merged = policy.merge(List.of(regexLane, llmLane));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).detail()).isEqualTo("方案完整性 10 分");
    }

    @Test
    void merge_emptySourceBacking_keepsFirstDetail() {
        // 对齐失败候选的 sourceText 为空（Mapper 置空防自包含伪背书）：
        // 无背书不覆盖——幻觉长文本即使 detail 自述完整也不得挤掉正则残缺版
        ScoreCandidate regexLane = new ScoreCandidate("A1", "技术方案", "方案完整性 10 分",
                new BigDecimal("10"), null, null, "方案完整性 10 分", "P47", null);
        ScoreCandidate hallucinated = new ScoreCandidate("A1", "技术方案",
                "幻觉长文本：模型编造的完整条款表述，远长于正则召回的残缺行内片段。",
                new BigDecimal("10"), null, null, "", "P47", null);
        List<ScoreCandidate> merged = policy.merge(List.of(regexLane, hallucinated));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).detail()).isEqualTo("方案完整性 10 分");
    }

    @Test
    void merge_sameCodeDifferentDim_backedSupersetDetail_stillEnriched() {
        // 两路召回 dim 表述不一致（正则"技术部分" vs LLM"技术方案"），但正则残缺 detail
        // 是 LLM 对齐版 detail 的子串：判定同一项，补强 detail，dim 保留首次出现
        ScoreCandidate regexLane = new ScoreCandidate("A1", "技术部分", "方案完整性",
                new BigDecimal("10"), null, null, "方案完整性", "P47", null);
        String fullDetail = "技术方案要求：方案完整性、方案先进性与合理性，并提供实施组织安排。";
        ScoreCandidate llmLane = new ScoreCandidate("A1", "技术方案", fullDetail,
                new BigDecimal("10"), null, null, fullDetail, "P47 评分表", "GRADE_TO_SCORE");
        List<ScoreCandidate> merged = policy.merge(List.of(regexLane, llmLane));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).detail()).isEqualTo(fullDetail);
        assertThat(merged.get(0).dim()).isEqualTo("技术部分");
    }

    @Test
    void merge_sameCodeDifferentDim_disjointDetail_keepsBoth() {
        // 真不同项撞编号（如商务表 1 vs 技术表 1）：两段 detail 互不为子串，各自属于不同维度，均应保留
        ScoreCandidate first = new ScoreCandidate("B2", "项目管理机构", "项目经理具备 PMP 证书 5 分",
                new BigDecimal("5"), null, null, "项目经理具备 PMP 证书 5 分", "P52", null);
        String other = "售后服务方案完善、响应机制健全，提供 7×24 小时驻场支持服务安排的得 3 分。";
        ScoreCandidate second = new ScoreCandidate("B2", "售后服务", other,
                new BigDecimal("3"), null, null, other, "P53", null);
        List<ScoreCandidate> merged = policy.merge(List.of(first, second));
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).dim()).isEqualTo("项目管理机构");
        assertThat(merged.get(0).detail()).isEqualTo("项目经理具备 PMP 证书 5 分");
        assertThat(merged.get(1).dim()).isEqualTo("售后服务");
        assertThat(merged.get(1).detail()).isEqualTo(other);
    }

    @Test
    void merge_duplicateCodeAndDim_keepsOne() {
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("A1", "技术方案", "方案完整性 10 分", "10"),
                candidate("A1", "技术方案", "方案完整性 10 分（重复召回）", "10")
        ));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).code()).isEqualTo("A1");
        // 行为锚定（2026-08-20 背书覆盖规则）：重复召回取有原文背书的更完整版，非首次版
        assertThat(merged.get(0).detail()).isEqualTo("方案完整性 10 分（重复召回）");
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
    void merge_sameCodeDifferentDim_preservesBothItems() {
        // 跨维度编号重复但名称内容不同：均保留（例如商务项 1 与技术项 1）
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("1", "商务评分", "企业规模与业绩 10 分", "10"),
                candidate("1", "技术评分", "技术对接方案与能力 20 分", "20")
        ));
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).dim()).isEqualTo("商务评分");
        assertThat(merged.get(1).dim()).isEqualTo("技术评分");
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

    @Test
    void merge_priceDim_crossChunkComplementaryDetails_concatenated() {
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate(null, "报价评分", "价格分计算方法：以评标基准价为基础", "30"),
                candidate(null, "报价评分", "下浮率 K=3%，BP%=80", "30")
        ));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).detail()).contains("价格分计算方法", "下浮率", "BP%=80");
    }

    @Test
    void merge_priceDim_overlappingChunkLines_deduplicated() {
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate(null, "报价评分", "价格分计算方法：以评标基准价为基础\nBP%=80", "30"),
                candidate(null, "报价评分", "BP%=80\nHP%=100", "30")
        ));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).detail())
                .isEqualTo("价格分计算方法：以评标基准价为基础\nBP%=80\nHP%=100");
    }

    @Test
    void merge_priceDim_sameCodeAcrossDimNaming_merged() {
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate("1", "报价评分", "价格分计算方法前半段", "30"),
                candidate("1", "价格评分", "价格分计算公式后半段", "30")
        ));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).detail()).contains("前半段", "后半段");
    }

    @Test
    void merge_priceDim_detailCappedAtMaxLength() {
        StringBuilder baseDetail = new StringBuilder();
        StringBuilder laterDetail = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            baseDetail.append("BASE参数行").append(i).append("：报价评分参数内容填充文本填充文本\n");
            laterDetail.append("LATER参数行").append(i).append("：报价评分参数内容填充文本填充文本\n");
        }
        List<ScoreCandidate> merged = policy.merge(List.of(
                candidate(null, "报价评分", baseDetail.toString(), "30"),
                candidate(null, "报价评分", laterDetail.toString(), "30")
        ));
        assertThat(merged).hasSize(1);
        String detail = merged.get(0).detail();
        assertThat(detail.length()).isLessThanOrEqualTo(ScoreItemMergePolicy.PRICE_DETAIL_MAX_CHARS);
        assertThat(detail).startsWith("BASE参数行0：");
    }

    @Test
    void merge_preservesParentDimAndDimSource() {
        ScoreCandidate tableCandidate = new ScoreCandidate("1", "商务部分", "1. 人员配置：相关经验要求",
                new BigDecimal("10"), "OBJECTIVE", null, "1. 人员配置：相关经验要求", "P1", "TABLE_GRID",
                "商务部分", "PARENT_SINGLE");
        ScoreCandidate llmCandidate = new ScoreCandidate("1", "商务部分", "1. 人员配置：相关经验要求，包含项目经理",
                new BigDecimal("10"), "OBJECTIVE", null, "1. 人员配置：相关经验要求，包含项目经理", "P1", "GLOBAL_MERGE");

        List<ScoreCandidate> merged = policy.merge(List.of(tableCandidate, llmCandidate));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).parentDim()).isEqualTo("商务部分");
        assertThat(merged.get(0).dimSource()).isEqualTo("PARENT_SINGLE");
        assertThat(merged.get(0).isParentOnlyDim()).isTrue();
    }
}
