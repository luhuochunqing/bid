package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeductionItemPolicyTest {

    @Test
    void isDeduction_matchesDeductionKeywords() {
        assertThat(DeductionItemPolicy.isDeductionText("本项为扣分项，最高扣2分")).isTrue();
        assertThat(DeductionItemPolicy.isDeductionText("存在失信行为扣完")).isTrue();
        assertThat(DeductionItemPolicy.isDeductionText("信用中国不良记录")).isTrue();
        assertThat(DeductionItemPolicy.isDeductionText("诚信评价报告")).isTrue();
        assertThat(DeductionItemPolicy.isDeductionText("重大安全事故处罚")).isTrue();
        assertThat(DeductionItemPolicy.isDeductionText("列入黑名单者扣分")).isTrue();
        assertThat(DeductionItemPolicy.isDeductionText("无违规行为得满分")).isTrue();
    }

    @Test
    void isDeduction_candidateScansAllFields() {
        ScoreCandidate dimHit = new ScoreCandidate("1", "诚信评价", "优良", BigDecimal.ZERO, null, null, null, null, null, null, null);
        assertThat(DeductionItemPolicy.isDeduction(dimHit)).isTrue();

        ScoreCandidate parentHit = new ScoreCandidate("2", "公共信用信息报告", "优良", BigDecimal.ZERO, null, null, null, null, null, "一、诚信评价", null);
        assertThat(DeductionItemPolicy.isDeduction(parentHit)).isTrue();

        ScoreCandidate detailHit = new ScoreCandidate("3", "履约情况", "发生违约扣2分", BigDecimal.ZERO, null, null, null, null, null, null, null);
        assertThat(DeductionItemPolicy.isDeduction(detailHit)).isTrue();

        ScoreCandidate normal = new ScoreCandidate("4", "仓储能力", "仓库面积达标", new BigDecimal("10"), null, null, null, null, null, "二、商务评分", null);
        assertThat(DeductionItemPolicy.isDeduction(normal)).isFalse();
    }

    @Test
    void isDeduction_nullSafe() {
        assertThat(DeductionItemPolicy.isDeductionText(null)).isFalse();
        assertThat(DeductionItemPolicy.isDeductionText("")).isFalse();
        assertThat(DeductionItemPolicy.isDeduction(null)).isFalse();
    }

    @Test
    void isCreditRescue_requiresBothSubjectAndTrigger() {
        // 主体词（诚信/信用）+ 失信触发词同时存在才救援
        assertThat(DeductionItemPolicy.isCreditRescue(
                "诚信评价", "供应商存在失信行为记录的按信用评价办法处理")).isTrue();
        // 裸信用词（无失信语义）不救援——真程序性行不得泄漏
        assertThat(DeductionItemPolicy.isCreditRescue(
                "信用评价程序", "推荐成交候选供应商三名信用评价程序按规定执行")).isFalse();
        // 仅主体词、无失信语义不救援
        assertThat(DeductionItemPolicy.isCreditRescue("诚信评价", "供应商信用等级优良")).isFalse();
        // 仅触发词、无主体词不救援
        assertThat(DeductionItemPolicy.isCreditRescue("保证金管理", "失信企业不予退还投标保证金")).isFalse();
        assertThat(DeductionItemPolicy.isCreditRescue()).isFalse();
        assertThat(DeductionItemPolicy.isCreditRescue(null, "  ")).isFalse();
    }

    @Test
    void hasExplicitScoreRule_matchesGradeAndDeductionRules() {
        assertThat(DeductionItemPolicy.hasExplicitScoreRule("优秀得7分")).isTrue();
        assertThat(DeductionItemPolicy.hasExplicitScoreRule("出现失信行为扣2分")).isTrue();
        assertThat(DeductionItemPolicy.hasExplicitScoreRule("扣5 分")).isTrue();
        assertThat(DeductionItemPolicy.hasExplicitScoreRule("不存在失信行为不扣")).isTrue();
        assertThat(DeductionItemPolicy.hasExplicitScoreRule("每起扣减2")).isTrue();
        assertThat(DeductionItemPolicy.hasExplicitScoreRule("供应商信用等级优良")).isFalse();
        assertThat(DeductionItemPolicy.hasExplicitScoreRule()).isFalse();
        assertThat(DeductionItemPolicy.hasExplicitScoreRule(null, "")).isFalse();
    }
}
