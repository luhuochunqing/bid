package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.entity.ComplianceRule;

import java.util.Map;

final class ExperienceRuleDefinitionParser {

    private static final TypeReference<Map<String, Object>> RULE_DEF_TYPE = new TypeReference<>() {
    };

    private ExperienceRuleDefinitionParser() {
    }

    static ExperienceRuleDefinition parse(ComplianceRule rule, ObjectMapper objectMapper) throws JsonProcessingException {
        Map<String, Object> ruleDefinition = objectMapper.readValue(rule.getRuleDefinition(), RULE_DEF_TYPE);
        return new ExperienceRuleDefinition(
                readPositiveInt(ruleDefinition.get("minYears")),
                readPositiveInt(ruleDefinition.get("minProjects"))
        );
    }

    private static int readPositiveInt(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
