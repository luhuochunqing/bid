package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.port.CaseSearchPort;
import com.xiyu.bid.casework.domain.model.CaseSearchCriteria;
import com.xiyu.bid.casework.domain.model.CaseSearchOptions;
import com.xiyu.bid.casework.dto.CaseRecommendationDTO;
import com.xiyu.bid.casework.dto.CaseSearchOptionsDTO;
import com.xiyu.bid.casework.dto.CaseSearchResultDTO;
import com.xiyu.bid.casework.infrastructure.persistence.CaseMapper;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaseSearchAppService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int RELATED_POOL_SIZE = 200;

    private final CaseRepository caseRepository;
    private final CaseSearchPort caseSearchPort;
    private final CaseMapper caseMapper;

    public CaseSearchResultDTO search(CaseSearchCriteria criteria) {
        CaseSearchCriteria normalizedCriteria = normalizeCriteria(criteria);
        int page = normalizedCriteria.page() <= 0 ? DEFAULT_PAGE : normalizedCriteria.page();
        int pageSize = normalizedCriteria.pageSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(normalizedCriteria.pageSize(), 100);
        String sort = normalizedCriteria.sort() == null ? "latest" : normalizedCriteria.sort();
        var pageable = PageRequest.of(page - 1, pageSize, caseSearchPort.resolveSort(sort));
        var resultPage = caseSearchPort.search(normalizedCriteria, pageable);

        return CaseSearchResultDTO.builder()
                .items(resultPage.stream().map(caseMapper::toDTO).toList())
                .total(resultPage.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .totalPages(resultPage.getTotalPages())
                .sort(sort)
                .build();
    }

    public CaseSearchOptions getSearchOptions() {
        return new CaseSearchOptions(
                Arrays.stream(Case.Industry.values()).map(Enum::name).toList(),
                Arrays.stream(Case.Outcome.values()).map(Enum::name).toList(),
                sortedDistinct(caseSearchPort.findDistinctStatuses()),
                sortedDistinct(caseSearchPort.findDistinctVisibilities()),
                sortedDistinct(caseSearchPort.findDistinctProductLines()),
                sortedDistinct(caseSearchPort.findDistinctTags()),
                List.of("latest", "popular", "amountDesc", "amountAsc", "oldest"));
    }

    public CaseSearchOptionsDTO getSearchOptionsDTO() {
        CaseSearchOptions options = getSearchOptions();
        return CaseSearchOptionsDTO.builder()
                .industries(options.industries())
                .outcomes(options.outcomes())
                .statuses(options.statuses())
                .visibilities(options.visibilities())
                .productLines(options.productLines())
                .tags(options.tags())
                .sortOptions(options.sortOptions())
                .build();
    }

    public List<CaseRecommendationDTO> getRelatedCases(Long caseId, int limit) {
        Case current = caseRepository.findById(caseId).orElse(null);
        if (current == null) {
            return List.of();
        }
        int actualLimit = limit <= 0 ? 5 : Math.min(limit, 20);
        List<Case> candidates = caseSearchPort.findRelatedCandidates(
                caseId,
                PageRequest.of(0, RELATED_POOL_SIZE, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));

        return candidates.stream()
                .map(candidate -> scoreRelatedCase(current, candidate))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(CaseRecommendation::score).reversed()
                        .thenComparing(item -> item.caseData().getPublishedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(actualLimit)
                .map(this::toDTO)
                .toList();
    }

    private CaseRecommendation scoreRelatedCase(Case current, Case candidate) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (current.getSourceProjectId() != null && current.getSourceProjectId().equals(candidate.getSourceProjectId())) {
            score += 100;
            reasons.add("源项目一致");
        }
        if (hasText(current.getProductLine()) && current.getProductLine().equalsIgnoreCase(candidate.getProductLine())) {
            score += 50;
            reasons.add("产品线一致");
        }
        if (current.getIndustry() != null && current.getIndustry() == candidate.getIndustry()) {
            score += 25;
            reasons.add("行业一致");
        }
        if (current.getOutcome() != null && current.getOutcome() == candidate.getOutcome()) {
            score += 10;
            reasons.add("结果一致");
        }
        if (hasText(current.getCustomerName()) && current.getCustomerName().equalsIgnoreCase(candidate.getCustomerName())) {
            score += 20;
            reasons.add("客户一致");
        }

        int tagMatches = sharedCount(current.getTags(), candidate.getTags());
        if (tagMatches > 0) {
            score += tagMatches * 10;
            reasons.add("共享" + tagMatches + "个标签");
        }

        int technologyMatches = sharedCount(current.getTechnologies(), candidate.getTechnologies());
        if (technologyMatches > 0) {
            score += technologyMatches * 5;
            reasons.add("共享" + technologyMatches + "个技术栈");
        }

        if (hasText(current.getSearchDocument()) && hasText(candidate.getSearchDocument())) {
            Set<String> currentTokens = tokenSet(current.getSearchDocument());
            Set<String> candidateTokens = tokenSet(candidate.getSearchDocument());
            currentTokens.retainAll(candidateTokens);
            if (!currentTokens.isEmpty()) {
                score += Math.min(currentTokens.size() * 2, 20);
                reasons.add("正文关键词重合");
            }
        }

        return new CaseRecommendation(
                candidate,
                score,
                reasons.isEmpty() ? "基础相似" : String.join("、", reasons)
        );
    }

    private CaseRecommendationDTO toDTO(CaseRecommendation recommendation) {
        return CaseRecommendationDTO.builder()
                .caseData(caseMapper.toDTO(recommendation.caseData()))
                .score(recommendation.score())
                .reason(recommendation.reason())
                .build();
    }

    private List<String> sortedDistinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String normalize(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int sharedCount(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> leftSet = left.stream().filter(this::hasText).map(this::normalizeToken).collect(Collectors.toSet());
        Set<String> rightSet = right.stream().filter(this::hasText).map(this::normalizeToken).collect(Collectors.toSet());
        leftSet.retainAll(rightSet);
        return leftSet.size();
    }

    private Set<String> tokenSet(String text) {
        Set<String> tokens = new HashSet<>();
        for (String token : text.split("\\s+")) {
            String normalized = normalizeToken(token);
            if (!normalized.isBlank()) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private CaseSearchCriteria normalizeCriteria(CaseSearchCriteria criteria) {
        if (criteria == null) {
            return new CaseSearchCriteria(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    DEFAULT_PAGE,
                    DEFAULT_PAGE_SIZE,
                    "latest");
        }
        return new CaseSearchCriteria(
                normalize(criteria.keyword()),
                normalize(criteria.industry()),
                normalize(criteria.productLine()),
                normalize(criteria.outcome()),
                criteria.year(),
                criteria.amountMin(),
                criteria.amountMax(),
                criteria.tags() == null ? List.of() : criteria.tags().stream().map(this::normalize).filter(this::hasText).toList(),
                normalize(criteria.status()),
                normalize(criteria.visibility()),
                criteria.page(),
                criteria.pageSize(),
                normalize(criteria.sort()));
    }

    private record CaseRecommendation(Case caseData, int score, String reason) {
    }
}
