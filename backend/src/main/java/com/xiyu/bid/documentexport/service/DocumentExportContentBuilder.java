package com.xiyu.bid.documentexport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DocumentExportContentBuilder {

    private final ObjectMapper objectMapper;

    public String build(Project project, DocumentStructure structure, List<DocumentSection> sections) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", project.getId());
        payload.put("projectName", project.getName());
        payload.put("projectStatus", project.getStatus());
        payload.put("structureId", structure.getId());
        payload.put("structureName", structure.getName());
        payload.put("sections", sections.stream()
                .sorted(Comparator.comparing(DocumentSection::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DocumentSection::getId))
                .map(section -> {
                    Map<String, Object> sectionPayload = new LinkedHashMap<>();
                    sectionPayload.put("id", section.getId());
                    sectionPayload.put("parentId", section.getParentId());
                    sectionPayload.put("sectionType", String.valueOf(section.getSectionType()));
                    sectionPayload.put("title", section.getTitle());
                    sectionPayload.put("content", Optional.ofNullable(section.getContent()).orElse(""));
                    sectionPayload.put("orderIndex", section.getOrderIndex());
                    sectionPayload.put("metadata", Optional.ofNullable(section.getMetadata()).orElse(""));
                    return sectionPayload;
                })
                .toList());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to generate document export content", exception);
        }
    }
}
