package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.dto.CertMatchRequest;
import com.xiyu.bid.scoreparse.dto.CertMatchedItem;
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
 * 资质证书匹配（POST /api/knowledge/cert/match，FR-009）。
 *
 * <p>规则：name 包含任一关键词 AND（requiredLevel 空 OR level 包含）
 * AND（requireValidUntil 空 OR expiry_date ≥ 该日期 AND status != RETIRED）。
 * 命中行 status=EXPIRED 或 expiry_date < 今天 → expired=true（算命中但标记）。
 */
@Service
@RequiredArgsConstructor
public class CertMatchService {

    private final BusinessQualificationJpaRepository repository;

    public KnowledgeMatchResult match(CertMatchRequest request) {
        LocalDate today = LocalDate.now();
        LocalDate validCheckDate = request.requireValidUntil() != null ? request.requireValidUntil() : today;
        List<CertMatchedItem> matched = repository.findAll(specificationOf(request)).stream()
                .map(row -> new CertMatchedItem(row.getId(), row.getName(), row.getLevel(),
                        row.getExpiryDate(), isExpired(row, validCheckDate)))
                .toList();
        boolean anyExpired = matched.stream().anyMatch(CertMatchedItem::expired);
        MatchTierPolicy.Outcome outcome = MatchTierPolicy.evaluate(
                matched.size(), request.requiredCount(), anyExpired, false);
        return new KnowledgeMatchResult(outcome.tier(), outcome.matchRatio(), matched,
                buildDetail(matched.size(), anyExpired));
    }

    private Specification<BusinessQualificationEntity> specificationOf(CertMatchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (request.certNameKeywords() != null && !request.certNameKeywords().isEmpty()) {
                predicates.add(likeAny(cb, root.get("name"), request.certNameKeywords()));
            }
            if (StringUtils.hasText(request.requiredLevel())) {
                predicates.add(cb.like(root.get("level"), "%" + request.requiredLevel().trim() + "%"));
            }
            predicates.add(cb.notEqual(root.get("status"), QualificationStatus.RETIRED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isExpired(BusinessQualificationEntity row, LocalDate today) {
        return row.getStatus() == QualificationStatus.EXPIRED
                || (row.getExpiryDate() != null && row.getExpiryDate().isBefore(today));
    }

    private static String buildDetail(int matchedCount, boolean anyExpired) {
        if (matchedCount == 0) {
            return "未命中任何资质证书";
        }
        return anyExpired
                ? "命中 " + matchedCount + " 条资质，其中含过期证书（expired=true，需人工确认）"
                : "命中 " + matchedCount + " 条有效资质";
    }

    private static Predicate likeAny(jakarta.persistence.criteria.CriteriaBuilder cb,
                                     jakarta.persistence.criteria.Path<String> path,
                                     List<String> keywords) {
        List<Predicate> likes = keywords.stream()
                .filter(StringUtils::hasText)
                .map(keyword -> cb.like(path, "%" + keyword.trim() + "%"))
                .toList();
        return cb.or(likes.toArray(new Predicate[0]));
    }
}
