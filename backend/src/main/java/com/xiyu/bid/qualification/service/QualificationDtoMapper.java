package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.application.command.QualificationBorrowCommand;
import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.application.command.QualificationReturnCommand;
import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.service.QualificationExpiryPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.qualification.dto.QualificationAttachmentDTO;
import com.xiyu.bid.qualification.dto.QualificationBorrowRecordDTO;
import com.xiyu.bid.qualification.dto.QualificationBorrowRequest;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.dto.QualificationOverviewDTO;
import com.xiyu.bid.qualification.dto.QualificationReturnRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class QualificationDtoMapper {

    private final QualificationExpiryPolicy expiryPolicy = new QualificationExpiryPolicy();

    public QualificationUpsertCommand toUpsertCommand(QualificationDTO dto) {
        QualificationSubjectType subjectType = dto.getSubjectType() != null
                ? dto.getSubjectType()
                : QualificationSubjectType.COMPANY;
        QualificationCategory category = dto.getCategory() != null
                ? dto.getCategory()
                : mapLegacyType(dto.getType());

        List<QualificationAttachment> attachments = dto.getAttachments() == null
                ? List.of(defaultAttachment(dto))
                : dto.getAttachments().stream().map(this::toAttachment).toList();

        return QualificationUpsertCommand.builder()
                .name(dto.getName())
                .subjectType(subjectType)
                .subjectName(dto.getSubjectName() == null || dto.getSubjectName().isBlank() ? "默认主体" : dto.getSubjectName())
                .category(category)
                .certificateNo(dto.getCertificateNo())
                .issuer(dto.getIssuer())
                .holderName(dto.getHolderName() == null ? dto.getHolder() : dto.getHolderName())
                .issueDate(dto.getIssueDate())
                .expiryDate(dto.getExpiryDate())
                .reminderEnabled(dto.getReminderEnabled())
                .reminderDays(dto.getReminderDays())
                .fileUrl(dto.getFileUrl())
                .attachments(attachments.stream().filter(item -> item.getFileUrl() != null && !item.getFileUrl().isBlank()).toList())
                .build();
    }

    public QualificationBorrowCommand toBorrowCommand(QualificationBorrowRequest request) {
        return QualificationBorrowCommand.builder()
                .borrower(request.getBorrower())
                .department(request.getDepartment())
                .projectId(request.getProjectId())
                .purpose(request.getPurpose())
                .expectedReturnDate(request.getExpectedReturnDate())
                .remark(request.getRemark())
                .build();
    }

    public QualificationReturnCommand toReturnCommand(QualificationReturnRequest request) {
        return QualificationReturnCommand.builder()
                .returnRemark(request == null ? null : request.getReturnRemark())
                .build();
    }

    public QualificationListCriteria toCriteria(
            String subjectType,
            String subjectName,
            String category,
            String status,
            String borrowStatus,
            Integer expiringWithinDays,
            String keyword
    ) {
        return QualificationListCriteria.builder()
                .subjectType(subjectType)
                .subjectName(subjectName)
                .category(category)
                .status(status)
                .borrowStatus(borrowStatus)
                .expiringWithinDays(expiringWithinDays)
                .keyword(keyword)
                .build();
    }

    public QualificationDTO toDto(BusinessQualification qualification) {
        return QualificationDTO.builder()
                .id(qualification.id())
                .name(qualification.name())
                .type(toLegacyType(qualification.category()))
                .level(Qualification.Level.OTHER)
                .subjectType(qualification.subject().getType())
                .subjectName(qualification.subject().getName())
                .category(qualification.category())
                .certificateNo(qualification.certificateNo())
                .issuer(qualification.issuer())
                .holderName(qualification.holderName())
                .holder(qualification.holderName())
                .issueDate(qualification.validityPeriod().getIssueDate())
                .expiryDate(qualification.validityPeriod().getExpiryDate())
                .status(qualification.status().name().toLowerCase())
                .remainingDays((int) qualification.remainingDays())
                .alertLevel(expiryPolicy.alertLevel(qualification.status()))
                .borrowed(qualification.currentBorrowStatus() == LoanStatus.BORROWED)
                .currentBorrowStatus(qualification.currentBorrowStatus().name().toLowerCase())
                .currentBorrower(qualification.currentBorrower())
                .currentBorrowDepartment(qualification.currentDepartment())
                .currentDepartment(qualification.currentDepartment())
                .currentProjectId(qualification.currentProjectId())
                .currentBorrowPurpose(qualification.borrowPurpose())
                .borrowPurpose(qualification.borrowPurpose())
                .currentExpectedReturnDate(qualification.expectedReturnDate())
                .expectedReturnDate(qualification.expectedReturnDate())
                .reminderEnabled(qualification.reminderPolicy().isEnabled())
                .reminderDays(qualification.reminderPolicy().getReminderDays())
                .fileUrl(qualification.fileUrl())
                .attachments(qualification.attachments().stream().map(this::toAttachmentDto).toList())
                .build();
    }

    public QualificationBorrowRecordDTO toBorrowRecordDto(QualificationLoan loan, BusinessQualification qualification) {
        return toBorrowRecordDto(loan, qualification.name());
    }

    public QualificationBorrowRecordDTO toBorrowRecordDto(QualificationLoan loan, String qualificationName) {
        return QualificationBorrowRecordDTO.builder()
                .id(loan.getId())
                .qualificationId(loan.getQualificationId())
                .qualificationName(qualificationName)
                .borrower(loan.getBorrower())
                .department(loan.getDepartment())
                .projectId(loan.getProjectId())
                .purpose(loan.getPurpose())
                .remark(loan.getRemark())
                .borrowedAt(formatTime(loan.getBorrowedAt()))
                .expectedReturnDate(loan.getExpectedReturnDate() == null ? null : loan.getExpectedReturnDate().toString())
                .returnedAt(formatTime(loan.getReturnedAt()))
                .returnRemark(loan.getReturnRemark())
                .status(loan.getStatus().name().toLowerCase())
                .build();
    }

    public QualificationOverviewDTO toOverview(List<QualificationDTO> items) {
        return QualificationOverviewDTO.builder()
                .total(items.size())
                .expiring(items.stream().filter(item -> "expiring".equals(item.getStatus())).count())
                .expired(items.stream().filter(item -> "expired".equals(item.getStatus())).count())
                .borrowed(items.stream().filter(item -> "borrowed".equals(item.getCurrentBorrowStatus())).count())
                .build();
    }

    private QualificationAttachment toAttachment(QualificationAttachmentDTO dto) {
        return new QualificationAttachment(
                dto.getId(),
                dto.getFileName(),
                dto.getFileUrl(),
                dto.getUploadedAt() == null ? LocalDateTime.now() : LocalDateTime.parse(dto.getUploadedAt())
        );
    }

    private QualificationAttachment defaultAttachment(QualificationDTO dto) {
        if (dto.getFileUrl() == null || dto.getFileUrl().isBlank()) {
            return new QualificationAttachment(null, null, null, null);
        }
        return new QualificationAttachment(
                null,
                dto.getName() == null ? "附件" : dto.getName() + ".pdf",
                dto.getFileUrl(),
                LocalDateTime.now()
        );
    }

    private QualificationAttachmentDTO toAttachmentDto(QualificationAttachment attachment) {
        return QualificationAttachmentDTO.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .uploadedAt(formatTime(attachment.getUploadedAt()))
                .build();
    }

    private QualificationCategory mapLegacyType(Qualification.Type type) {
        if (type == null) {
            return QualificationCategory.OTHER;
        }
        return switch (type) {
            case CONSTRUCTION -> QualificationCategory.LICENSE;
            case DESIGN -> QualificationCategory.PERSONNEL;
            case SERVICE -> QualificationCategory.PRODUCT;
            case OTHER -> QualificationCategory.OTHER;
        };
    }

    private Qualification.Type toLegacyType(QualificationCategory category) {
        return switch (category) {
            case LICENSE -> Qualification.Type.CONSTRUCTION;
            case PERSONNEL -> Qualification.Type.DESIGN;
            case PRODUCT -> Qualification.Type.SERVICE;
            case OTHER -> Qualification.Type.OTHER;
        };
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
