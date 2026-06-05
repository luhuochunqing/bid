package com.xiyu.bid.biddraftagent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidRequirementItemRepository;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidUploadedTenderDocumentReuseAppServiceTest {

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private BidRequirementItemRepository requirementItemRepository;
    @Mock
    private BidTenderDocumentSnapshotRepository documentSnapshotRepository;
    @Mock
    private TenderDocumentStorage documentStorage;
    @Mock
    private TenderDocumentTextExtractor textExtractor;
    @Mock
    private TenderDocumentAnalyzer documentAnalyzer;
    @Mock
    private TransactionTemplate transactionTemplate;

    private BidUploadedTenderDocumentReuseAppService appService;

    @BeforeEach
    void setUp() {
        BidDraftAgentJsonCodec jsonCodec = new BidDraftAgentJsonCodec(new ObjectMapper().findAndRegisterModules());
        appService = new BidUploadedTenderDocumentReuseAppService(
                projectAccessScopeService,
                projectRepository,
                tenderRepository,
                projectDocumentRepository,
                requirementItemRepository,
                documentSnapshotRepository,
                documentStorage,
                textExtractor,
                documentAnalyzer,
                new TenderRequirementSnapshotUpdater(),
                new TenderRequirementEntityFactory(),
                jsonCodec,
                transactionTemplate
        );
    }

    @Test
    void parseLatestUploadedTenderDocument_shouldReuseProjectDocumentWithoutSavingAnotherDocument() {
        allowTransactionCallbacks();
        Project project = Project.builder().id(11L).tenderId(22L).build();
        Tender tender = Tender.builder().id(22L).title(" ").description(" ").tags(" ").build();
        ProjectDocument document = ProjectDocument.builder()
                .id(501L)
                .projectId(11L)
                .name("已上传招标文件.docx")
                .fileType("docx")
                .size("8KB")
                .fileUrl("bid-agent://tender-documents/11/stored.docx")
                .build();
        byte[] content = "资格要求：提供资质证书".getBytes(StandardCharsets.UTF_8);
        TenderRequirementProfile profile = sampleProfile();

        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(22L)).thenReturn(Optional.of(tender));
        when(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(11L)).thenReturn(List.of(document));
        when(documentStorage.loadByFileUrl("bid-agent://tender-documents/11/stored.docx"))
                .thenReturn(Optional.of(new LoadedTenderDocument(
                        new StoredTenderDocument("bid-agent://tender-documents/11/stored.docx", "/tmp/stored.docx", "abc"),
                        content
                )));
        when(textExtractor.extract("已上传招标文件.docx", null, content))
                .thenReturn(new ExtractedTenderDocument("已上传招标文件.docx", null, "抽取正文", 4, "test-extractor"));
        when(documentAnalyzer.analyze(any())).thenReturn(profile);
        when(documentSnapshotRepository.save(any())).thenAnswer(invocation -> {
            BidTenderDocumentSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(701L);
            return snapshot;
        });
        when(requirementItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = appService.parseLatestUploadedTenderDocument(11L);

        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).isEqualTo("已复用项目已上传的招标文件");
        assertThat(result.get().getDocument().getId()).isEqualTo(501L);
        assertThat(result.get().getDocument().getSnapshotId()).isEqualTo(701L);
        assertThat(tender.getTitle()).isEqualTo("2026园区改造招标公告");
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(11L);
        verify(projectDocumentRepository, never()).save(any());
        ArgumentCaptor<List<BidRequirementItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(requirementItemRepository).saveAll(itemCaptor.capture());
        assertThat(itemCaptor.getValue()).hasSize(1);
    }

    @Test
    void parseLatestUploadedTenderDocument_withoutReusableFile_shouldReturnEmpty() {
        Project project = Project.builder().id(11L).tenderId(22L).build();
        Tender tender = Tender.builder().id(22L).build();
        ProjectDocument document = ProjectDocument.builder()
                .id(501L)
                .projectId(11L)
                .name("普通会议纪要.docx")
                .fileUrl("bid-agent://tender-documents/11/meeting.docx")
                .build();

        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(22L)).thenReturn(Optional.of(tender));
        when(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(11L)).thenReturn(List.of(document));

        var result = appService.parseLatestUploadedTenderDocument(11L);

        assertThat(result).isEmpty();
        verify(documentStorage, never()).loadByFileUrl(any());
        verify(textExtractor, never()).extract(any(), any(), any());
    }

    @Test
    void parseLatestUploadedTenderDocument_shouldReuseTenderSourceDocumentWhenProjectHasNoDocuments() {
        allowTransactionCallbacks();
        Project project = Project.builder().id(11L).tenderId(22L).build();
        Tender tender = Tender.builder()
                .id(22L)
                .title(" ")
                .description(" ")
                .tags(" ")
                .sourceDocumentName("首次上传招标文件.pdf")
                .sourceDocumentFileType("application/pdf")
                .sourceDocumentFileUrl("doc-insight://TENDER_INTAKE/manual-tender/hash-首次上传招标文件.pdf")
                .build();
        byte[] content = "资格要求：提供资质证书".getBytes(StandardCharsets.UTF_8);
        TenderRequirementProfile profile = sampleProfile();

        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(22L)).thenReturn(Optional.of(tender));
        when(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(11L)).thenReturn(List.of());
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument saved = invocation.getArgument(0);
            saved.setId(801L);
            return saved;
        });
        when(documentStorage.loadByFileUrl("doc-insight://TENDER_INTAKE/manual-tender/hash-首次上传招标文件.pdf"))
                .thenReturn(Optional.of(new LoadedTenderDocument(
                        new StoredTenderDocument(
                                "doc-insight://TENDER_INTAKE/manual-tender/hash-首次上传招标文件.pdf",
                                "/tmp/hash-首次上传招标文件.pdf",
                                "abc"
                        ),
                        content
                )));
        when(textExtractor.extract("首次上传招标文件.pdf", "application/pdf", content))
                .thenReturn(new ExtractedTenderDocument("首次上传招标文件.pdf", "application/pdf", "抽取正文", 4, "test-extractor"));
        when(documentAnalyzer.analyze(any())).thenReturn(profile);
        when(documentSnapshotRepository.save(any())).thenAnswer(invocation -> {
            BidTenderDocumentSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(901L);
            return snapshot;
        });
        when(requirementItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = appService.parseLatestUploadedTenderDocument(11L);

        assertThat(result).isPresent();
        assertThat(result.get().getDocument().getId()).isEqualTo(801L);
        assertThat(result.get().getDocument().getSnapshotId()).isEqualTo(901L);
        assertThat(result.get().getDocument().getFileUrl())
                .isEqualTo("doc-insight://TENDER_INTAKE/manual-tender/hash-首次上传招标文件.pdf");
        ArgumentCaptor<ProjectDocument> documentCaptor = ArgumentCaptor.forClass(ProjectDocument.class);
        verify(projectDocumentRepository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue()).satisfies(document -> {
            assertThat(document.getProjectId()).isEqualTo(11L);
            assertThat(document.getDocumentCategory()).isEqualTo("TENDER_FILE");
            assertThat(document.getLinkedEntityType()).isEqualTo("TENDER");
            assertThat(document.getLinkedEntityId()).isEqualTo(22L);
            assertThat(document.getFileUrl()).isEqualTo("doc-insight://TENDER_INTAKE/manual-tender/hash-首次上传招标文件.pdf");
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void allowTransactionCallbacks() {
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private TenderRequirementProfile sampleProfile() {
        return new TenderRequirementProfile(
                "华东智慧园区改造项目",
                "2026园区改造招标公告",
                "园区数字化改造",
                "上海采购集团",
                new BigDecimal("1250000.00"),
                "上海浦东",
                "智慧园区",
                LocalDate.of(2026, 4, 1),
                LocalDateTime.of(2026, 5, 30, 17, 30),
                List.of("提供有效资质证书"),
                List.of("提供实施方案"),
                List.of("响应付款和交付条款"),
                List.of("技术方案50分"),
                "2026-05-30",
                List.of("投标函", "授权书"),
                List.of("报价和法务条款需人工确认"),
                List.of("智慧园区", "数字化"),
                List.of(new TenderRequirementItemSnapshot("qualification", "资质证书", "提供有效资质证书", true, "资格要求", 95))
        );
    }
}
