package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.entity.ComplianceRule;

final class ExperienceCompliancePolicy {

    private ExperienceCompliancePolicy() {
    }

    static ComplianceIssue evaluate(
            ComplianceRule rule,
            ExperienceRuleDefinition definition,
            long matchingProjects
    ) {
        boolean passed = matchingProjects >= definition.minProjects();
        if (passed) {
            return ComplianceIssueFactory.build(
                    rule,
                    ComplianceIssue.Severity.LOW,
                    "historical case library contains enough matching winning projects within %d year(s): found %d, required %d"
                            .formatted(definition.minYears(), matchingProjects, definition.minProjects()),
                    null,
                    true
            );
        }

        return ComplianceIssueFactory.build(
                rule,
                ComplianceIssue.Severity.MEDIUM,
                "historical case library does not contain enough recent winning projects within %d year(s): found %d, required %d"
                        .formatted(definition.minYears(), matchingProjects, definition.minProjects()),
                "Add more matching winning cases or relax the experience threshold",
                false
        );
    }
}
