package com.xiyu.bid.businessqualification.infrastructure.persistence;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.model.QualificationPage;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationAttachmentEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BusinessQualificationRepositoryAdapter implements BusinessQualificationRepository {

    private final BusinessQualificationJpaRepository qualificationJpaRepository;
    private final QualificationAttachmentJpaRepository attachmentJpaRepository;

    @Override
    public BusinessQualification save(BusinessQualification qualification) {
        BusinessQualificationEntity savedEntity = qualificationJpaRepository.save(toEntity(qualification));

        attachmentJpaRepository.deleteByQualificationId(savedEntity.getId());
        List<QualificationAttachmentEntity> attachments = qualification.attachments().stream()
                .map(attachment -> QualificationAttachmentEntity.builder()
                        .qualificationId(savedEntity.getId())
                        .fileName(attachment.getFileName())
                        .fileUrl(attachment.getFileUrl())
                        .uploadedAt(attachment.getUploadedAt())
                        .build())
                .toList();
        attachmentJpaRepository.saveAll(attachments);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<BusinessQualification> findById(Long id) {
        return qualificationJpaRepository.findById(id).map(this::toDomain);
    }

    /**
     * CO-155 fix: paginated query. SQL layer does filter+count+sort, then
     * maps Spring's Page into the domain-layer QualificationPage record so
     * the port boundary stays free of framework types
     * (enforced by FPJavaArchitectureTest).
     */
    @Override
    public QualificationPage<BusinessQualification> findAll(QualificationListCriteria criteria, int page, int size) {
        org.springframework.data.domain.Pageable springPageable =
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "id"));
        org.springframework.data.domain.Page<BusinessQualificationEntity> springPage =
                qualificationJpaRepository.findAll(toSpecification(criteria), springPageable);
        return new QualificationPage<>(
                springPage.map(this::toDomain).getContent(),
                springPage.getTotalElements(),
                springPage.getNumber(),
                springPage.getSize()
        );
    }

    /**
     * Backward-compatible query without Pageable (used by export/statistics).
     */
    @Override
    public List<BusinessQualification> findAll(QualificationListCriteria criteria) {
        return qualificationJpaRepository.findAll(toSpecification(criteria)).stream()
                .map(this::toDomain)
                .filter(item -> matchesDerived(item, criteria))
                .sorted(Comparator.comparing(BusinessQualification::id, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<BusinessQualification> findExpiringWithinDays(int days) {
        return qualificationJpaRepository.findByExpiryDateLessThanEqual(LocalDate.now().plusDays(days)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        attachmentJpaRepository.deleteByQualificationId(id);
        qualificationJpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return qualificationJpaRepository.existsById(id);
    }

    @Override
    public boolean existsByCertificateNo(String certificateNo) {
        return qualificationJpaRepository.existsByCertificateNo(certificateNo);
    }

    @Override
    public List<String> findAllLevels() {
        return qualificationJpaRepository.findDistinctLevelByLevelIsNotNull().stream()
                .sorted()
                .toList();
    }

    /**
     * Push down filterable criteria to SQL via Specification.
     * Derived status (valid/expiring/expired from validity period) is left to matchesDerived.
     */
    private Specification<BusinessQualificationEntity> toSpecification(QualificationListCriteria criteria) {
        return (root, query, cb) -> {
            if (criteria == null) return cb.conjunction();
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.getSubjectType() != null && !criteria.getSubjectType().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("subjectType")), criteria.getSubjectType().toUpperCase(Locale.ROOT)));
            }
            if (criteria.getSubjectName() != null && !criteria.getSubjectName().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("subjectName")), "%" + escapeSqlWildcards(criteria.getSubjectName().toLowerCase(Locale.ROOT)) + "%"));
            }
            if (criteria.getCategory() != null && !criteria.getCategory().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("category")), criteria.getCategory().toUpperCase(Locale.ROOT)));
}
            if (criteria.getLevel() != null && !criteria.getLevel().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("level")), criteria.getLevel().toUpperCase(Locale.ROOT)));
            }
            if (criteria.getExpiringFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expiryDate"), criteria.getExpiringFrom()));
            }
            if (criteria.getExpiringTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expiryDate"), criteria.getExpiringTo()));
            }
            if (criteria.getIssuer() != null && !criteria.getIssuer().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("issuer")), "%" + escapeSqlWildcards(criteria.getIssuer().toLowerCase(Locale.ROOT)) + "%"));
            }
            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                String kw = "%" + escapeSqlWildcards(criteria.getKeyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), kw),
                        cb.like(cb.lower(cb.coalesce(root.get("certificateNo"), "")), kw),
                        cb.like(cb.lower(cb.coalesce(root.get("issuer"), "")), kw),
                        cb.like(cb.lower(cb.coalesce(root.get("holderName"), "")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Escape SQL wildcards (% and _) to prevent injection in LIKE queries.
     */
    private String escapeSqlWildcards(String input) {
        if (input == null) return null;
        return input.replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * SQL-cannot-express derived status filter (status=valid/expiring/expired is computed live by validity period).
     * NOTE: pagination path does not use this method (frontend statuses are uppercase enum names IN_STOCK/EXPIRING/EXPIRED/RETIRED
     * which map to DB status column string, not derived values -- the CO-155 calibrated reality).
     * Kept for backward compatibility with findAll(criteria) old callers.
     */
    private boolean matchesDerived(BusinessQualification item, QualificationListCriteria criteria) {
        if (criteria == null) {
            return true;
        }
        if (criteria.getStatus() != null && !criteria.getStatus().isEmpty()
                && (item.status() == null || criteria.getStatus().stream().noneMatch(s -> statusMatches(item.status(), s)))) {
            return false;
        }
        if (criteria.getExpiringWithinDays() != null && item.remainingDays() > criteria.getExpiringWithinDays()) {
            return false;
        }
        return true;
    }

    /**
     * CO-155 fix: handle VALID/IN_STOCK equivalence for filter compatibility.
     */
    private boolean statusMatches(QualificationStatus status, String filter) {
        if (status == null || filter == null) return false;
        String statusName = status.name();
        // VALID (deprecated) should match IN_STOCK filter
        if (status == QualificationStatus.IN_STOCK && "IN_STOCK".equalsIgnoreCase(filter)) return true;
        return statusName.equalsIgnoreCase(filter);
    }

    private BusinessQualificationEntity toEntity(BusinessQualification qualification) {
        return BusinessQualificationEntity.builder()
                .id(qualification.id())
                .name(qualification.name())
                .level(qualification.level())
                .subjectType(qualification.subject().getType())
                .subjectName(qualification.subject().getName())
                .category(qualification.category())
                .certificateNo(qualification.certificateNo())
                .issuer(qualification.issuer())
                .agency(qualification.agency())
                .agencyContact(qualification.agencyContact())
                .certScope(qualification.certScope())
                .certReviewNote(qualification.certReviewNote())
                .holderName(qualification.holderName())
                .issueDate(qualification.validityPeriod().getIssueDate())
                .expiryDate(qualification.validityPeriod().getExpiryDate())
                .status(qualification.status())
                .reminderEnabled(qualification.reminderPolicy().isEnabled())
                .reminderDays(qualification.reminderPolicy().getReminderDays())
                .lastRemindedAt(qualification.reminderPolicy().getLastRemindedAt())
                .fileUrl(qualification.fileUrl())
                .retireReason(qualification.retireReason())
                .retired(qualification.retired())
                .build();
    }

    private BusinessQualification toDomain(BusinessQualificationEntity entity) {
        return BusinessQualification.createWithRetired(
                entity.getId(),
                entity.getName(),
                entity.getLevel(),
                QualificationSubject.of(entity.getSubjectType(), entity.getSubjectName()),
                entity.getCategory(),
                entity.getCertificateNo(),
                entity.getIssuer(),
                entity.getAgency(),
                entity.getAgencyContact(),
                entity.getCertScope(),
                entity.getCertReviewNote(),
                entity.getHolderName(),
                new ValidityPeriod(entity.getIssueDate(), entity.getExpiryDate()),
                new ReminderPolicy(
                        entity.isReminderEnabled(),
                        entity.getReminderDays(),
                        entity.getLastRemindedAt()
                ),
                entity.getFileUrl(),
                entity.getRetireReason(),
                entity.isRetired(),
                attachmentJpaRepository.findByQualificationIdOrderByUploadedAtDesc(entity.getId()).stream()
                        .map(this::toDomainAttachment)
                        .toList()
        );
    }

    private QualificationAttachment toDomainAttachment(QualificationAttachmentEntity entity) {
        return new QualificationAttachment(
                entity.getId(),
                entity.getFileName(),
                entity.getFileUrl(),
                entity.getUploadedAt()
        );
    }
}
