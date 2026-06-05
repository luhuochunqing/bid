package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.entity.ComplianceRule;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceRuleEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluateProjectRule_ShouldFlagQualificationFailure() {
        ComplianceIssue issue = ComplianceRuleEvaluator.evaluateProjectRule(
                rule(ComplianceRule.RuleType.QUALIFICATION, "{\"minLevel\":\"A\",\"required\":true}"),
                Project.builder().build(),
                objectMapper
        );

        assertThat(issue.getPassed()).isFalse();
        assertThat(issue.getSeverity()).isEqualTo(ComplianceIssue.Severity.HIGH);
        assertThat(issue.getDescription()).contains("Minimum qualification level");
    }

    @Test
    void evaluateProjectRule_ShouldReturnDefinitionErrorForInvalidJson() {
        ComplianceIssue issue = ComplianceRuleEvaluator.evaluateProjectRule(
                rule(ComplianceRule.RuleType.DOCUMENT, "{not-json"),
                Project.builder().build(),
                objectMapper
        );

        assertThat(issue.getPassed()).isFalse();
        assertThat(issue.getDescription()).isEqualTo("Invalid rule definition");
    }

    @Test
    void evaluateTenderRule_ShouldFailWhenDeadlinePassed() {
        Tender tender = Tender.builder()
                .deadline(LocalDateTime.now().minusDays(1))
                .build();

        ComplianceIssue issue = ComplianceRuleEvaluator.evaluateTenderRule(
                rule(ComplianceRule.RuleType.DEADLINE, "{}"),
                tender,
                objectMapper
        );

        assertThat(issue.getPassed()).isFalse();
        assertThat(issue.getDescription()).contains("deadline");
    }

    private ComplianceRule rule(ComplianceRule.RuleType ruleType, String definition) {
        return ComplianceRule.builder()
                .id(1L)
                .name("rule")
                .ruleType(ruleType)
                .ruleDefinition(definition)
                .build();
    }
}
