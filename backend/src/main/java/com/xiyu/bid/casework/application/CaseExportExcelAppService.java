package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.domain.model.CaseExportCriteria;
import com.xiyu.bid.casework.domain.model.CaseExportRecord;
import com.xiyu.bid.casework.domain.policy.CaseExportFilterPolicy;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CaseExportExcelAppService {

    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final DateTimeFormatter FILENAME_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final KnowledgeCaseRepository caseRepository;
    private final CaseExcelGenerator excelGenerator;

    public record ExportResult(byte[] data, String filename, int recordCount) {}

    public ExportResult exportCasesAsExcel(
            String keyword,
            String scoringCategory,
            String customerType,
            List<String> projectTypes,
            String uploadDateFrom,
            String uploadDateTo,
            String closeDateFrom,
            String closeDateTo,
            List<String> statuses) {

        CaseExportCriteria criteria = CaseExportCriteria.fromQueryParams(
                keyword, scoringCategory, customerType, projectTypes,
                uploadDateFrom, uploadDateTo, closeDateFrom, closeDateTo, statuses);

        List<KnowledgeCase> allCases = fetchAllCases(criteria);
        List<KnowledgeCase> filteredCases = CaseExportFilterPolicy.filterCases(allCases, criteria);

        List<CaseExportRecord> exportRecords = filteredCases.stream()
                .map(CaseExportFilterPolicy::toExportRecord)
                .toList();

        byte[] excelData;
        try {
            CaseExcelGenerator.ExportResult result = excelGenerator.generate(exportRecords);
            excelData = result.data();
            log.info("导出案例库台账成功，记录数: {}", result.recordCount());
        } catch (IOException e) {
            log.error("生成 Excel 文件失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }

        String filename = buildFilename();
        return new ExportResult(excelData, filename, exportRecords.size());
    }

    private List<KnowledgeCase> fetchAllCases(CaseExportCriteria criteria) {
        List<KnowledgeCase> allCases = new ArrayList<>();
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            Page<KnowledgeCase> page = caseRepository.findAll(
                    buildSpecification(criteria),
                    PageRequest.of(pageNumber, DEFAULT_PAGE_SIZE,
                            Sort.by(Sort.Direction.DESC, "isPinned")
                                    .and(Sort.by(Sort.Direction.DESC, "createdAt")))
            );

            allCases.addAll(page.getContent());
            hasMore = page.hasNext();
            pageNumber++;

            if (allCases.size() >= 10000) {
                log.warn("导出记录超过10000条，截断处理");
                break;
            }
        }

        return allCases;
    }

    private Specification<KnowledgeCase> buildSpecification(CaseExportCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String kw = "%" + criteria.keyword().trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("scoringPointTitle"), kw),
                        cb.like(root.get("requirementRaw"), kw),
                        cb.like(root.get("responseText"), kw)
                ));
            }
            if (criteria.scoringCategory() != null && !criteria.scoringCategory().isBlank()) {
                predicates.add(cb.equal(root.get("scoringCategory"), criteria.scoringCategory().trim()));
            }
            if (criteria.customerType() != null && !criteria.customerType().isBlank()) {
                predicates.add(cb.equal(root.get("customerType"), criteria.customerType().trim()));
            }
            if (criteria.projectTypes() != null && !criteria.projectTypes().isEmpty()) {
                predicates.add(root.get("projectType").in(criteria.projectTypes()));
            }
            if (criteria.uploadDateFrom() != null && !criteria.uploadDateFrom().isBlank()) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        LocalDate.parse(criteria.uploadDateFrom()).atStartOfDay()));
            }
            if (criteria.uploadDateTo() != null && !criteria.uploadDateTo().isBlank()) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        LocalDate.parse(criteria.uploadDateTo()).atTime(23, 59, 59)));
            }
            if (criteria.closeDateFrom() != null && !criteria.closeDateFrom().isBlank()) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        LocalDate.parse(criteria.closeDateFrom()).atStartOfDay()));
            }
            if (criteria.closeDateTo() != null && !criteria.closeDateTo().isBlank()) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        LocalDate.parse(criteria.closeDateTo()).atTime(23, 59, 59)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String buildFilename() {
        String timestamp = LocalDate.now().format(FILENAME_DATE_FORMATTER);
        return "方案管理-案例库台账-" + timestamp + ".xlsx";
    }
}
