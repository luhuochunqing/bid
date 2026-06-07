package com.xiyu.bid.documenteditor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiyu.bid.documenteditor.dto.DraftTreeSkippedSectionDTO;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertNodeRequest;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;
import com.xiyu.bid.documenteditor.entity.SectionType;
import com.xiyu.bid.documenteditor.imports.DocumentDraftTreeImportState;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
class DocumentDraftTreeNodeUpsertService {

    private static final String LOCKED_REASON = "LOCKED";

    private final DocumentSectionRepository sectionRepository;
    private final ObjectMapper objectMapper;

    void upsertNode(DocumentDraftTreeImportState state, Long parentId, int orderIndex, DraftTreeUpsertNodeRequest request) {
        state.getStats().incrementTotal();

        String requestedTitle = trimToNull(request.getTitle());
        String requestedKey = trimToNull(request.getSectionKey());
        DocumentSection existing = findExistingSection(requestedKey, requestedTitle, state.getSectionsByStableKey(), state.getSectionsByTitle());
        if (existing != null && isLocked(state.getLocksBySectionId().get(existing.getId()))) {
            state.getStats().incrementSkipped();
            registerSkipped(existing, state.getStats());
            upsertChildren(state, existing.getId(), request.getChildren());
            return;
        }

        String previousStableKey = existing == null ? null : extractSectionKey(existing.getMetadata());
        String previousTitle = existing == null ? null : existing.getTitle();
        String stableKey = resolveStableKey(requestedKey, previousStableKey, requestedTitle);
        SectionType sectionType = request.getSectionType() != null
                ? request.getSectionType()
                : existing != null && existing.getSectionType() != null
                ? existing.getSectionType()
                : SectionType.SECTION;
        String mergedMetadata = mergeMetadata(existing == null ? null : existing.getMetadata(), request, stableKey);

        DocumentSection section = existing == null
                ? DocumentSection.builder()
                .structureId(state.getStructureId())
                .parentId(parentId)
                .sectionType(sectionType)
                .title(requestedTitle)
                .content(request.getContent())
                .orderIndex(orderIndex)
                .metadata(mergedMetadata)
                .build()
                : existing;

        if (existing != null) {
            boolean changed = applyUpdates(section, parentId, orderIndex, sectionType, requestedTitle, request.getContent(), mergedMetadata);
            if (changed) {
                section = sectionRepository.save(section);
                state.getStats().incrementUpdated();
            }
        } else {
            section = sectionRepository.save(section);
            state.getStats().incrementCreated();
        }

        registerSection(section, previousStableKey, previousTitle, stableKey, state.getSectionsByStableKey(), state.getSectionsByTitle());
        upsertChildren(state, section.getId(), request.getChildren());
    }

    private void upsertChildren(DocumentDraftTreeImportState state, Long parentId, List<DraftTreeUpsertNodeRequest> children) {
        if (children == null || children.isEmpty()) {
            return;
        }
        for (int i = 0; i < children.size(); i++) {
            upsertNode(state, parentId, i + 1, children.get(i));
        }
    }

    private boolean applyUpdates(
            DocumentSection section,
            Long parentId,
            int orderIndex,
            SectionType sectionType,
            String title,
            String content,
            String metadata
    ) {
        boolean changed = false;
        if (!Objects.equals(section.getParentId(), parentId)) {
            section.setParentId(parentId);
            changed = true;
        }
        if (!Objects.equals(section.getOrderIndex(), orderIndex)) {
            section.setOrderIndex(orderIndex);
            changed = true;
        }
        if (!Objects.equals(section.getSectionType(), sectionType)) {
            section.setSectionType(sectionType);
            changed = true;
        }
        if (!Objects.equals(section.getTitle(), title)) {
            section.setTitle(title);
            changed = true;
        }
        if (content != null && !Objects.equals(section.getContent(), content)) {
            section.setContent(content);
            changed = true;
        }
        if (!Objects.equals(section.getMetadata(), metadata)) {
            section.setMetadata(metadata);
            changed = true;
        }
        return changed;
    }

    private DocumentSection findExistingSection(
            String requestedKey,
            String requestedTitle,
            Map<String, DocumentSection> sectionsByStableKey,
            Map<String, DocumentSection> sectionsByTitle
    ) {
        if (requestedKey != null) {
            DocumentSection byKey = sectionsByStableKey.get(normalizeLookup(requestedKey));
            if (byKey != null) {
                return byKey;
            }
        }
        return requestedTitle == null ? null : sectionsByTitle.get(normalizeLookup(requestedTitle));
    }

    private void registerSection(
            DocumentSection section,
            String previousStableKey,
            String previousTitle,
            String newStableKey,
            Map<String, DocumentSection> sectionsByStableKey,
            Map<String, DocumentSection> sectionsByTitle
    ) {
        if (previousStableKey != null) {
            removeIfOwned(sectionsByStableKey, previousStableKey, section.getId());
        }
        if (previousTitle != null) {
            removeIfOwned(sectionsByTitle, previousTitle, section.getId());
        }
        sectionsByStableKey.put(normalizeLookup(newStableKey), section);
        sectionsByTitle.put(normalizeLookup(section.getTitle()), section);
    }

    private void registerSkipped(DocumentSection section, DocumentDraftTreeImportState.ImportStats stats) {
        stats.getSkippedSections().add(DraftTreeSkippedSectionDTO.builder()
                .sectionId(section.getId())
                .sectionKey(extractSectionKey(section.getMetadata()))
                .title(section.getTitle())
                .locked(Boolean.TRUE)
                .reason(LOCKED_REASON)
                .build());
    }

    private void removeIfOwned(Map<String, DocumentSection> index, String key, Long sectionId) {
        String lookupKey = normalizeLookup(key);
        DocumentSection indexedSection = index.get(lookupKey);
        if (indexedSection != null && Objects.equals(indexedSection.getId(), sectionId)) {
            index.remove(lookupKey);
        }
    }

    private boolean isLocked(DocumentSectionLock lock) {
        return lock != null && Boolean.TRUE.equals(lock.getLocked());
    }

    private String mergeMetadata(String existingMetadata, DraftTreeUpsertNodeRequest request, String stableKey) {
        ObjectNode metadata = readMetadataNode(existingMetadata);
        metadata.put("sectionKey", stableKey);

        if (request.getRunId() != null) {
            metadata.put("runId", request.getRunId());
        }
        if (request.getSourceReferences() != null && !request.getSourceReferences().isEmpty()) {
            List<String> sourceReferences = request.getSourceReferences().stream()
                    .map(this::trimToNull)
                    .filter(Objects::nonNull)
                    .toList();
            if (!sourceReferences.isEmpty()) {
                metadata.set("sourceReferences", objectMapper.valueToTree(sourceReferences));
            }
        }
        if (request.getConfidence() != null) {
            metadata.put("confidence", request.getConfidence());
        }
        if (request.getManual() != null) {
            metadata.put("manual", request.getManual());
        }

        return metadata.isEmpty() ? null : metadata.toString();
    }

    private ObjectNode readMetadataNode(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            if (node != null && node.isObject()) {
                return (ObjectNode) node.deepCopy();
            }
        } catch (JsonProcessingException ignored) {
            // Keep legacy metadata text if it cannot be parsed as JSON.
        }

        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("legacyMetadata", metadata);
        return fallback;
    }

    private String resolveStableKey(String requestedKey, String previousStableKey, String requestedTitle) {
        if (requestedKey != null) {
            return requestedKey;
        }
        if (previousStableKey != null) {
            return previousStableKey;
        }
        return requestedTitle;
    }

    private String extractSectionKey(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            if (node != null && node.isObject()) {
                return trimToNull(node.path("sectionKey").asText(null));
            }
        } catch (JsonProcessingException ignored) {
            // Legacy text metadata or malformed JSON should not break draft import.
        }
        return null;
    }

    private String normalizeLookup(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
