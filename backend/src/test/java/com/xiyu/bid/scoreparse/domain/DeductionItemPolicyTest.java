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
}
