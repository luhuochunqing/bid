package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowFormTemplateQueryService {

    private final WorkflowFormTemplateStore templateStore;

    @Transactional(readOnly = true)
    public Map<String, Object> getActiveSchema(String templateCode) {
        WorkflowFormTemplateRecord record = templateStore.findActiveByCode(templateCode)
                .orElseThrow(() -> new IllegalArgumentException("未找到启用的流程表单模板: " + templateCode));
        Map<String, Object> schema = new LinkedHashMap<>(record.schema());
        schema.put("templateCode", record.templateCode());
        schema.put("businessType", record.businessType().name());
        schema.put("version", record.version());
        return schema;
    }
}
