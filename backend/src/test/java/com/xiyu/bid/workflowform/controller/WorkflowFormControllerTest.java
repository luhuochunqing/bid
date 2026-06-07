package com.xiyu.bid.workflowform.controller;

import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.workflowform.application.command.WorkflowFormAttachmentUploadCommand;
import com.xiyu.bid.workflowform.application.service.WorkflowFormAccessGuard;
import com.xiyu.bid.workflowform.application.service.WorkflowFormAttachmentUploadService;
import com.xiyu.bid.workflowform.application.service.WorkflowFormSubmissionService;
import com.xiyu.bid.workflowform.application.service.WorkflowFormTemplateQueryService;
import com.xiyu.bid.workflowform.application.view.WorkflowFormAttachmentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkflowFormControllerTest {

    @Mock
    private WorkflowFormSubmissionService submissionService;
    @Mock
    private WorkflowFormTemplateQueryService templateQueryService;
    @Mock
    private WorkflowFormAccessGuard accessGuard;
    @Mock
    private WorkflowFormAttachmentUploadService attachmentUploadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WorkflowFormController controller = new WorkflowFormController(
                submissionService,
                templateQueryService,
                accessGuard,
                attachmentUploadService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadAttachment_returns_structured_metadata() throws Exception {
        when(attachmentUploadService.upload(any())).thenReturn(new WorkflowFormAttachmentView(
                "授权书.pdf",
                "doc-insight://workflow-form-attachments/10/stored.pdf",
                "doc-insight://workflow-form-attachments/10/stored.pdf",
                "application/pdf",
                11L
        ));

        mockMvc.perform(multipart("/api/workflow-forms/attachments")
                        .file("file", "pdf-content".getBytes())
                        .param("templateCode", "QUALIFICATION_BORROW")
                        .param("fieldKey", "supportingFiles")
                        .param("projectId", "10")
                        .param("fileName", "授权书.pdf")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").value("授权书.pdf"))
                .andExpect(jsonPath("$.data.fileUrl").value("doc-insight://workflow-form-attachments/10/stored.pdf"))
                .andExpect(jsonPath("$.data.storagePath").value("doc-insight://workflow-form-attachments/10/stored.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.size").value(11));

        verify(accessGuard).assertCanAccessProject(10L);
        ArgumentCaptor<WorkflowFormAttachmentUploadCommand> command = ArgumentCaptor.forClass(WorkflowFormAttachmentUploadCommand.class);
        verify(attachmentUploadService).upload(command.capture());
        assertThat(command.getValue().templateCode()).isEqualTo("QUALIFICATION_BORROW");
        assertThat(command.getValue().fieldKey()).isEqualTo("supportingFiles");
        assertThat(command.getValue().projectId()).isEqualTo(10L);
        assertThat(command.getValue().fileName()).isEqualTo("授权书.pdf");
        assertThat(command.getValue().content()).isEqualTo("pdf-content".getBytes());
    }
}
