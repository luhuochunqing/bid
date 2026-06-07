package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.command.OaCallbackCommand;
import com.xiyu.bid.workflowform.application.port.QualificationBorrowApplyCommand;
import com.xiyu.bid.workflowform.application.port.QualificationBorrowApplyPort;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceRecord;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.OaApprovalStatus;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowFormOaResultServiceTest {

    @Test
    void approved_callback_applies_qualification_borrow_once() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        Long id = store.create(FormBusinessType.QUALIFICATION_BORROW, "QUALIFICATION_BORROW", 10L, "小王", values());
        store.markOaApproving(id, "OA-1");
        CapturingQualificationBorrowApplyPort applyPort = new CapturingQualificationBorrowApplyPort();
        WorkflowFormOaResultService service = new WorkflowFormOaResultService(store, applyPort, new NoopWorkflowFormAccessGuard());

        service.handleCallback(new OaCallbackCommand("OA-1", OaApprovalStatus.APPROVED, "经理", "同意", "evt-1"));
        service.handleCallback(new OaCallbackCommand("OA-1", OaApprovalStatus.APPROVED, "经理", "重复回调", "evt-1"));

        assertThat(applyPort.calls).isEqualTo(1);
        assertThat(applyPort.lastCommand.qualificationId()).isEqualTo(1001L);
        assertThat(store.findById(id).orElseThrow().status()).isEqualTo(WorkflowFormStatus.BUSINESS_APPLIED);
    }

    @Test
    void rejected_callback_does_not_apply_qualification_borrow() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        Long id = store.create(FormBusinessType.QUALIFICATION_BORROW, "QUALIFICATION_BORROW", 10L, "小王", values());
        store.markOaApproving(id, "OA-1");
        CapturingQualificationBorrowApplyPort applyPort = new CapturingQualificationBorrowApplyPort();
        WorkflowFormOaResultService service = new WorkflowFormOaResultService(store, applyPort, new NoopWorkflowFormAccessGuard());

        service.handleCallback(new OaCallbackCommand("OA-1", OaApprovalStatus.REJECTED, "经理", "不同意", "evt-2"));

        assertThat(applyPort.calls).isZero();
        assertThat(store.findById(id).orElseThrow().status()).isEqualTo(WorkflowFormStatus.OA_REJECTED);
    }

    @Test
    void approved_callback_after_rejection_does_not_apply_business() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        Long id = store.create(FormBusinessType.QUALIFICATION_BORROW, "QUALIFICATION_BORROW", 10L, "小王", values());
        store.markOaApproving(id, "OA-1");
        CapturingQualificationBorrowApplyPort applyPort = new CapturingQualificationBorrowApplyPort();
        WorkflowFormOaResultService service = new WorkflowFormOaResultService(store, applyPort, new NoopWorkflowFormAccessGuard());

        service.handleCallback(new OaCallbackCommand("OA-1", OaApprovalStatus.REJECTED, "经理", "不同意", "evt-3"));
        service.handleCallback(new OaCallbackCommand("OA-1", OaApprovalStatus.APPROVED, "经理", "后续重复推送", "evt-4"));

        assertThat(applyPort.calls).isZero();
        assertThat(store.findById(id).orElseThrow().status()).isEqualTo(WorkflowFormStatus.OA_REJECTED);
    }

    @Test
    void business_apply_failure_keeps_oa_approved_and_records_retry_reason() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        Long id = store.create(FormBusinessType.QUALIFICATION_BORROW, "QUALIFICATION_BORROW", 10L, "小王", values());
        store.markOaApproving(id, "OA-1");
        CapturingQualificationBorrowApplyPort applyPort = new CapturingQualificationBorrowApplyPort();
        applyPort.failMessage = "借阅服务暂不可用";
        WorkflowFormOaResultService service = new WorkflowFormOaResultService(store, applyPort, new NoopWorkflowFormAccessGuard());

        service.handleCallback(new OaCallbackCommand("OA-1", OaApprovalStatus.APPROVED, "经理", "同意", "evt-5"));

        WorkflowFormInstanceRecord record = store.findById(id).orElseThrow();
        assertThat(record.status()).isEqualTo(WorkflowFormStatus.OA_APPROVED);
        assertThat(record.businessApplied()).isFalse();
        assertThat(record.businessApplyError()).isEqualTo("借阅服务暂不可用");
    }

    private static Map<String, Object> values() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("qualificationId", "1001");
        values.put("borrower", "小王");
        values.put("department", "投标管理部");
        values.put("projectId", "10");
        values.put("purpose", "用于投标文件编制");
        values.put("expectedReturnDate", "2026-05-10");
        values.put("remark", "请审批");
        return values;
    }

    static class CapturingQualificationBorrowApplyPort implements QualificationBorrowApplyPort {
        int calls;
        QualificationBorrowApplyCommand lastCommand;
        String failMessage;

        @Override
        public void apply(QualificationBorrowApplyCommand command) {
            calls++;
            lastCommand = command;
            if (failMessage != null) {
                throw new IllegalStateException(failMessage);
            }
        }
    }
}
