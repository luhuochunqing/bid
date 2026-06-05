package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.entity.ComplianceRule;

final class ComplianceIssueFactory {

    private ComplianceIssueFactory() {
    }

    static ComplianceIssue build(
            ComplianceRule rule,
            ComplianceIssue.Severity severity,
            String description,
            String recommendation,
            boolean passed
    ) {
        return ComplianceIssue.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .ruleType(rule.getRuleType())
                .severity(severity)
                .description(description)
                .recommendation(recommendation)
                .passed(passed)
                .build();
    }

    static ComplianceIssue definitionError(ComplianceRule rule) {
        return build(
                rule,
                ComplianceIssue.Severity.MEDIUM,
                "Invalid rule definition",
                "Review and fix rule definition",
                false
        );
    }

    static ComplianceIssue executionFailure(ComplianceRule rule, Exception exception) {
        return build(
                rule,
                ComplianceIssue.Severity.MEDIUM,
                "Rule check failed: " + exception.getMessage(),
                "Review rule configuration",
                false
        );
    }
}
