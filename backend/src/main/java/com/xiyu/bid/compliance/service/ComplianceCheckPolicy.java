package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.dto.RiskAssessmentDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.entity.Project;

import java.util.List;

final class ComplianceCheckPolicy {

    private ComplianceCheckPolicy() {
    }

    static Evaluation summarize(List<ComplianceIssue> issues, int totalRules) {
        int failedRules = (int) issues.stream()
                .filter(issue -> !Boolean.TRUE.equals(issue.getPassed()))
                .count();
        return new Evaluation(
                determineOverallStatus(issues, totalRules, failedRules),
                calculateRiskScore(issues, totalRules),
                failedRules
        );
    }

    static int defaultRiskScore(Project.Status status) {
        return switch (status) {
            case PENDING_INITIATION -> 20;
            case INITIATED -> 20;
            case BIDDING -> 50;
            case EVALUATING -> 40;
            case WON, LOST, FAILED, ABANDONED -> 10;
        };
    }

    static String recommendationFor(RiskAssessmentDTO.RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "Project is low risk. Proceed with normal bidding process.";
            case MEDIUM -> "Project has medium risk. Review compliance issues and address key concerns.";
            case HIGH -> "Project has high risk. Immediate action required to address compliance issues.";
        };
    }

    private static ComplianceCheckResult.Status determineOverallStatus(
            List<ComplianceIssue> issues,
            int totalRules,
            int failedRules
    ) {
        if (totalRules == 0 || failedRules == 0) {
            return ComplianceCheckResult.Status.COMPLIANT;
        }

        double failureRate = (double) failedRules / totalRules;
        boolean hasCritical = issues.stream()
                .anyMatch(issue -> issue.getSeverity() == ComplianceIssue.Severity.CRITICAL
                        && !Boolean.TRUE.equals(issue.getPassed()));

        if (hasCritical || failureRate >= 0.5d) {
            return ComplianceCheckResult.Status.NON_COMPLIANT;
        }
        if (failureRate >= 0.2d) {
            return ComplianceCheckResult.Status.PARTIAL_COMPLIANT;
        }
        return ComplianceCheckResult.Status.WARNING;
    }

    private static int calculateRiskScore(List<ComplianceIssue> issues, int totalRules) {
        if (totalRules == 0 || issues.isEmpty()) {
            return 0;
        }

        int totalScore = issues.stream()
                .mapToInt(issue -> Boolean.TRUE.equals(issue.getPassed()) ? 0 : severityScore(issue.getSeverity()))
                .sum();

        return Math.min(100, totalScore / totalRules);
    }

    private static int severityScore(ComplianceIssue.Severity severity) {
        return switch (severity) {
            case CRITICAL -> 100;
            case HIGH -> 75;
            case MEDIUM -> 50;
            case LOW -> 25;
        };
    }

    record Evaluation(ComplianceCheckResult.Status overallStatus, int riskScore, int failedRules) {
    }
}
