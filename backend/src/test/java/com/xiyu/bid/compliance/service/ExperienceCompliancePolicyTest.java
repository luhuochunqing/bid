package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.entity.ComplianceRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceCompliancePolicyTest {

    @Test
    void evaluate_ShouldReturnMediumSeverityFailureWhenWinningCasesAreInsufficient() {
        ComplianceIssue issue = ExperienceCompliancePolicy.evaluate(
                rule(),
                new ExperienceRuleDefinition(1, 2),
                1
        );

        assertThat(issue.getPassed()).isFalse();
        assertThat(issue.getSeverity()).isEqualTo(ComplianceIssue.Severity.MEDIUM);
        assertThat(issue.getDescription()).contains("historical case library does not contain enough recent winning projects");
    }

    @Test
    void evaluate_ShouldDescribeSuccessfulCaseLibraryHit() {
        ComplianceIssue issue = ExperienceCompliancePolicy.evaluate(
                rule(),
                new ExperienceRuleDefinition(3, 2),
                2
        );

        assertThat(issue.getPassed()).isTrue();
        assertThat(issue.getSeverity()).isEqualTo(ComplianceIssue.Severity.LOW);
        assertThat(issue.getDescription()).contains("historical case library contains enough matching winning projects");
    }

    private ComplianceRule rule() {
        return ComplianceRule.builder()
                .id(1L)
                .name("experience")
                .ruleType(ComplianceRule.RuleType.EXPERIENCE)
                .ruleDefinition("{\"minYears\":1,\"minProjects\":2}")
                .build();
    }
}
