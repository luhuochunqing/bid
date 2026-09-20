package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectEvidenceRequirementPolicyTest {

    private final ProjectEvidenceRequirementPolicy policy = new ProjectEvidenceRequirementPolicy();

    @Test
    @DisplayName("1117 业绩条款：付款流水、发票和盖章客户清单必须人工核验")
    void assess_project1117_requiresManualEvidence() {
        String detail = "有效业绩须同时满足：2021年1月1日起、合同金额1000万元以上；"
                + "提供合作客户名单、项目联系人及供货覆盖城市并加盖公章；"
                + "提供最少一次付款银行流水及对应发票扫描件。";

        ProjectEvidenceRequirementPolicy.Assessment result = policy.assess(detail);

        assertThat(result.requiresManualReview()).isTrue();
        assertThat(result.manualChecks()).containsExactly(
                "盖章的合作客户名单、项目联系人及供货覆盖城市",
                "合同付款银行流水",
                "对应发票");
    }

    @Test
    @DisplayName("只要求系统已有结构化字段时无需额外人工材料门禁")
    void assess_structuredFieldsOnly_noManualEvidence() {
        ProjectEvidenceRequirementPolicy.Assessment result = policy.assess(
                "2022年1月1日起签订的工业品合同，单项金额不少于500万元，提供3个业绩");

        assertThat(result.requiresManualReview()).isFalse();
        assertThat(result.manualChecks()).isEmpty();
    }

    @Test
    @DisplayName("附件同义表达和合同关键页均要求人工核验")
    void assess_documentSynonyms_requireManualEvidence() {
        ProjectEvidenceRequirementPolicy.Assessment result = policy.assess(
                "提供客户名录、项目联系人和供货区域，并附银行回单、转账记录及合同关键页复印件");

        assertThat(result.manualChecks()).contains(
                "合作客户名单、项目联系人及供货覆盖区域",
                "合同付款凭证",
                "合同或中标证明材料");
    }
}
