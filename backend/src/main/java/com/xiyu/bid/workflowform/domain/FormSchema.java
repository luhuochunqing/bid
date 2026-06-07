package com.xiyu.bid.workflowform.domain;

import java.util.List;

public record FormSchema(String templateCode, FormBusinessType businessType, List<FormFieldDefinition> fields) {

    public FormSchema {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
