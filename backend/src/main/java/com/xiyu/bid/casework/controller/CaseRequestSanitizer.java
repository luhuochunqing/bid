package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.domain.model.CaseSearchCriteria;
import com.xiyu.bid.casework.dto.CaseDTO;
import com.xiyu.bid.casework.dto.CasePromoteFromProjectRequest;
import com.xiyu.bid.casework.dto.CaseReferenceRecordCreateRequest;
import com.xiyu.bid.casework.dto.CaseShareRecordCreateRequest;
import com.xiyu.bid.util.InputSanitizer;

import java.math.BigDecimal;
import java.util.List;

final class CaseRequestSanitizer {

    private CaseRequestSanitizer() {
    }

    static void sanitizeCase(CaseDTO dto) {
        if (dto.getTitle() != null) dto.setTitle(InputSanitizer.sanitizeString(dto.getTitle(), 200));
        if (dto.getDescription() != null) dto.setDescription(InputSanitizer.sanitizeString(dto.getDescription(), 2000));
        if (dto.getCustomerName() != null) dto.setCustomerName(InputSanitizer.sanitizeString(dto.getCustomerName(), 255));
        if (dto.getLocationName() != null) dto.setLocationName(InputSanitizer.sanitizeString(dto.getLocationName(), 255));
        if (dto.getProjectPeriod() != null) dto.setProjectPeriod(InputSanitizer.sanitizeString(dto.getProjectPeriod(), 255));
        if (dto.getProductLine() != null) dto.setProductLine(InputSanitizer.sanitizeString(dto.getProductLine(), 255));
        if (dto.getArchiveSummary() != null) dto.setArchiveSummary(InputSanitizer.sanitizeString(dto.getArchiveSummary(), 5000));
        if (dto.getPriceStrategy() != null) dto.setPriceStrategy(InputSanitizer.sanitizeString(dto.getPriceStrategy(), 5000));
        if (dto.getDocumentSnapshotText() != null) dto.setDocumentSnapshotText(InputSanitizer.sanitizeString(dto.getDocumentSnapshotText(), 10000));
        if (dto.getSearchDocument() != null) dto.setSearchDocument(InputSanitizer.sanitizeString(dto.getSearchDocument(), 10000));
        if (dto.getStatus() != null) dto.setStatus(InputSanitizer.sanitizeString(dto.getStatus(), 30));
        if (dto.getVisibility() != null) dto.setVisibility(InputSanitizer.sanitizeString(dto.getVisibility(), 30));
        dto.setTags(sanitizeList(dto.getTags(), 50));
        dto.setHighlights(sanitizeList(dto.getHighlights(), 1000));
        dto.setTechnologies(sanitizeList(dto.getTechnologies(), 255));
        dto.setSuccessFactors(sanitizeList(dto.getSuccessFactors(), 1000));
        dto.setLessonsLearned(sanitizeList(dto.getLessonsLearned(), 1000));
        dto.setAttachmentNames(sanitizeList(dto.getAttachmentNames(), 255));
    }

    static void sanitizePromotion(CasePromoteFromProjectRequest request) {
        if (request.getTitle() != null) request.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 200));
        if (request.getDescription() != null) request.setDescription(InputSanitizer.sanitizeString(request.getDescription(), 2000));
        if (request.getCustomerName() != null) request.setCustomerName(InputSanitizer.sanitizeString(request.getCustomerName(), 255));
        if (request.getLocationName() != null) request.setLocationName(InputSanitizer.sanitizeString(request.getLocationName(), 255));
        if (request.getProjectPeriod() != null) request.setProjectPeriod(InputSanitizer.sanitizeString(request.getProjectPeriod(), 255));
        if (request.getProductLine() != null) request.setProductLine(InputSanitizer.sanitizeString(request.getProductLine(), 255));
        if (request.getArchiveSummary() != null) request.setArchiveSummary(InputSanitizer.sanitizeString(request.getArchiveSummary(), 5000));
        if (request.getPriceStrategy() != null) request.setPriceStrategy(InputSanitizer.sanitizeString(request.getPriceStrategy(), 5000));
        if (request.getDocumentSnapshotText() != null) request.setDocumentSnapshotText(InputSanitizer.sanitizeString(request.getDocumentSnapshotText(), 10000));
        if (request.getStatus() != null) request.setStatus(InputSanitizer.sanitizeString(request.getStatus(), 30));
        if (request.getVisibility() != null) request.setVisibility(InputSanitizer.sanitizeString(request.getVisibility(), 30));
        request.setTags(sanitizeList(request.getTags(), 50));
        request.setHighlights(sanitizeList(request.getHighlights(), 1000));
        request.setTechnologies(sanitizeList(request.getTechnologies(), 255));
        request.setSuccessFactors(sanitizeList(request.getSuccessFactors(), 1000));
        request.setLessonsLearned(sanitizeList(request.getLessonsLearned(), 1000));
        request.setAttachmentNames(sanitizeList(request.getAttachmentNames(), 255));
    }

    static void sanitizeShareRecord(CaseShareRecordCreateRequest request) {
        if (request.getCreatedByName() != null) request.setCreatedByName(InputSanitizer.sanitizeString(request.getCreatedByName(), 100));
        if (request.getBaseUrl() != null) request.setBaseUrl(InputSanitizer.sanitizeString(request.getBaseUrl(), 500));
    }

    static void sanitizeReferenceRecord(CaseReferenceRecordCreateRequest request) {
        if (request.getReferencedByName() != null) request.setReferencedByName(InputSanitizer.sanitizeString(request.getReferencedByName(), 100));
        if (request.getReferenceTarget() != null) request.setReferenceTarget(InputSanitizer.sanitizeString(request.getReferenceTarget(), 255));
        if (request.getReferenceContext() != null) request.setReferenceContext(InputSanitizer.sanitizeString(request.getReferenceContext(), 1000));
    }

    static CaseSearchCriteria sanitizeSearchCriteria(
            String keyword,
            CaseDTO.Industry industry,
            String productLine,
            CaseDTO.Outcome outcome,
            Integer year,
            BigDecimal amountMin,
            BigDecimal amountMax,
            List<String> tags,
            String status,
            String visibility,
            Integer page,
            Integer pageSize,
            String sort
    ) {
        return new CaseSearchCriteria(
                sanitizeQueryText(keyword, 200),
                industry == null ? null : industry.name(),
                sanitizeQueryText(productLine, 100),
                outcome == null ? null : outcome.name(),
                year,
                amountMin,
                amountMax,
                sanitizeTags(tags),
                sanitizeQueryText(status, 30),
                sanitizeQueryText(visibility, 30),
                Math.max(page == null ? 1 : page, 1),
                Math.max(pageSize == null ? 20 : pageSize, 1),
                sanitizeQueryText(sort, 30)
        );
    }

    private static List<String> sanitizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(tag -> sanitizeQueryText(tag, 50))
                .filter(tag -> tag != null && !tag.isBlank())
                .toList();
    }

    private static String sanitizeQueryText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = InputSanitizer.sanitizeString(value, maxLength);
        return sanitized.isBlank() ? null : sanitized;
    }

    private static List<String> sanitizeList(List<String> values, int maxLength) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(value -> InputSanitizer.sanitizeString(value, maxLength))
                .toList();
    }
}
