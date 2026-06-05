package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectDocumentUploadWorkflowServiceTest {

    @Test
    void createUploadedProjectDocument_shouldStoreRealFileAndPersistFileUrl() throws Exception {
        ProjectWorkflowGuardService accessGuard = mock(ProjectWorkflowGuardService.class);
        ProjectDocumentWorkflowService documentWorkflowService = mock(ProjectDocumentWorkflowService.class);
        ProjectDocumentFileStorage fileStorage = mock(ProjectDocumentFileStorage.class);
        ProjectDocumentUploadWorkflowService service = new ProjectDocumentUploadWorkflowService(
                accessGuard,
                documentWorkflowService,
                fileStorage,
                null // ProjectArchiveWorkflowService (archive attach tested via integration)
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "招标文件.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "招标正文".getBytes(StandardCharsets.UTF_8)
        );
        when(fileStorage.store(1001L, "招标文件.docx", file.getContentType(), file.getBytes()))
                .thenReturn(new StoredProjectDocumentFile("bid-agent://tender-documents/1001/stored.docx"));
        when(documentWorkflowService.createProjectDocument(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.any(ProjectDocumentCreateRequest.class)
        )).thenReturn(ProjectDocumentDTO.builder()
                .id(3003L)
                .name("招标文件.docx")
                .fileType("docx")
                .fileUrl("bid-agent://tender-documents/1001/stored.docx")
                .build());

        ProjectDocumentDTO dto = service.createUploadedProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("招标文件.docx")
                .uploaderName("王工")
                .build(), file);

        assertThat(dto.getName()).isEqualTo("招标文件.docx");
        assertThat(dto.getFileUrl()).isEqualTo("bid-agent://tender-documents/1001/stored.docx");
        assertThat(dto.getFileType()).isEqualTo("docx");
        verify(accessGuard).requireWorkflowMutationProject(1001L);
    }
}
