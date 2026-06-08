package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.service.QualificationValidationResult;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateQualificationAppService {

    private final BusinessQualificationRepository repository;

    @Transactional
    public BusinessQualification create(QualificationUpsertCommand command) {
        QualificationSubject subject = QualificationSubject.of(
                command.getSubjectType(),
                command.getSubjectName()
        );
        requireValid(subject.validate());

        BusinessQualification qualification = BusinessQualification.create(
                null,
                command.getName(),
                subject,
                command.getCategory(),
                command.getCertificateNo(),
                command.getIssuer(),
                command.getAgency(),
                command.getAgencyContact(),
                command.getCertScope(),
                command.getCertReviewNote(),
                command.getHolderName(),
                new ValidityPeriod(command.getIssueDate(), command.getExpiryDate()),
                new ReminderPolicy(
                        Boolean.TRUE.equals(command.getReminderEnabled()) || command.getReminderEnabled() == null,
                        command.getReminderDays() == null ? 30 : command.getReminderDays(),
                        null
                ),
                LoanStatus.AVAILABLE,
                null, null, null, null, null,
                command.getFileUrl(),
                command.getRetireReason(),
                command.getAttachments() == null ? List.of() : command.getAttachments()
        );

        return repository.save(qualification);
    }

    private void requireValid(QualificationValidationResult validationResult) {
        if (!validationResult.valid()) {
            throw new IllegalArgumentException(validationResult.message());
        }
    }
}
