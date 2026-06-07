package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.command.OaCallbackCommand;
import com.xiyu.bid.workflowform.application.port.QualificationBorrowApplyCommand;
import com.xiyu.bid.workflowform.application.port.QualificationBorrowApplyPort;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceStore;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.OaApprovalStatus;
import com.xiyu.bid.workflowform.domain.OaResultApplicationPolicy;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowFormOaResultService {

    private final WorkflowFormInstanceStore store;
    private final QualificationBorrowApplyPort qualificationBorrowApplyPort;
    private final WorkflowFormAccessGuard accessGuard;

    @Transactional
    public void handleCallback(OaCallbackCommand command) {
        WorkflowFormInstanceRecord record = store.findByOaInstanceId(command.oaInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("未找到 OA 流程实例: " + command.oaInstanceId()));
        if (store.isEventProcessed(command.eventId())) {
            return;
        }
        store.recordEvent(record.id(), record.oaInstanceId(), command.eventId(), command.status().name(), command.comment());
        if (record.businessApplied() || record.status() == WorkflowFormStatus.BUSINESS_APPLIED || record.status() == WorkflowFormStatus.OA_REJECTED) {
            return;
        }
        if (record.status() != WorkflowFormStatus.OA_APPROVING) {
            return;
        }
        if (command.status() == OaApprovalStatus.REJECTED) {
            store.markOaRejected(record.id(), command.operatorName(), command.comment());
            return;
        }
        if (!OaResultApplicationPolicy.canApplyBusiness(command.status())) {
            return;
        }
        if (store.markOaApproved(record.id(), command.operatorName(), command.comment())) {
            applyBusiness(record);
        }
    }

    private void applyBusiness(WorkflowFormInstanceRecord record) {
        if (record.businessType() != FormBusinessType.QUALIFICATION_BORROW) {
            return;
        }
        accessGuard.assertCanAccessProject(record.projectId());
        try {
            qualificationBorrowApplyPort.apply(toQualificationBorrowCommand(record));
            store.markBusinessApplied(record.id());
        } catch (RuntimeException exception) {
            store.markBusinessApplyFailed(record.id(), exception.getMessage());
        }
    }

    private QualificationBorrowApplyCommand toQualificationBorrowCommand(WorkflowFormInstanceRecord record) {
        Map<String, Object> formData = record.formData();
        return new QualificationBorrowApplyCommand(
                Long.valueOf(String.valueOf(formData.get("qualificationId"))),
                stringValue(formData, "borrower"),
                stringValue(formData, "department"),
                record.projectId(),
                stringValue(formData, "purpose"),
                LocalDate.parse(String.valueOf(formData.get("expectedReturnDate"))),
                stringValue(formData, "remark")
        );
    }

    private String stringValue(Map<String, Object> formData, String key) {
        Object value = formData.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
