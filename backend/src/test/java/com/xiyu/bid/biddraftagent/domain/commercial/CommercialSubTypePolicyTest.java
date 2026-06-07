package com.xiyu.bid.biddraftagent.domain.commercial;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CommercialSubTypePolicyTest {

    private final CommercialSubTypePolicy policy = new CommercialSubTypePolicy();

    @Test
    void classify_paymentTerms() {
        assertThat(policy.classify("合同签订后预付30%货款")).isEqualTo(CommercialSubType.PAYMENT_TERMS);
    }

    @Test
    void classify_performanceBond() {
        assertThat(policy.classify("中标人需缴纳合同金额10%的履约保证金")).isEqualTo(CommercialSubType.PERFORMANCE_BOND);
    }

    @Test
    void classify_deliveryCycle() {
        assertThat(policy.classify("合同签订后60天内完成交付")).isEqualTo(CommercialSubType.DELIVERY_CYCLE);
    }

    @Test
    void classify_warrantyPeriod() {
        assertThat(policy.classify("提供不少于3年的免费质保期")).isEqualTo(CommercialSubType.WARRANTY_PERIOD);
    }

    @Test
    void classify_breachLiability() {
        assertThat(policy.classify("逾期交货每天按合同金额0.5%扣罚违约金")).isEqualTo(CommercialSubType.BREACH_LIABILITY);
    }

    @Test
    void classify_ipOwnership() {
        assertThat(policy.classify("项目成果的知识产权归采购人所有")).isEqualTo(CommercialSubType.IP_OWNERSHIP);
    }

    @Test
    void classify_null_returnsPaymentTerms() {
        assertThat(policy.classify(null)).isEqualTo(CommercialSubType.PAYMENT_TERMS);
    }

    @Test
    void classifyAll_mixed() {
        List<String> reqs = List.of(
                "合同签订后预付30%",
                "需缴纳10%履约保证金",
                "60天内交付",
                "质保期3年"
        );
        var items = policy.classifyAll(reqs);
        assertThat(items).hasSize(4);
        assertThat(items.get(0).subType()).isEqualTo(CommercialSubType.PAYMENT_TERMS);
        assertThat(items.get(1).subType()).isEqualTo(CommercialSubType.PERFORMANCE_BOND);
        assertThat(items.get(2).subType()).isEqualTo(CommercialSubType.DELIVERY_CYCLE);
        assertThat(items.get(3).subType()).isEqualTo(CommercialSubType.WARRANTY_PERIOD);
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
