package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenderEvaluationDocumentServiceTest {

    private static final Long TENDER_ID = 100L;
    private static final Long DOCUMENT_ID = 500L;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;

    @Mock
    private TenderRepository tenderRepository;

    private TenderEvaluationDocumentService service;

    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        service = new TenderEvaluationDocumentService(projectDocumentRepository, tenderRepository);
        mockFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    // ---------- uploadDocument ----------

    @Test
    @DisplayName("uploadDocument: Tender status is PENDING_ASSIGNMENT -> Success")
    void uploadDocument_pendingAssignment_success() {
        Tender tender = Tender.builder().id(TENDER_ID).status(Tender.Status.PENDING_ASSIGNMENT).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        ProjectDocument savedDoc = ProjectDocument.builder().id(DOCUMENT_ID).projectId(TENDER_ID).build();
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenReturn(savedDoc);

        ProjectDocument result = service.uploadDocument(TENDER_ID, mockFile, "Uploader");
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(DOCUMENT_ID);
        verify(projectDocumentRepository, times(1)).save(any(ProjectDocument.class));
    }

    @Test
    @DisplayName("uploadDocument: Tender status is TRACKING -> Throws AccessDeniedException")
    void uploadDocument_tracking_throwsAccessDenied() {
        Tender tender = Tender.builder().id(TENDER_ID).status(Tender.Status.TRACKING).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> service.uploadDocument(TENDER_ID, mockFile, "Uploader"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("评估表对所有角色均为只读");
    }

    @Test
    @DisplayName("uploadDocument: Tender status is EVALUATED -> Throws AccessDeniedException")
    void uploadDocument_evaluated_throwsAccessDenied() {
        Tender tender = Tender.builder().id(TENDER_ID).status(Tender.Status.EVALUATED).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> service.uploadDocument(TENDER_ID, mockFile, "Uploader"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("评估表对所有角色均为只读");
    }

    @Test
    @DisplayName("uploadDocument: Tender status is BIDDING -> Throws AccessDeniedException")
    void uploadDocument_bidding_throwsAccessDenied() {
        Tender tender = Tender.builder().id(TENDER_ID).status(Tender.Status.BIDDING).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> service.uploadDocument(TENDER_ID, mockFile, "Uploader"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("评估表对所有角色均为只读");
    }

    @Test
    @DisplayName("uploadDocument: Tender status is ABANDONED -> Throws AccessDeniedException")
    void uploadDocument_abandoned_throwsAccessDenied() {
        Tender tender = Tender.builder().id(TENDER_ID).status(Tender.Status.ABANDONED).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> service.uploadDocument(TENDER_ID, mockFile, "Uploader"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("评估表对所有角色均为只读");
    }

    // ---------- deleteDocument ----------

    @Test
    @DisplayName("deleteDocument: Tender status is PENDING_ASSIGNMENT -> Success")
    void deleteDocument_pendingAssignment_success() {
        ProjectDocument doc = ProjectDocument.builder().id(DOCUMENT_ID).linkedEntityId(TENDER_ID).build();
        when(projectDocumentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(doc));

        Tender tender = Tender.builder().id(TENDER_ID).status(Tender.Status.PENDING_ASSIGNMENT).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        service.deleteDocument(DOCUMENT_ID);
        verify(projectDocumentRepository, times(1)).delete(doc);
    }

    @Test
    @DisplayName("deleteDocument: Tender status is TRACKING -> Throws AccessDeniedException")
    void deleteDocument_tracking_throwsAccessDenied() {
        ProjectDocument doc = ProjectDocument.builder().id(DOCUMENT_ID).linkedEntityId(TENDER_ID).build();
        when(projectDocumentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(doc));

        Tender tender = Tender.builder().id(TENDER_ID).status(Tender.Status.TRACKING).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> service.deleteDocument(DOCUMENT_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("评估表对所有角色均为只读");
    }
}
