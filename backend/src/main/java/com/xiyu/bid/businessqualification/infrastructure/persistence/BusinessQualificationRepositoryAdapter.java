package com.xiyu.bid.businessqualification.infrastructure.persistence;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationAttachmentEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

    @Override
    public List<BusinessQualification> findAll(QualificationListCriteria criteria) {
        return qualificationJpaRepository.findAll().stream()
                .map(this::toDomain)
                .filter(item -> matches(item, criteria))
                .sorted(Comparator.comparing(BusinessQualification::id).reversed())
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

    private boolean matches(BusinessQualification item, QualificationListCriteria criteria) {
        if (criteria == null) {
            return true;
        }
        if (criteria.getSubjectType() != null && !item.subject().getType().name().equalsIgnoreCase(criteria.getSubjectType())) {
            return false;
        }
        if (criteria.getSubjectName() != null && !contains(item.subject().getName(), criteria.getSubjectName())) {
            return false;
        }
        if (criteria.getCategory() != null && !item.category().name().equalsIgnoreCase(criteria.getCategory())) {
            return false;
        }
        if (criteria.getStatus() != null && !item.status().name().equalsIgnoreCase(criteria.getStatus())) {
            return false;
        }
        if (criteria.getBorrowStatus() != null && !item.currentBorrowStatus().name().equalsIgnoreCase(criteria.getBorrowStatus())) {
            return false;
        }
        if (criteria.getExpiringWithinDays() != null && item.remainingDays() > criteria.getExpiringWithinDays()) {
            return false;
        }
        if (criteria.getKeyword() != null) {
            return contains(item.name(), criteria.getKeyword())
                    || contains(item.certificateNo(), criteria.getKeyword())
                    || contains(item.issuer(), criteria.getKeyword())
                    || contains(item.holderName(), criteria.getKeyword());
        }
        return true;
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private BusinessQualificationEntity toEntity(BusinessQualification qualification) {
        return BusinessQualificationEntity.builder()
                .id(qualification.id())
                .name(qualification.name())
                .subjectType(qualification.subject().getType())
                .subjectName(qualification.subject().getName())
                .category(qualification.category())
                .certificateNo(qualification.certificateNo())
                .issuer(qualification.issuer())
                .holderName(qualification.holderName())
                .issueDate(qualification.validityPeriod().getIssueDate())
                .expiryDate(qualification.validityPeriod().getExpiryDate())
                .status(qualification.status())
                .reminderEnabled(qualification.reminderPolicy().isEnabled())
                .reminderDays(qualification.reminderPolicy().getReminderDays())
                .lastRemindedAt(qualification.reminderPolicy().getLastRemindedAt())
                .currentBorrowStatus(qualification.currentBorrowStatus())
                .currentBorrower(qualification.currentBorrower())
                .currentDepartment(qualification.currentDepartment())
                .currentProjectId(qualification.currentProjectId())
                .borrowPurpose(qualification.borrowPurpose())
                .expectedReturnDate(qualification.expectedReturnDate())
                .fileUrl(qualification.fileUrl())
                .build();
    }

    private BusinessQualification toDomain(BusinessQualificationEntity entity) {
        return BusinessQualification.create(
                entity.getId(),
                entity.getName(),
                QualificationSubject.of(entity.getSubjectType(), entity.getSubjectName()),
                entity.getCategory(),
                entity.getCertificateNo(),
                entity.getIssuer(),
                entity.getHolderName(),
                new ValidityPeriod(entity.getIssueDate(), entity.getExpiryDate()),
                new ReminderPolicy(
                        entity.isReminderEnabled(),
                        entity.getReminderDays(),
                        entity.getLastRemindedAt()
                ),
                entity.getCurrentBorrowStatus(),
                entity.getCurrentBorrower(),
                entity.getCurrentDepartment(),
                entity.getCurrentProjectId(),
                entity.getBorrowPurpose(),
                entity.getExpectedReturnDate(),
                entity.getFileUrl(),
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
