package com.xiyu.bid.documenteditor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertRequest;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertResultDTO;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.documenteditor.imports.DocumentDraftTreeImportState;
import com.xiyu.bid.documenteditor.repository.DocumentSectionLockRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DocumentDraftTreeImportService {

    private final DocumentSectionRepository sectionRepository;
    private final DocumentSectionLockRepository lockRepository;
    private final DocumentDraftTreeNodeUpsertService nodeUpsertService;
    private final DocumentDraftTreeStructureService structureService;
    private final ObjectMapper objectMapper;

    DraftTreeUpsertResultDTO upsertDraftTree(Long projectId, DraftTreeUpsertRequest request) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Draft tree request is required");
        }
        if (request.getSections() == null || request.getSections().isEmpty()) {
            throw new IllegalArgumentException("Draft sections are required");
        }
        boolean structureCreated = !structureService.exists(projectId);
        DocumentStructure structure = structureService.resolveStructure(projectId, request);

        List<DocumentSection> existingSections = sectionRepository.findByStructureId(structure.getId());
        Map<String, DocumentSection> sectionsByStableKey = new LinkedHashMap<>();
        Map<String, DocumentSection> sectionsByTitle = new LinkedHashMap<>();
        indexExistingSections(existingSections, sectionsByStableKey, sectionsByTitle);

        Map<Long, DocumentSectionLock> locksBySectionId = loadLocks(existingSections);
        DocumentDraftTreeImportState state = new DocumentDraftTreeImportState(
                projectId,
                structure.getId(),
                structureCreated,
                sectionsByStableKey,
                sectionsByTitle,
                locksBySectionId
        );

        for (int i = 0; i < request.getSections().size(); i++) {
            nodeUpsertService.upsertNode(state, null, i + 1, request.getSections().get(i));
        }

        return state.toResult();
    }

    private void indexExistingSections(
            List<DocumentSection> existingSections,
            Map<String, DocumentSection> sectionsByStableKey,
            Map<String, DocumentSection> sectionsByTitle
    ) {
        existingSections.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DocumentSection::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(section -> {
                    sectionsByStableKey.put(normalizeLookup(extractSectionKey(section.getMetadata(), section.getTitle())), section);
                    sectionsByTitle.put(normalizeLookup(section.getTitle()), section);
                });
    }

    private Map<Long, DocumentSectionLock> loadLocks(List<DocumentSection> existingSections) {
        List<Long> sectionIds = existingSections.stream()
                .map(DocumentSection::getId)
                .filter(Objects::nonNull)
                .toList();
        if (sectionIds.isEmpty()) {
            return Map.of();
        }
        return lockRepository.findBySectionIdIn(sectionIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(DocumentSectionLock::getSectionId, lock -> lock, (left, right) -> left, LinkedHashMap::new));
    }

    private String extractSectionKey(String metadata, String fallbackTitle) {
        if (metadata == null || metadata.isBlank()) {
            return fallbackTitle;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            if (node != null && node.isObject()) {
                String key = trimToNull(node.path("sectionKey").asText(null));
                return key != null ? key : fallbackTitle;
            }
        } catch (JsonProcessingException ignored) {
            // fall through to the title fallback
        }
        return fallbackTitle;
    }

    private String normalizeLookup(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
