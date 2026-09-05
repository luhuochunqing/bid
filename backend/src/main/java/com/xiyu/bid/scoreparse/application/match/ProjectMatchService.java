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
@lombok.extern.slf4j.Slf4j
public class ProjectMatchService {

    private final PerformanceRecordJpaRepository repository;

    public KnowledgeMatchResult match(ProjectMatchRequest request) {
        if (request == null) {
            return KnowledgeMatchResult.empty("请求参数为空");
        }
        List<String> keywords = request.projectTypeKeywords() == null ? List.of() : request.projectTypeKeywords();
        List<ProjectType> enumHits = keywords.stream()
                .map(ProjectMatchService::parseProjectType)
                .filter(Objects::nonNull)
                .toList();
        List<String> textKeywords = filterSpecificProjectKeywords(
                keywords.stream().filter(keyword -> parseProjectType(keyword) == null).toList()
        );

        // 关键门禁：若没有任何可执行的业务过滤约束，严禁裸查全表导致假阳性满分！
        if (enumHits.isEmpty() && textKeywords.isEmpty() && request.signedAfter() == null) {
            log.warn("业绩匹配缺乏有效约束（行业关键词/项目类型/签约时间均未提供或被过滤），禁止按全量存在性满分: request={}", request);
            return KnowledgeMatchResult.empty("未抽取到可执行的业绩匹配约束（行业/类型/签约时间），禁止全表匹配，待人工确认");
        }

        Specification<PerformanceRecordEntity> spec = specificationOf(enumHits, textKeywords, request.signedAfter());
        List<ProjectMatchedItem> matched = repository.findAll(spec).stream()
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

    private static final java.util.Set<String> GENERIC_PROJECT_KEYWORDS = java.util.Set.of(
            "业绩", "供货", "类似", "采购", "服务", "数量", "评价", "排序", "规模",
            "合同", "案例", "时间", "要求", "项目", "情况",
            "总体", "注册", "协议", "单位", "提供", "评审", "应答人", "承诺"
    );

    static List<String> filterSpecificProjectKeywords(List<String> rawKeywords) {
        if (rawKeywords == null || rawKeywords.isEmpty()) {
            return List.of();
        }
        return rawKeywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(k -> k.length() <= 8)
                .filter(k -> !GENERIC_PROJECT_KEYWORDS.contains(k))
                .filter(k -> !k.matches(".*(?:得|扣|满分)\\s*\\d+(?:\\.\\d+)?\\s*分.*")
                        && !k.matches(".*\\d+(?:\\.\\d+)?\\s*分(?!钟).*"))
                .filter(k -> !k.matches("(?i)^(优秀|良好|一般|合格|满分|分值|排序|评价|评审|评分|情况|要求|万元|亿元|以上|以下)$"))
                .toList();
    }

    private Specification<PerformanceRecordEntity> specificationOf(
            List<ProjectType> enumHits, List<String> textKeywords, LocalDate signedAfter) {
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
            if (signedAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("signingDate"), signedAfter));
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
