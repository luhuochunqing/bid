package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.dto.KnowledgeCaseResponse;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeCaseQueryAppService {

    private final KnowledgeCaseRepository caseRepository;

    public Page<KnowledgeCaseResponse> queryCases(
            String keyword,
            String scoringCategory,
            String customerType,
            List<String> projectTypes,
            String uploadDateFrom,
            String uploadDateTo,
            String closeDateFrom,
            String closeDateTo,
            List<String> statuses,
            String sortBy,
            int page,
            int size) {

        Sort pinnedSort = Sort.by(Sort.Direction.DESC, "isPinned");
        Sort secondarySort = "reuse".equalsIgnoreCase(sortBy)
                ? Sort.by(Sort.Direction.DESC, "reuseCount")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Sort sort = pinnedSort.and(secondarySort);

        Specification<KnowledgeCase> spec = buildSpecification(
                keyword, scoringCategory, customerType, projectTypes,
                uploadDateFrom, uploadDateTo, closeDateFrom, closeDateTo, statuses);

        Page<KnowledgeCase> pagedResult = caseRepository.findAll(spec, PageRequest.of(page, size, sort));

        List<KnowledgeCaseResponse> responses = pagedResult.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(responses, PageRequest.of(page, size), pagedResult.getTotalElements());
    }

    public KnowledgeCase getCaseDetail(Long id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("案例不存在: " + id));
    }

    private Specification<KnowledgeCase> buildSpecification(
            String keyword, String scoringCategory, String customerType,
            List<String> projectTypes,
            String uploadDateFrom, String uploadDateTo,
            String closeDateFrom, String closeDateTo,
            List<String> statuses) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 状态多选：未指定时默认显示 ACTIVE
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            } else {
                predicates.add(cb.equal(root.get("status"), "ACTIVE"));
            }

            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("scoringPointTitle"), kw),
                        cb.like(root.get("requirementRaw"), kw),
                        cb.like(root.get("responseText"), kw)
                ));
            }
            if (scoringCategory != null && !scoringCategory.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("scoringCategory"), scoringCategory.trim()));
            }
            if (customerType != null && !customerType.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("customerType"), customerType.trim()));
            }
            // 项目类型多选
            if (projectTypes != null && !projectTypes.isEmpty()) {
                predicates.add(root.get("projectType").in(projectTypes));
            }
            // 上传时间区间 → createdAt
            if (uploadDateFrom != null && !uploadDateFrom.trim().isEmpty()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDate.parse(uploadDateFrom).atStartOfDay()));
            }
            if (uploadDateTo != null && !uploadDateTo.trim().isEmpty()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), LocalDate.parse(uploadDateTo).atTime(23, 59, 59)));
            }
            // 结项时间区间 → createdAt（案例创建时项目刚好结项，二者一致）
            if (closeDateFrom != null && !closeDateFrom.trim().isEmpty()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDate.parse(closeDateFrom).atStartOfDay()));
            }
            if (closeDateTo != null && !closeDateTo.trim().isEmpty()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), LocalDate.parse(closeDateTo).atTime(23, 59, 59)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private KnowledgeCaseResponse toResponse(KnowledgeCase c) {
        return new KnowledgeCaseResponse(
                c.getId(),
                c.getScoringPointTitle(),
                c.getSourceProjectId(),
                summarize(c.getResponseText()),
                c.getProjectType(),
                c.getCustomerType(),
                c.getReuseCount(),
                c.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                Boolean.TRUE.equals(c.getIsPinned()),
                c.getBidResult(),
                c.getSourceProjectName(),
                c.getProductLine()
        );
    }

    private String summarize(String text) {
        if (text == null) return "";
        return text.length() <= 100 ? text : text.substring(0, 100) + "...";
    }
}
