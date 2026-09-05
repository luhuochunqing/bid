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

    private static final java.util.Set<String> GENERIC_WAREHOUSE_KEYWORDS = java.util.Set.of(
            "仓库", "仓储", "自有", "租赁", "数量", "面积", "排序", "全国", "各地", "情况",
            "评分", "优秀", "良好", "一般", "满分", "分值", "根据", "应答人", "承诺", "要求",
            "物流", "配送", "网点", "设施", "满足", "条件"
    );

    static List<String> filterSpecificWarehouseKeywords(List<String> rawKeywords) {
        if (rawKeywords == null || rawKeywords.isEmpty()) {
            return List.of();
        }
        return rawKeywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(k -> k.length() <= 8)
                .filter(k -> !GENERIC_WAREHOUSE_KEYWORDS.contains(k))
                .filter(k -> !k.matches(".*(\\d+分|排序|评分|评价|评审|优秀|良好|一般|万元|亿元|以上|以下|情况|要求).*"))
                .toList();
    }

    private Specification<WarehouseEntity> specificationOf(WarehouseMatchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(WarehouseStatus.IN_USE, WarehouseStatus.EXPIRING));
            List<String> cleanKeywords = filterSpecificWarehouseKeywords(request.nameKeywords());
            if (!cleanKeywords.isEmpty()) {
                List<Predicate> likes = cleanKeywords.stream()
                        .map(keyword -> cb.like(root.get("name"), "%" + keyword + "%"))
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
