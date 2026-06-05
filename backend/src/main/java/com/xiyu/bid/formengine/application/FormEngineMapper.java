package com.xiyu.bid.formengine.application;

import com.xiyu.bid.formengine.domain.CrossFieldValidationRule;
import com.xiyu.bid.formengine.domain.FieldVisibility;
import com.xiyu.bid.formengine.domain.FormFieldCondition;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.CrossFieldValidationRuleEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldConditionEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldVisibilityEntity;

/**
 * Entity → Domain record 转换器。
 */
public final class FormEngineMapper {

    private FormEngineMapper() {}

    public static FieldVisibility toVisibility(FormFieldVisibilityEntity entity) {
        return new FieldVisibility(
                entity.getId(),
                entity.getFieldKey(),
                entity.getRolePattern(),
                entity.getOrgId(),
                entity.getVisible(),
                entity.getReadonly(),
                entity.getHidden()
        );
    }

    public static FormFieldCondition toCondition(FormFieldConditionEntity entity) {
        return FormFieldCondition.withId(
                entity.getId(),
                entity.getSourceField(),
                entity.getOperator(),
                entity.getTargetValue(),
                entity.getAction(),
                entity.getTargetField(),
                entity.getDisplayOrder()
        );
    }

    public static CrossFieldValidationRule toCrossFieldRule(CrossFieldValidationRuleEntity entity) {
        return new CrossFieldValidationRule(
                entity.getId(),
                entity.getDefinition().getId(),
                entity.getScope(),
                entity.getSourceField(),
                entity.getOperator(),
                entity.getTargetField(),
                entity.getTargetValue(),
                entity.getErrorMessage(),
                entity.getPriority()
        );
    }
}
