package com.xiyu.bid.biddraftagent.domain.risk;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RedLineRiskPolicyTest {

    private final RedLineRiskPolicy policy = new RedLineRiskPolicy();

    @Test
    void classify_redLine_disqualification() {
        assertThat(policy.classify("未按规定装订标书将被废标")).isEqualTo(RiskLevel.RED_LINE);
    }

    @Test
    void classify_redLine_bondOverdue() {
        assertThat(policy.classify("投标保证金逾期未缴纳将取消资格")).isEqualTo(RiskLevel.RED_LINE);
    }

    @Test
    void classify_redLine_fraud() {
        assertThat(policy.classify("提供虚假业绩材料将被否决投标")).isEqualTo(RiskLevel.RED_LINE);
    }

    @Test
    void classify_redLine_overBudget() {
        assertThat(policy.classify("报价超过控制价将被废标")).isEqualTo(RiskLevel.RED_LINE);
    }

    @Test
    void classify_redLine_noSeal() {
        assertThat(policy.classify("投标文件未加盖公章视为无效")).isEqualTo(RiskLevel.RED_LINE);
    }

    @Test
    void classify_warning_general() {
        assertThat(policy.classify("项目竞争激烈，建议提前准备")).isEqualTo(RiskLevel.WARNING);
    }

    @Test
    void classify_warning_incomplete() {
        assertThat(policy.classify("技术方案描述不完整，建议补充")).isEqualTo(RiskLevel.WARNING);
    }

    @Test
    void classify_null_returnsInfo() {
        assertThat(policy.classify(null)).isEqualTo(RiskLevel.INFO);
    }

    @Test
    void classifyAll_mixed() {
        var items = policy.classifyAll(List.of(
                "未按规定装订将被废标",
                "竞争激烈注意风险",
                "报价超控制价废标处理"
        ));
        assertThat(items).hasSize(3);
        assertThat(items.get(0).level()).isEqualTo(RiskLevel.RED_LINE);
        assertThat(items.get(1).level()).isEqualTo(RiskLevel.WARNING);
        assertThat(items.get(2).level()).isEqualTo(RiskLevel.RED_LINE);
    }

    @Test
    void classifyAll_empty() {
        assertThat(policy.classifyAll(List.of())).isEmpty();
    }

    @Test
    void classifyAll_null() {
        assertThat(policy.classifyAll(null)).isEmpty();
    }
}
