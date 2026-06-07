package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
class TenderRequirementEntityFactory {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "qualification",
            "technical",
            "commercial",
            "pricing",
            "legal",
            "delivery",
            "scoring",
            "material",
            "other"
    );

    List<BidRequirementItem> buildItems(
            Long projectId,
            Long tenderId,
            Long documentId,
            TenderRequirementProfile profile
    ) {
        return profile.items().stream()
                .map(item -> buildItem(projectId, tenderId, documentId, item))
                .toList();
    }

    BidTenderDocumentSnapshot buildSnapshot(
            Long projectId,
            Long tenderId,
            ProjectDocument document,
            StoredTenderDocument storedDocument,
            ExtractedTenderDocument extracted,
            TenderRequirementProfile profile,
            String profileJson
    ) {
        return BidTenderDocumentSnapshot.builder()
                .projectId(projectId)
                .tenderId(tenderId)
                .projectDocumentId(document.getId())
                .fileName(extracted.fileName())
                .contentType(extracted.contentType())
                .fileUrl(storedDocument.fileUrl())
                .storagePath(storedDocument.storagePath())
                .contentSha256(storedDocument.contentSha256())
                .extractedText(extracted.text())
                .profileJson(profileJson)
                .extractorKey(extracted.extractorKey())
                .analyzerKey("openai-tender-requirements-v1")
                .build();
    }

    private BidRequirementItem buildItem(
            Long projectId,
            Long tenderId,
            Long documentId,
            TenderRequirementItemSnapshot item
    ) {
        return BidRequirementItem.builder()
                .projectId(projectId)
                .tenderId(tenderId)
                .projectDocumentId(documentId)
                .category(normalizeCategory(item.category()))
                .title(defaultText(item.title(), "未命名要求"))
                .content(defaultText(item.content(), item.title()))
                .sourceExcerpt(trimToNull(item.sourceExcerpt()))
                .mandatory(item.mandatory())
                .confidence(normalizeConfidence(item.confidence()))
                .build();
    }

    private String normalizeCategory(String category) {
        String value = defaultText(category, "other").toLowerCase();
        return ALLOWED_CATEGORIES.contains(value) ? value : "other";
    }

    private Integer normalizeConfidence(Integer confidence) {
        if (confidence == null) {
            return null;
        }
        return Math.max(0, Math.min(100, confidence));
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            return trimmed;
        }
        String fallbackTrimmed = trimToNull(fallback);
        return fallbackTrimmed == null ? "" : fallbackTrimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
