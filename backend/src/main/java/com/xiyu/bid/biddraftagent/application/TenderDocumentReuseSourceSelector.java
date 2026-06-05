package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class TenderDocumentReuseSourceSelector {

    static final String DOCUMENT_CATEGORY = "TENDER_FILE";
    static final String LINKED_ENTITY_TYPE = "TENDER";

    Optional<ProjectDocument> firstReusableProjectDocument(List<ProjectDocument> documents, Long tenderId) {
        return documents.stream()
                .filter(document -> isReusableProjectDocument(document, tenderId))
                .findFirst();
    }

    Optional<ProjectDocument> tenderSourceDocument(Long projectId, Tender tender) {
        String fileUrl = trimToNull(tender.getSourceDocumentFileUrl());
        if (fileUrl == null) {
            return Optional.empty();
        }
        return Optional.of(ProjectDocument.builder()
                .projectId(projectId)
                .name(firstNonBlank(tender.getSourceDocumentName(), "招标文件"))
                .fileType(firstNonBlank(tender.getSourceDocumentFileType(), TenderDocumentFileType.toProjectDocumentType(
                        tender.getSourceDocumentName(),
                        null
                )))
                .documentCategory(DOCUMENT_CATEGORY)
                .linkedEntityType(LINKED_ENTITY_TYPE)
                .linkedEntityId(tender.getId())
                .fileUrl(fileUrl)
                .uploaderName("系统")
                .build());
    }

    String contentTypeOf(ProjectDocument document) {
        String fileType = trimToNull(document.getFileType());
        return fileType != null && fileType.contains("/") ? fileType : null;
    }

    private boolean isReusableProjectDocument(ProjectDocument document, Long tenderId) {
        if (document == null || trimToNull(document.getFileUrl()) == null) {
            return false;
        }
        boolean linkedTender = LINKED_ENTITY_TYPE.equals(trimToNull(document.getLinkedEntityType()))
                && Objects.equals(tenderId, document.getLinkedEntityId());
        boolean categoryTender = DOCUMENT_CATEGORY.equals(trimToNull(document.getDocumentCategory()));
        return linkedTender || categoryTender || nameLooksLikeTender(document.getName());
    }

    private boolean nameLooksLikeTender(String name) {
        String normalized = trimToNull(name);
        if (normalized == null) {
            return false;
        }
        String lowerName = normalized.toLowerCase(Locale.ROOT);
        return lowerName.contains("招标")
                || lowerName.contains("标书")
                || lowerName.contains("tender")
                || lowerName.contains("bid");
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = trimToNull(preferred);
        return normalized != null ? normalized : fallback;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
