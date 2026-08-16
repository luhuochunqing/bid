package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchRequest;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 仓库匹配（POST /api/knowledge/warehouse/match）。
 *
 * <p>规则：status IN (IN_USE, EXPIRING) AND 名称/区域/面积条件
 * AND（facilityKeywords 空 OR remarks 包含——设施降级匹配，matchDetail 注明）。
 * 设施降级语义在 Java 侧守卫，保证可测。
 */
@Service
@RequiredArgsConstructor
public class WarehouseMatchService {

    private final WarehouseRepository repository;

    public KnowledgeMatchResult match(WarehouseMatchRequest request) {
        boolean degraded = request.facilityKeywords() != null
                && request.facilityKeywords().stream().anyMatch(StringUtils::hasText);
        List<WarehouseMatchedItem> matched = repository.findAll(specificationOf(request)).stream()
                .filter(row -> !degraded || facilityHit(row.getRemarks(), request.facilityKeywords()))
                .map(row -> new WarehouseMatchedItem(row.getId(), row.getName(), row.getRegion(),
                        row.getArea(), row.getStatus() == null ? null : row.getStatus().name()))
                .toList();
        MatchTierPolicy.Outcome outcome = MatchTierPolicy.evaluate(
                matched.size(), null, false, degraded);
        return new KnowledgeMatchResult(outcome.tier(), outcome.matchRatio(), matched,
                buildDetail(matched.size(), degraded));
    }

    private Specification<WarehouseEntity> specificationOf(WarehouseMatchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(WarehouseStatus.IN_USE, WarehouseStatus.EXPIRING));
            if (request.nameKeywords() != null && !request.nameKeywords().isEmpty()) {
                List<Predicate> likes = request.nameKeywords().stream()
                        .filter(StringUtils::hasText)
                        .map(keyword -> cb.like(root.get("name"), "%" + keyword.trim() + "%"))
                        .toList();
                predicates.add(cb.or(likes.toArray(new Predicate[0])));
            }
            if (StringUtils.hasText(request.region())) {
                predicates.add(cb.equal(root.get("region"), request.region().trim()));
            }
            if (request.minArea() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("area"), request.minArea()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean facilityHit(String remarks, List<String> facilityKeywords) {
        if (!StringUtils.hasText(remarks)) {
            return false;
        }
        return facilityKeywords.stream()
                .filter(StringUtils::hasText)
                .anyMatch(keyword -> remarks.contains(keyword.trim()));
    }

    private static String buildDetail(int matchedCount, boolean degraded) {
        if (matchedCount == 0) {
            return "未命中符合条件的仓库";
        }
        return degraded
                ? "命中 " + matchedCount + " 个仓库（设施关键词基于备注文本匹配——降级匹配）"
                : "命中 " + matchedCount + " 个仓库";
    }
}
