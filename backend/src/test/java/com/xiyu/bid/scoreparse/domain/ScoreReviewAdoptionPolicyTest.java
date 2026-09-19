package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreReviewAdoptionPolicyTest {

    private final ScoreReviewAdoptionPolicy policy = new ScoreReviewAdoptionPolicy();

    @Test
    @DisplayName("空事实列表采纳应返回失败")
    void adopt_emptyFacts_returnsFailure() {
        var result = policy.adopt(List.of(), Set.of(), Map.of());
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("候选评分事实为空");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    @DisplayName("默认自动过滤分值为0的占位模板项，提取有效正分值项并计算总分")
    void adopt_autoFiltersZeroWeightPlaceholderItems() {
        var fact1 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "f1", "1", "供应商综合实力", BigDecimal.valueOf(2.0), "具有相关资质得2分", "");
        var fact2 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "f2", "2", "管理体系及资质", BigDecimal.valueOf(2.0), "体系认证齐全得2分", "");
        var placeholderFact = new ScoreReviewAdoptionPolicy.CandidateInput(
                "f3", "3", "某占位模板项", BigDecimal.ZERO, "XX~XX", "");

        var result = policy.adopt(List.of(fact1, fact2, placeholderFact), Set.of(), Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.totalWeight()).isEqualByComparingTo("4.00");
        assertThat(result.candidates().get(0).dim()).isEqualTo("供应商综合实力");
        assertThat(result.candidates().get(0).weight()).isEqualByComparingTo("2.00");
        assertThat(result.candidates().get(1).dim()).isEqualTo("管理体系及资质");
    }

    @Test
    @DisplayName("支持显式指定选中的 factId 集合并应用微调分值")
    void adopt_withSelectedFactIdsAndAdjustments() {
        var fact1 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "f1", "1", "商务部分", BigDecimal.valueOf(10.0), "商务细则", "");
        var fact2 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "f2", "2", "技术方案", BigDecimal.valueOf(50.0), "技术细则", "");
        var fact3 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "f3", "3", "价格部分", BigDecimal.valueOf(40.0), "报价细则", "");

        // 只采纳 f1 和 f2，并将 f1 微调为 15 分
        var result = policy.adopt(
                List.of(fact1, fact2, fact3),
                Set.of("f1", "f2"),
                Map.of("f1", BigDecimal.valueOf(15.0))
        );

        assertThat(result.success()).isTrue();
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.totalWeight()).isEqualByComparingTo("65.00");
        assertThat(result.candidates().get(0).weight()).isEqualByComparingTo("15.00");
        assertThat(result.candidates().get(1).weight()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("当所有选出项分值均为0时，采纳应报错拒绝")
    void adopt_allZeroWeights_returnsFailure() {
        var placeholder1 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "f1", "1", "占位1", BigDecimal.ZERO, "XX~XX", "");
        var placeholder2 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "f2", "2", "占位2", BigDecimal.ZERO, "XX~XX", "");

        var result = policy.adopt(List.of(placeholder1, placeholder2), Set.of(), Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("未选出任何有效分值");
    }

    @Test
    @DisplayName("被引用的父项汇总（如商务30分）应自动被排除，仅保留末级细则叶子项")
    void adopt_excludesParentFactsFromAdoption() {
        var parentFact = new ScoreReviewAdoptionPolicy.CandidateInput(
                "p1", "", "商务部分", BigDecimal.valueOf(30.0), "商务部分汇总", "");
        var childFact1 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "c1", "1", "企业业绩", BigDecimal.valueOf(6.0), "同类项目业绩6分", "p1");
        var childFact2 = new ScoreReviewAdoptionPolicy.CandidateInput(
                "c2", "2", "财务状况", BigDecimal.valueOf(3.0), "财务审计报告3分", "p1");

        var result = policy.adopt(List.of(parentFact, childFact1, childFact2), Set.of(), Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.totalWeight()).isEqualByComparingTo("9.00");
        assertThat(result.candidates().stream().map(ScoreCandidate::dim).toList())
                .containsExactly("企业业绩", "财务状况");
    }
}
