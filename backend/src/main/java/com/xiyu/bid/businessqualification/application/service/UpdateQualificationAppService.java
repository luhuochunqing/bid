package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.service.QualificationValidationResult;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdateQualificationAppService {

    private final BusinessQualificationRepository repository;
    private final IAuditLogService auditLogService;

    @Transactional
    public BusinessQualification update(Long id, QualificationUpsertCommand command) {
        BusinessQualification existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessQualification", String.valueOf(id)));

        QualificationSubject subject = QualificationSubject.of(
                command.getSubjectType() == null ? existing.subject().getType() : command.getSubjectType(),
                command.getSubjectName() == null ? existing.subject().getName() : command.getSubjectName()
        );
        requireValid(subject.validate());

        List<QualificationAttachment> newAttachments = command.getAttachments() == null || command.getAttachments().isEmpty()
                ? existing.attachments()
                : command.getAttachments();

        BusinessQualification updated = BusinessQualification.create(
                existing.id(),
                command.getName() == null ? existing.name() : command.getName(),
                subject,
                command.getCategory() == null ? existing.category() : command.getCategory(),
                command.getCertificateNo() == null ? existing.certificateNo() : command.getCertificateNo(),
                command.getIssuer() == null ? existing.issuer() : command.getIssuer(),
                command.getAgency() == null ? existing.agency() : command.getAgency(),
                command.getAgencyContact() == null ? existing.agencyContact() : command.getAgencyContact(),
                command.getCertScope() == null ? existing.certScope() : command.getCertScope(),
                command.getCertReviewNote() == null ? existing.certReviewNote() : command.getCertReviewNote(),
                command.getHolderName() == null ? existing.holderName() : command.getHolderName(),
                new ValidityPeriod(
                        command.getIssueDate() == null ? existing.validityPeriod().getIssueDate() : command.getIssueDate(),
                        command.getExpiryDate() == null ? existing.validityPeriod().getExpiryDate() : command.getExpiryDate()
                ),
                new ReminderPolicy(
                        command.getReminderEnabled() == null ? existing.reminderPolicy().isEnabled() : command.getReminderEnabled(),
                        command.getReminderDays() == null ? existing.reminderPolicy().getReminderDays() : command.getReminderDays(),
                        existing.reminderPolicy().getLastRemindedAt()
                ),
                existing.currentBorrowStatus(),
                existing.currentBorrower(),
                existing.currentDepartment(),
                existing.currentProjectId(),
                existing.borrowPurpose(),
                existing.expectedReturnDate(),
                command.getFileUrl() == null ? existing.fileUrl() : command.getFileUrl(),
                newAttachments
        );

        recordAttachmentChangeAudit(existing, updated, command.getFileUrl(), command.getAttachments());

        return repository.save(updated);
    }

    private void recordAttachmentChangeAudit(BusinessQualification existing, BusinessQualification updated,
                                            String newFileUrl, List<QualificationAttachment> newAttachments) {
        String existingUrl = existing.fileUrl();
        String updatedUrl = updated.fileUrl();
        boolean urlChanged = (existingUrl == null && updatedUrl != null)
                || (existingUrl != null && !existingUrl.equals(updatedUrl));
        boolean attachmentsReplaced = newAttachments != null && !newAttachments.equals(existing.attachments());
        boolean anyChange = urlChanged || attachmentsReplaced;

        if (!anyChange) {
            return;
        }

        String action = detectChangeType(existingUrl, updatedUrl, newAttachments);
        String oldValue = buildOldValueDesc(existing);
        String newValue = buildNewValueDesc(updatedUrl, newAttachments);

        auditLogService.log(AuditLogService.AuditLogEntry.builder()
                .action("ATTACHMENT_CHANGE")
                .entityType("Qualification")
                .entityId(String.valueOf(existing.id()))
                .description(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .success(true)
                .build());
    }

    private String detectChangeType(String existingUrl, String updatedUrl, List<QualificationAttachment> newAttachments) {
        boolean urlAdded = existingUrl == null && updatedUrl != null;
        boolean urlRemoved = existingUrl != null && updatedUrl == null;
        if (urlRemoved || (newAttachments != null && newAttachments.stream().noneMatch(a -> a.getFileUrl() != null))) {
            return "删除资质证书附件";
        }
        if (urlAdded) {
            return "上传资质证书附件";
        }
        return "替换资质证书附件";
    }

    private String buildOldValueDesc(BusinessQualification existing) {
        if (existing.attachments() == null || existing.attachments().isEmpty()) {
            return existing.fileUrl() == null ? "(无附件)" : existing.fileUrl();
        }
        return existing.attachments().stream()
                .map(a -> a.getFileName() + " [" + a.getFileUrl() + "]")
                .reduce((a, b) -> a + "; " + b)
                .orElse("(无附件)");
    }

    private String buildNewValueDesc(String newFileUrl, List<QualificationAttachment> newAttachments) {
        if (newAttachments == null || newAttachments.isEmpty()) {
            return newFileUrl == null ? "(无附件)" : newFileUrl;
        }
        return newAttachments.stream()
                .map(a -> a.getFileName() + " [" + a.getFileUrl() + "]")
                .reduce((a, b) -> a + "; " + b)
                .orElse("(无附件)");
    }

    private void requireValid(QualificationValidationResult validationResult) {
        if (!validationResult.valid()) {
            throw new IllegalArgumentException(validationResult.message());
        }
    }
}
