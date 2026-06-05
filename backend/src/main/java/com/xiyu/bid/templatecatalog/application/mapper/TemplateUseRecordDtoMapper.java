package com.xiyu.bid.templatecatalog.application.mapper;

import com.xiyu.bid.entity.TemplateUseRecord;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogUseRecordView;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class TemplateUseRecordDtoMapper {

    public TemplateCatalogUseRecordView toDto(TemplateUseRecord record) {
        return TemplateCatalogUseRecordView.builder()
                .id(record.getId())
                .documentName(record.getDocumentName())
                .docType(record.getDocType())
                .projectId(record.getProjectId())
                .applyOptions(splitOptions(record.getAppliedOptions()))
                .usedBy(record.getUsedBy())
                .usedAt(record.getUsedAt())
                .build();
    }

    private List<String> splitOptions(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
