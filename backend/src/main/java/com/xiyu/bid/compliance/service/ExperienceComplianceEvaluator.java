package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.dto.ComplianceIssue;
import com.xiyu.bid.compliance.entity.ComplianceRule;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.CaseRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;

@Component
class ExperienceComplianceEvaluator {

    private final CaseRepository caseRepository;

    ExperienceComplianceEvaluator(CaseRepository pCaseRepository) {
        this.caseRepository = pCaseRepository;
    }

    ComplianceIssue evaluate(ComplianceRule rule, Project project, ObjectMapper objectMapper) {
        try {
            ExperienceRuleDefinition definition = ExperienceRuleDefinitionParser.parse(rule, objectMapper);
            long matchingProjects = caseRepository.countWonCasesByFilters(
                    parseIndustry(project.getIndustry()),
                    normalize(project.getSourceModule()),
                    projectDateFrom(definition.minYears()),
                    null
            );
            return ExperienceCompliancePolicy.evaluate(rule, definition, matchingProjects);
        } catch (JsonProcessingException exception) {
            return ComplianceIssueFactory.definitionError(rule);
        }
    }

    private static Case.Industry parseIndustry(String industry) {
        if (industry == null || industry.isBlank()) {
            return null;
        }
        try {
            return Case.Industry.valueOf(industry.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate projectDateFrom(int minYears) {
        return minYears <= 0 ? null : LocalDate.now().minusYears(minYears).plusDays(1);
    }
}
