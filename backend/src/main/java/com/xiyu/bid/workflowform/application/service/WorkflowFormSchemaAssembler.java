package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateRecord;
import com.xiyu.bid.workflowform.domain.FormFieldDefinition;
import com.xiyu.bid.workflowform.domain.FormFieldType;
import com.xiyu.bid.workflowform.domain.FormSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WorkflowFormSchemaAssembler {

    public FormSchema toSchema(WorkflowFormTemplateRecord record) {
        return new FormSchema(record.templateCode(), record.businessType(), fields(record.schema()));
    }

    private List<FormFieldDefinition> fields(Map<String, Object> schema) {
        Object rawFields = schema == null ? null : schema.get("fields");
        if (!(rawFields instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(FormFieldDefinition::fromMap)
                .toList();
    }
}
