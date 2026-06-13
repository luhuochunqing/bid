package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.CaseZipPackager;
import com.xiyu.bid.casework.domain.model.CaseExportContext;
import com.xiyu.bid.casework.domain.model.CaseExportResult;
import com.xiyu.bid.casework.domain.model.CaseExportZipEntry;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import com.xiyu.bid.casework.domain.policy.CaseExportPolicy;
import com.xiyu.bid.casework.dto.CaseExportQuery;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CaseExportAppService {

    private static final int EXPORT_PAGE_SIZE = 500;

    private final KnowledgeCaseRepository knowledgeCaseRepository;
    private final CaseExportPolicy caseExportPolicy;
    private final CaseZipPackager caseZipPackager;

    public CaseExportResult exportCases(CaseExportQuery query, String operatorName) {
        log.info("Starting case export for operator: {}", operatorName);

        List<KnowledgeCase> cases = fetchAllCasesForExport(query);
        log.info("Fetched {} cases for export", cases.size());

        CaseExportPolicy.ExportValidationResult validation = caseExportPolicy.validateExportRequest(cases);
        if (!validation.valid()) {
            log.warn("Case export validation failed: {}", validation.errorMessage());
            throw new IllegalStateException(validation.errorMessage());
        }

        List<KnowledgeCaseReadModel> sortedCases = caseExportPolicy.sortCasesForExport(cases);
        CaseExportContext context = caseExportPolicy.buildExportContext(sortedCases, operatorName);

        List<CaseExportZipEntry> responseEntries = context.zipEntries().stream()
                .filter(e -> e.entryPath().endsWith(".txt"))
                .toList();

        byte[] indexExcelBytes = buildIndexExcel(sortedCases);

        byte[] zipBytes = caseZipPackager.buildCaseZipBytes(responseEntries, indexExcelBytes);

        log.info("Case export completed: {} bytes for {} cases", zipBytes.length, sortedCases.size());

        return new CaseExportResult(
                zipBytes,
                context.zipFileName(),
                sortedCases.size(),
                zipBytes.length
        );
    }

    private List<KnowledgeCase> fetchAllCasesForExport(CaseExportQuery query) {
        Sort sort = resolveSort(query.sortBy());
        int totalCount = (int) knowledgeCaseRepository.count(buildExportSpecification(query));

        if (totalCount == 0) {
            return List.of();
        }

        int totalPages = (int) Math.ceil((double) totalCount / EXPORT_PAGE_SIZE);
        java.util.ArrayList<KnowledgeCase> allCases = new java.util.ArrayList<>();

        for (int page = 0; page < totalPages; page++) {
            Page<KnowledgeCase> casePage = knowledgeCaseRepository.findAll(
                    buildExportSpecification(query),
                    PageRequest.of(page, EXPORT_PAGE_SIZE, sort)
            );
            allCases.addAll(casePage.getContent());
        }

        return allCases;
    }

    private org.springframework.data.jpa.domain.Specification<KnowledgeCase> buildExportSpecification(CaseExportQuery query) {
        return (root, criteriaQuery, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (hasText(query.keyword())) {
                String pattern = "%" + query.keyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("scoringPointTitle")), pattern),
                        cb.like(cb.lower(root.get("requirementRaw")), pattern),
                        cb.like(cb.lower(root.get("responseText")), pattern),
                        cb.like(cb.lower(root.get("sourceProjectName")), pattern)
                ));
            }

            if (hasText(query.scoringCategory())) {
                predicates.add(cb.equal(root.get("scoringCategory"), query.scoringCategory()));
            }

            if (hasText(query.customerType())) {
                predicates.add(cb.equal(root.get("customerType"), query.customerType()));
            }

            if (query.projectTypes() != null && !query.projectTypes().isEmpty()) {
                predicates.add(root.get("projectType").in(query.projectTypes()));
            }

            if (query.statuses() != null && !query.statuses().isEmpty()) {
                predicates.add(root.get("status").in(query.statuses()));
            }

            if (hasText(query.uploadDateFrom())) {
                LocalDateTime from = LocalDateTime.parse(query.uploadDateFrom() + "T00:00:00");
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (hasText(query.uploadDateTo())) {
                LocalDateTime to = LocalDateTime.parse(query.uploadDateTo() + "T23:59:59");
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Sort resolveSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("createdAt"));
        }
        return switch (sortBy.trim().toLowerCase()) {
            case "reuse" -> Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("reuseCount"));
            default -> Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("createdAt"));
        };
    }

    private byte[] buildIndexExcel(List<? extends KnowledgeCaseReadModel> cases) {
        List<CaseZipPackager.CaseIndexRow> indexRows = cases.stream()
                .map(this::toIndexRow)
                .toList();

        return caseZipPackager.buildCaseIndexExcel(indexRows);
    }

    private CaseZipPackager.CaseIndexRow toIndexRow(KnowledgeCaseReadModel kc) {
        return new CaseZipPackager.CaseIndexRow(
                kc.getId(),
                nullSafe(kc.getSourceProjectName()),
                nullSafe(kc.getScoringPointTitle()),
                nullSafe(kc.getScoringCategory()),
                nullSafe(kc.getCustomerType()),
                nullSafe(kc.getProjectType()),
                nullSafe(kc.getBidResult()),
                nullSafe(kc.getProductLine()),
                kc.getReuseCount() != null ? kc.getReuseCount() : 0,
                nullSafe(kc.getStatus()),
                formatDateTime(kc.getCreatedAt())
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullSafe(String value) {
        return value != null ? value : "-";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
