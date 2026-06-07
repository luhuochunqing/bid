package com.xiyu.bid.workflowform.infrastructure.oa;

import com.xiyu.bid.workflowform.application.port.OaAttachmentPayload;
import com.xiyu.bid.workflowform.application.port.OaStartCommand;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockOaWorkflowGatewayTest {

    @Test
    void startProcess_captures_structured_attachment_payload() {
        MockOaWorkflowGateway gateway = new MockOaWorkflowGateway();
        OaStartCommand command = new OaStartCommand(
                "WF_QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                1L,
                "小王",
                Map.of("supportingFiles", List.of(Map.of(
                        "fileName", "授权书.pdf",
                        "fileUrl", "doc-insight://workflow-form-attachments/10/stored.pdf",
                        "storagePath", "doc-insight://workflow-form-attachments/10/stored.pdf",
                        "contentType", "application/pdf",
                        "size", 11L
                ))),
                "QUALIFICATION_BORROW",
                Map.of(),
                false
        );

        gateway.startProcess(command);

        OaStartCommand captured = gateway.lastStartedCommand().orElseThrow();
        Object attachmentValue = captured.formData().get("supportingFiles");
        assertThat(attachmentValue).asList().singleElement().isInstanceOf(OaAttachmentPayload.class);
        OaAttachmentPayload payload = (OaAttachmentPayload) ((List<?>) attachmentValue).getFirst();
        assertThat(payload.fileName()).isEqualTo("授权书.pdf");
        assertThat(payload.fileUrl()).isEqualTo("doc-insight://workflow-form-attachments/10/stored.pdf");
        assertThat(payload.storagePath()).isEqualTo("doc-insight://workflow-form-attachments/10/stored.pdf");
    }

    @Test
    void startProcess_preserves_optional_null_form_values() {
        MockOaWorkflowGateway gateway = new MockOaWorkflowGateway();
        OaStartCommand command = new OaStartCommand(
                "WF_QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                1L,
                "小王",
                new java.util.LinkedHashMap<>() {{
                    put("remark", null);
                    put("supportingFiles", List.of());
                }},
                "QUALIFICATION_BORROW",
                Map.of(),
                false
        );

        gateway.startProcess(command);

        OaStartCommand captured = gateway.lastStartedCommand().orElseThrow();
        assertThat(captured.formData()).containsEntry("remark", null);
    }
}
