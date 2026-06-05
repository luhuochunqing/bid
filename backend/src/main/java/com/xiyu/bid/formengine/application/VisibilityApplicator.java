package com.xiyu.bid.formengine.application;

import com.xiyu.bid.formengine.domain.FieldVisibility;
import com.xiyu.bid.formengine.domain.FormFieldCondition;
import com.xiyu.bid.formengine.domain.ResolvedField;
import com.xiyu.bid.workflowform.domain.FormFieldDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 字段可见性应用器。
 * 职责：根据可见性规则和条件规则，计算字段的 hidden/readonly 状态。
 */
@Component
public class VisibilityApplicator {

    private final ConditionEvaluator conditionEvaluator;

    public VisibilityApplicator(ConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    public List<ResolvedField> apply(
            List<FormFieldDefinition> fields,
            List<FieldVisibility> visibilityRules,
            List<FormFieldCondition> conditions,
            Map<String, Object> formData) {

        Map<String, List<FieldVisibility>> visibilityByField = visibilityRules.stream()
                .collect(java.util.stream.Collectors.groupingBy(FieldVisibility::fieldKey));
        Map<String, List<FormFieldCondition>> conditionsBySource = conditions.stream()
                .collect(java.util.stream.Collectors.groupingBy(FormFieldCondition::sourceField));

        return fields.stream()
                .map(field -> resolveField(
                        field,
                        visibilityByField.get(field.key()),
                        conditionsBySource.get(field.key()),
                        formData))
                .toList();
    }

    private ResolvedField resolveField(
            FormFieldDefinition field,
            List<FieldVisibility> rules,
            List<FormFieldCondition> conditions,
            Map<String, Object> formData) {

        boolean hidden = field.hidden().orElse(false);
        boolean readonly = field.readonly().orElse(false);

        if (rules != null) {
            for (FieldVisibility rule : rules) {
                if (rule.orgId() != null) continue;
                if (rule.hidden()) hidden = true;
                if (rule.readonly()) readonly = true;
            }
        }

        if (conditions != null) {
            for (FormFieldCondition cond : conditions) {
                Object sourceValue = formData.get(cond.sourceField());
                if (conditionEvaluator.evaluate(cond, sourceValue)) {
                    String target = conditionEvaluator.targetFieldOf(cond);
                    if (target.equals(field.key())) {
                        switch (conditionEvaluator.actionFor(cond)) {
                            case "hide" -> hidden = true;
                            case "show" -> hidden = false;
                            case "readonly" -> readonly = true;
                        }
                    }
                }
            }
        }

        return ResolvedField.builder()
                .key(field.key())
                .label(field.label())
                .type(field.type().name())
                .required(field.required())
                .hidden(hidden)
                .readonly(readonly)
                .options(field.options().orElse(null))
                .build();
    }
}
