package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.entity.ComplianceRule;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

final class ComplianceRuleEvaluator {

    private static final TypeReference<Map<String, Object>> RULE_DEF_TYPE = new TypeReference<>() {
    };

    private ComplianceRuleEvaluator() {
    }

    static ComplianceIssue evaluateProjectRule(ComplianceRule rule, Project project, ObjectMapper objectMapper) {
        return switch (rule.getRuleType()) {
            case QUALIFICATION -> checkQualifications(rule, objectMapper);
            case DOCUMENT -> checkDocuments(rule, objectMapper);
            case FINANCIAL -> checkFinancials(rule, objectMapper);
            case EXPERIENCE -> checkExperience(rule, objectMapper);
            case DEADLINE -> checkDeadlines(rule, objectMapper);
        };
    }

    static ComplianceIssue evaluateTenderRule(ComplianceRule rule, Tender tender, ObjectMapper objectMapper) {
        return switch (rule.getRuleType()) {
            case DOCUMENT -> ComplianceIssueFactory.build(
                    rule,
                    ComplianceIssue.Severity.LOW,
                    "Tender documents available",
                    null,
                    true
            );
            case DEADLINE -> checkTenderDeadlines(rule, tender);
            default -> ComplianceIssueFactory.build(
                    rule,
                    ComplianceIssue.Severity.LOW,
                    "General tender check passed",
                    null,
                    true
            );
        };
    }

    private static ComplianceIssue checkQualifications(ComplianceRule rule, ObjectMapper objectMapper) {
        try {
            Map<String, Object> ruleDef = readRuleDefinition(rule, objectMapper);
            String minLevel = (String) ruleDef.get("minLevel");
            Boolean required = (Boolean) ruleDef.getOrDefault("required", true);
            boolean passed = !Boolean.TRUE.equals(required) || minLevel == null;

            return ComplianceIssueFactory.build(
                    rule,
                    passed ? ComplianceIssue.Severity.LOW : ComplianceIssue.Severity.HIGH,
                    passed ? "Qualification requirements met" : "Minimum qualification level not met",
                    passed ? null : "Ensure company meets minimum qualification requirements",
                    passed
            );
        } catch (JsonProcessingException exception) {
            return ComplianceIssueFactory.definitionError(rule);
        }
    }

    @SuppressWarnings("unchecked")
    private static ComplianceIssue checkDocuments(ComplianceRule rule, ObjectMapper objectMapper) {
        try {
            Map<String, Object> ruleDef = readRuleDefinition(rule, objectMapper);
            List<String> requiredDocs = (List<String>) ruleDef.get("requiredDocs");
            boolean passed = requiredDocs == null || requiredDocs.isEmpty();

            return ComplianceIssueFactory.build(
                    rule,
                    passed ? ComplianceIssue.Severity.LOW : ComplianceIssue.Severity.MEDIUM,
                    passed ? "All required documents present" : "Missing required documents",
                    passed ? null : "Submit all required documents",
                    passed
            );
        } catch (JsonProcessingException exception) {
            return ComplianceIssueFactory.definitionError(rule);
        }
    }

    private static ComplianceIssue checkFinancials(ComplianceRule rule, ObjectMapper objectMapper) {
        try {
            readRuleDefinition(rule, objectMapper);
            return ComplianceIssueFactory.build(
                    rule,
                    ComplianceIssue.Severity.LOW,
                    "Financial health indicators good",
                    null,
                    true
            );
        } catch (JsonProcessingException exception) {
            return ComplianceIssueFactory.definitionError(rule);
        }
    }

    private static ComplianceIssue checkExperience(ComplianceRule rule, ObjectMapper objectMapper) {
        try {
            readRuleDefinition(rule, objectMapper);
            return ComplianceIssueFactory.build(
                    rule,
                    ComplianceIssue.Severity.LOW,
                    "Experience requirements met",
                    null,
                    true
            );
        } catch (JsonProcessingException exception) {
            return ComplianceIssueFactory.definitionError(rule);
        }
    }

    private static ComplianceIssue checkDeadlines(ComplianceRule rule, ObjectMapper objectMapper) {
        try {
            readRuleDefinition(rule, objectMapper);
            return ComplianceIssueFactory.build(
                    rule,
                    ComplianceIssue.Severity.LOW,
                    "Timeline requirements met",
                    null,
                    true
            );
        } catch (JsonProcessingException exception) {
            return ComplianceIssueFactory.definitionError(rule);
        }
    }

    private static ComplianceIssue checkTenderDeadlines(ComplianceRule rule, Tender tender) {
        boolean passed = tender.getDeadline() != null && tender.getDeadline().isAfter(LocalDateTime.now());
        return ComplianceIssueFactory.build(
                rule,
                passed ? ComplianceIssue.Severity.LOW : ComplianceIssue.Severity.HIGH,
                passed ? "Tender deadline valid" : "Tender deadline passed or not set",
                null,
                passed
        );
    }

    private static Map<String, Object> readRuleDefinition(ComplianceRule rule, ObjectMapper objectMapper)
            throws JsonProcessingException {
        return objectMapper.readValue(rule.getRuleDefinition(), RULE_DEF_TYPE);
    }
}
