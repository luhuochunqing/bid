package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceRecordJpaRepository;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 项目业绩匹配（POST /api/knowledge/project/match，FR-010）。
 *
 * <p>规则：project_type/industry 命中 AND（signedAfter 空 OR signing_date ≥）
 * AND 金额条件（contract_amount 为 NULL 的存量行跳过金额比对不失配——research R7，
 * 金额语义在 Java 侧二次守卫保证可测）。
 */
@Service
@RequiredArgsConstructor
public class ProjectMatchService {

    private final PerformanceRecordJpaRepository repository;

    public KnowledgeMatchResult match(ProjectMatchRequest request) {
        List<ProjectMatchedItem> matched = repository.findAll(specificationOf(request)).stream()
                .filter(row -> amountMatches(row.getContractAmount(), request.minContractAmount()))
                .map(row -> new ProjectMatchedItem(row.getId(), row.getContractName(),
                        displayName(row), row.getSigningDate(), row.getContractAmount()))
                .toList();
        MatchTierPolicy.Outcome outcome = MatchTierPolicy.evaluate(
                matched.size(), request.requiredCount(), false, false);
        return new KnowledgeMatchResult(outcome.tier(), outcome.matchRatio(), matched,
                matched.isEmpty() ? "未命中符合条件的业绩"
                        : "命中业绩 " + matched.size() + "/" + effectiveRequired(request.requiredCount()));
    }

    private Specification<PerformanceRecordEntity> specificationOf(ProjectMatchRequest request) {
        List<String> keywords = request.projectTypeKeywords() == null ? List.of() : request.projectTypeKeywords();
        List<ProjectType> enumHits = keywords.stream()
                .map(ProjectMatchService::parseProjectType)
                .filter(Objects::nonNull)
                .toList();
        List<String> textKeywords = keywords.stream()
                .filter(keyword -> parseProjectType(keyword) == null)
                .toList();
        return (root, query, cb) -> {
            List<Predicate> typePredicates = new ArrayList<>();
            if (!enumHits.isEmpty()) {
                typePredicates.add(root.get("projectType").in(enumHits));
            }
            textKeywords.stream()
                    .filter(StringUtils::hasText)
                    .forEach(keyword -> typePredicates.add(
                            cb.like(root.get("industry"), "%" + keyword.trim() + "%")));
            List<Predicate> predicates = new ArrayList<>();
            if (!typePredicates.isEmpty()) {
                predicates.add(cb.or(typePredicates.toArray(new Predicate[0])));
            }
            if (request.signedAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("signingDate"), request.signedAfter()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** NULL 金额跳过比对不失配（存量数据）；非 NULL 时需 ≥ 下限。 */
    private static boolean amountMatches(BigDecimal contractAmount, BigDecimal minContractAmount) {
        return minContractAmount == null || contractAmount == null
                || contractAmount.compareTo(minContractAmount) >= 0;
    }

    private static ProjectType parseProjectType(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        for (ProjectType type : ProjectType.values()) {
            if (type.name().equalsIgnoreCase(trimmed) || type.displayName().equals(trimmed)) {
                return type;
            }
        }
        return null;
    }

    private static String displayName(PerformanceRecordEntity row) {
        return row.getProjectType() != null ? row.getProjectType().displayName() : row.getIndustry();
    }

    private static int effectiveRequired(Integer requiredCount) {
        return (requiredCount == null || requiredCount <= 0) ? 1 : requiredCount;
    }
}
