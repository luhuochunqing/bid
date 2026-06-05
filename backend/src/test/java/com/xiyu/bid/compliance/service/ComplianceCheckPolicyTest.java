package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.dto.RiskAssessmentDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.entity.ComplianceRule;
import com.xiyu.bid.entity.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceCheckPolicyTest {

    @Test
    void summarize_ShouldReturnCompliantWhenNoRulesExist() {
        ComplianceCheckPolicy.Evaluation evaluation = ComplianceCheckPolicy.summarize(List.of(), 0);

        assertThat(evaluation.overallStatus()).isEqualTo(ComplianceCheckResult.Status.COMPLIANT);
        assertThat(evaluation.riskScore()).isZero();
        assertThat(evaluation.failedRules()).isZero();
    }

    @Test
    void summarize_ShouldReturnNonCompliantWhenCriticalFailureExists() {
        ComplianceIssue criticalFailure = issue(ComplianceIssue.Severity.CRITICAL, false);
        ComplianceIssue lowSuccess = issue(ComplianceIssue.Severity.LOW, true);

        ComplianceCheckPolicy.Evaluation evaluation = ComplianceCheckPolicy.summarize(
                List.of(criticalFailure, lowSuccess),
                2
        );

        assertThat(evaluation.overallStatus()).isEqualTo(ComplianceCheckResult.Status.NON_COMPLIANT);
        assertThat(evaluation.riskScore()).isEqualTo(50);
        assertThat(evaluation.failedRules()).isEqualTo(1);
    }

    @Test
    void summarize_ShouldReturnPartialCompliantForModerateFailureRate() {
        ComplianceCheckPolicy.Evaluation evaluation = ComplianceCheckPolicy.summarize(
                List.of(
                        issue(ComplianceIssue.Severity.MEDIUM, false),
                        issue(ComplianceIssue.Severity.LOW, true),
                        issue(ComplianceIssue.Severity.LOW, true),
                        issue(ComplianceIssue.Severity.LOW, true),
                        issue(ComplianceIssue.Severity.LOW, true)
                ),
                5
        );

        assertThat(evaluation.overallStatus()).isEqualTo(ComplianceCheckResult.Status.PARTIAL_COMPLIANT);
        assertThat(evaluation.riskScore()).isEqualTo(10);
        assertThat(evaluation.failedRules()).isEqualTo(1);
    }

    @Test
    void defaultRiskScore_ShouldFollowProjectStatus() {
        assertThat(ComplianceCheckPolicy.defaultRiskScore(Project.Status.BIDDING)).isEqualTo(50);
        assertThat(ComplianceCheckPolicy.defaultRiskScore(Project.Status.WON)).isEqualTo(10);
    }

    @Test
    void recommendationFor_ShouldReturnStableMessages() {
        assertThat(ComplianceCheckPolicy.recommendationFor(RiskAssessmentDTO.RiskLevel.LOW))
                .contains("low risk");
        assertThat(ComplianceCheckPolicy.recommendationFor(RiskAssessmentDTO.RiskLevel.HIGH))
                .contains("Immediate action required");
    }

    private ComplianceIssue issue(ComplianceIssue.Severity severity, boolean passed) {
        return ComplianceIssue.builder()
                .ruleId(1L)
                .ruleName("rule")
                .ruleType(ComplianceRule.RuleType.DOCUMENT)
                .severity(severity)
                .description("desc")
                .passed(passed)
                .build();
    }
}
