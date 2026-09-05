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

import java.math.BigDecimal;
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
@lombok.extern.slf4j.Slf4j
public class WarehouseMatchService {

    private final WarehouseRepository repository;

    public KnowledgeMatchResult match(WarehouseMatchRequest request) {
        if (request == null) {
            return KnowledgeMatchResult.empty("请求参数为空");
        }
        List<String> cleanKeywords = filterSpecificWarehouseKeywords(request.nameKeywords());
        boolean hasRegion = StringUtils.hasText(request.region());
        boolean hasMinArea = request.minArea() != null;
        boolean degraded = request.facilityKeywords() != null
                && request.facilityKeywords().stream().anyMatch(StringUtils::hasText);

        // 关键门禁：若没有任何可执行的业务过滤约束，严禁按在用仓存在性全量兜底导致假阳性满分！
        if (cleanKeywords.isEmpty() && !hasRegion && !hasMinArea) {
            log.warn("仓库匹配缺乏有效约束（名称关键词/地区/面积门槛均未提供或被过滤），禁止按在用仓存在性全量兜底: request={}", request);
            return KnowledgeMatchResult.empty("未抽取到可执行的仓库匹配约束（名称关键词/地区/面积），禁止按存在性全量匹配，待人工确认");
        }

        List<WarehouseMatchedItem> matched = repository.findAll(specificationOf(cleanKeywords, request.region(), request.minArea())).stream()
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
            "满足", "条件"
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
                .filter(k -> !k.matches(".*(?:得|扣|满分)\\s*\\d+(?:\\.\\d+)?\\s*分.*")
                        && !k.matches(".*\\d+(?:\\.\\d+)?\\s*分(?!钟).*"))
                .filter(k -> !k.matches("(?i)^(优秀|良好|一般|合格|满分|分值|排序|评价|评审|评分|情况|要求|万元|亿元|以上|以下)$"))
                .toList();
    }

    private Specification<WarehouseEntity> specificationOf(
            List<String> cleanKeywords, String region, BigDecimal minArea) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(WarehouseStatus.IN_USE, WarehouseStatus.EXPIRING));
            if (!cleanKeywords.isEmpty()) {
                List<Predicate> likes = cleanKeywords.stream()
                        .map(keyword -> cb.like(root.get("name"), "%" + keyword + "%"))
                        .toList();
                predicates.add(cb.or(likes.toArray(new Predicate[0])));
            }
            if (StringUtils.hasText(region)) {
                predicates.add(cb.equal(root.get("region"), region.trim()));
            }
            if (minArea != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("area"), minArea));
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
