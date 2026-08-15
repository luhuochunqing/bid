package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelCertificateEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelCertificateJpaRepository;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelJpaRepository;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.PersonMatchRequest;
import com.xiyu.bid.scoreparse.dto.PersonMatchedItem;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 人员匹配（POST /api/knowledge/person/match，FR-010）。
 *
 * <p>规则：status=ACTIVE AND technical_title 命中岗位关键词 AND 证书子表存在
 * 未删除（deleted_at IS NULL）、名称命中、有效期内记录。单人多证只计一次。
 * Certificate validity (soft delete/expiry/keyword) is finally guarded on the Java side, ensuring testability.
 */
@Service
@RequiredArgsConstructor
public class PersonMatchService {

    private final PersonnelJpaRepository personnelRepository;
    private final PersonnelCertificateJpaRepository certificateRepository;

    public KnowledgeMatchResult match(PersonMatchRequest request) {
        boolean hasPosition = hasText(request.positionKeywords());
        boolean hasCert = hasText(request.certNameKeywords());
        if (!hasPosition && !hasCert) {
            return KnowledgeMatchResult.empty("未提供岗位或证书关键词，无法匹配");
        }
        LocalDate today = LocalDate.now();
        List<PersonMatchedItem> matched = new ArrayList<>();
        for (PersonnelEntity person : personnelRepository.findAll(specificationOf(request, hasPosition, hasCert))) {
            List<String> hitCerts = hitCertificates(person.getId(), request.certNameKeywords(), today);
            if (hasCert && hitCerts.isEmpty()) {
                continue;
            }
            matched.add(new PersonMatchedItem(person.getId(), person.getName(),
                    person.getEmployeeNumber(), person.getTechnicalTitle(), hitCerts));
        }
        MatchTierPolicy.Outcome outcome = MatchTierPolicy.evaluate(
                matched.size(), request.requiredCount(), false, false);
        return new KnowledgeMatchResult(outcome.tier(), outcome.matchRatio(), matched,
                matched.isEmpty() ? "未命中符合条件的人员"
                        : "符合人数 " + matched.size() + "/" + effectiveRequired(request.requiredCount()));
    }

    private Specification<PersonnelEntity> specificationOf(PersonMatchRequest request,
                                                           boolean hasPosition, boolean hasCert) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), PersonnelStatus.ACTIVE));
            if (hasPosition) {
                predicates.add(likeAny(cb, root.get("technicalTitle"), request.positionKeywords()));
            }
            if (hasCert) {
                Subquery<Long> certExists = query.subquery(Long.class);
                Root<PersonnelCertificateEntity> certRoot = certExists.from(PersonnelCertificateEntity.class);
                certExists.select(certRoot.get("personnelId")).where(cb.and(
                        cb.equal(certRoot.get("personnelId"), root.get("id")),
                        cb.isNull(certRoot.get("deletedAt")),
                        likeAny(cb, certRoot.get("certificateName"), request.certNameKeywords())));
                predicates.add(cb.exists(certExists));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 有效命中证书名（软删除/过期/未命中关键词的证书剔除；expiry 为空视为长期有效）。 */
    private List<String> hitCertificates(Long personnelId, List<String> keywords, LocalDate today) {
        return certificateRepository.findByPersonnelId(personnelId).stream()
                .filter(cert -> cert.getDeletedAt() == null)
                .filter(cert -> cert.getExpiryDate() == null || !cert.getExpiryDate().isBefore(today))
                .filter(cert -> !hasText(keywords)
                        || containsAnyKeyword(cert.getCertificateName(), keywords))
                .map(PersonnelCertificateEntity::getCertificateName)
                .distinct()
                .toList();
    }

    private static int effectiveRequired(Integer requiredCount) {
        return (requiredCount == null || requiredCount <= 0) ? 1 : requiredCount;
    }

    private static boolean hasText(List<String> keywords) {
        return keywords != null && keywords.stream().anyMatch(StringUtils::hasText);
    }

    private static boolean containsAnyKeyword(String value, List<String> keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return keywords.stream()
                .filter(StringUtils::hasText)
                .anyMatch(keyword -> value.contains(keyword.trim()));
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
