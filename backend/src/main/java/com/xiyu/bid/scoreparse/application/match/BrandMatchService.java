package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.ManufacturerAuthorizationJpaRepository;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.dto.BrandMatchRequest;
import com.xiyu.bid.scoreparse.dto.BrandMatchedItem;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 品牌授权匹配（POST /api/knowledge/brand/match）。
 *
 * <p>规则：status IN (ACTIVE, EXPIRING_SOON) AND brand_name 命中
 * AND（productLine 空 OR =，解析失败忽略并注明）AND（importDomestic 空 OR =）
 * AND（requireValidUntil 空 OR auth_end_date ≥）。
 * 授权范围降级：productLine+importDomestic 近似表达，matchDetail 注明；
 * 命中含 expireSoon=true 标记（auth_end_date 在未来 90 天内）。
 */
@Service
@RequiredArgsConstructor
public class BrandMatchService {

    private static final long EXPIRE_SOON_WINDOW_DAYS = 90;

    private final ManufacturerAuthorizationJpaRepository repository;

    public KnowledgeMatchResult match(BrandMatchRequest request) {
        LocalDate today = LocalDate.now();
        ProductLine parsedProductLine = StringUtils.hasText(request.productLine())
                ? ProductLine.fromString(request.productLine().trim())
                : null;
        boolean scopeDegraded = parsedProductLine != null || StringUtils.hasText(request.importDomestic());
        List<BrandMatchedItem> matched = repository.findAll(
                        specificationOf(request, parsedProductLine)).stream()
                .map(row -> new BrandMatchedItem(row.getId(), row.getBrandName(),
                        row.getManufacturerName(),
                        row.getProductLine() == null ? null : row.getProductLine().name(),
                        row.getAuthEndDate(), isExpireSoon(row.getAuthEndDate(), today)))
                .toList();
        boolean anyExpireSoon = matched.stream().anyMatch(BrandMatchedItem::expireSoon);
        // 契约 §5：授权范围降级仅 matchDetail 注明，不影响 tier（区别于 warehouse 的降级降档）。
        MatchTierPolicy.Outcome outcome = MatchTierPolicy.evaluate(
                matched.size(), null, anyExpireSoon, false);
        return new KnowledgeMatchResult(outcome.tier(), outcome.matchRatio(), matched,
                buildDetail(matched.size(), scopeDegraded, anyExpireSoon, request.productLine()));
    }

    private Specification<ManufacturerAuthorizationEntity> specificationOf(
            BrandMatchRequest request, ProductLine parsedProductLine) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(AuthStatus.ACTIVE, AuthStatus.EXPIRING_SOON));
            if (request.brandNameKeywords() != null && !request.brandNameKeywords().isEmpty()) {
                List<Predicate> likes = request.brandNameKeywords().stream()
                        .filter(StringUtils::hasText)
                        .map(keyword -> cb.like(root.get("brandName"), "%" + keyword.trim() + "%"))
                        .toList();
                predicates.add(cb.or(likes.toArray(new Predicate[0])));
            }
            if (parsedProductLine != null) {
                predicates.add(cb.equal(root.get("productLine"), parsedProductLine));
            }
            if (StringUtils.hasText(request.importDomestic())) {
                predicates.add(cb.equal(root.get("importDomestic"), request.importDomestic().trim()));
            }
            if (request.requireValidUntil() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("authEndDate"), request.requireValidUntil()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 授权止期在今天起 90 天内（含今天，不含已过期）→ expireSoon。 */
    private static boolean isExpireSoon(LocalDate authEndDate, LocalDate today) {
        return authEndDate != null && !authEndDate.isBefore(today)
                && authEndDate.isBefore(today.plusDays(EXPIRE_SOON_WINDOW_DAYS));
    }

    private static String buildDetail(int matchedCount, boolean scopeDegraded,
                                      boolean anyExpireSoon, String rawProductLine) {
        if (matchedCount == 0) {
            return "未命中符合条件的品牌授权";
        }
        StringBuilder detail = new StringBuilder("命中 ").append(matchedCount).append(" 条品牌授权");
        if (scopeDegraded) {
            detail.append("（授权范围按产品线+进口/国产近似表达——降级匹配）");
        }
        if (anyExpireSoon) {
            detail.append("，含 90 天内即将到期的授权（expireSoon=true）");
        }
        if (StringUtils.hasText(rawProductLine) && ProductLine.fromString(rawProductLine.trim()) == null) {
            detail.append("；产品线 ").append(rawProductLine).append(" 无法识别已忽略");
        }
        return detail.toString();
    }
}
