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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidTenderDocumentImportAppServiceTest {

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
    private BidAgentOperatorResolver operatorResolver;
    @Mock
    private TransactionTemplate transactionTemplate;

    private BidTenderDocumentImportAppService appService;

    @BeforeEach
    void setUp() {
        BidDraftAgentJsonCodec jsonCodec = new BidDraftAgentJsonCodec(new ObjectMapper().findAndRegisterModules());
        appService = new BidTenderDocumentImportAppService(
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
                operatorResolver,
                transactionTemplate
        );
    }

    @Test
    void parseTenderDocument_shouldPersistRequirementsAndUpdateTenderSnapshot() {
        allowTransactionCallbacks();
        MockMultipartFile file = sampleFile();
        Project project = Project.builder().id(11L).tenderId(22L).name("华东智慧园区改造项目").managerId(1L).build();
        Tender tender = Tender.builder().id(22L).title(" ").description(" ").tags(" ").build();
        TenderRequirementProfile profile = sampleProfile();

        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(22L)).thenReturn(Optional.of(tender));
        when(documentStorage.store(eq(11L), eq("招标文件.docx"), any(), any()))
                .thenReturn(new StoredTenderDocument("bid-agent://tender-documents/11/file", "/tmp/file", "abc"));
        when(textExtractor.extract(eq("招标文件.docx"), any(), any()))
                .thenReturn(new ExtractedTenderDocument("招标文件.docx", file.getContentType(), "抽取正文", 4, "test-extractor"));
        when(documentAnalyzer.analyze(any())).thenReturn(profile);
        when(operatorResolver.currentOperator()).thenReturn(new BidAgentOperator(7L, "张经理"));
        when(projectDocumentRepository.save(any())).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(501L);
            return document;
        });
        when(documentSnapshotRepository.save(any())).thenAnswer(invocation -> {
            BidTenderDocumentSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(601L);
            return snapshot;
        });
        when(requirementItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = appService.parseTenderDocument(11L, file);

        assertThat(result.getMessage()).isEqualTo("招标文件已解析，已更新招标要求快照");
        assertThat(result.getDocument().getSnapshotId()).isEqualTo(601L);
        assertThat(result.getRequirementProfile().projectName()).isEqualTo("华东智慧园区改造项目");
        assertThat(tender.getTitle()).isEqualTo("2026园区改造招标公告");
        assertThat(tender.getPurchaserName()).isEqualTo("上海采购集团");
        assertThat(tender.getDescription()).contains("资格要求", "评分标准", "必须提供的材料");
        assertThat(tender.getTags()).contains("智慧园区");
        assertThat(tender.getBudget()).isEqualByComparingTo("1250000.00");
        assertThat(tender.getRegion()).isEqualTo("上海浦东");
        assertThat(tender.getIndustry()).isEqualTo("智慧园区");
        assertThat(tender.getPublishDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(tender.getDeadline()).isEqualTo(LocalDateTime.of(2026, 5, 30, 17, 30));

        ArgumentCaptor<ProjectDocument> documentCaptor = ArgumentCaptor.forClass(ProjectDocument.class);
        ArgumentCaptor<List<BidRequirementItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(11L);
        verify(projectDocumentRepository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getFileType()).isEqualTo("docx");
        verify(requirementItemRepository).saveAll(itemCaptor.capture());
        assertThat(itemCaptor.getValue()).hasSize(2);
        assertThat(itemCaptor.getValue()).extracting(BidRequirementItem::getCategory)
                .contains("qualification", "technical");
    }

    @Test
    void parseTenderDocument_shouldNotOverwriteExistingTenderStructuredFields() {
        allowTransactionCallbacks();
        MockMultipartFile file = sampleFile();
        Project project = Project.builder().id(11L).tenderId(22L).name("华东智慧园区改造项目").managerId(1L).build();
        Tender tender = Tender.builder()
                .id(22L)
                .title("旧标题")
                .purchaserName("旧采购单位")
                .description("旧描述")
                .tags("旧标签")
                .budget(new BigDecimal("9900000.00"))
                .region("北京")
                .industry("制造业")
                .publishDate(LocalDate.of(2026, 3, 1))
                .deadline(LocalDateTime.of(2026, 4, 20, 10, 0))
                .build();

        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(22L)).thenReturn(Optional.of(tender));
        when(documentStorage.store(eq(11L), eq("招标文件.docx"), any(), any()))
                .thenReturn(new StoredTenderDocument("bid-agent://tender-documents/11/file", "/tmp/file", "abc"));
        when(textExtractor.extract(eq("招标文件.docx"), any(), any()))
                .thenReturn(new ExtractedTenderDocument("招标文件.docx", file.getContentType(), "抽取正文", 4, "test-extractor"));
        when(documentAnalyzer.analyze(any())).thenReturn(sampleProfile());
        when(operatorResolver.currentOperator()).thenReturn(new BidAgentOperator(7L, "张经理"));
        when(projectDocumentRepository.save(any())).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(501L);
            return document;
        });
        when(documentSnapshotRepository.save(any())).thenAnswer(invocation -> {
            BidTenderDocumentSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(601L);
            return snapshot;
        });
        when(requirementItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        appService.parseTenderDocument(11L, file);

        assertThat(tender.getTitle()).isEqualTo("旧标题");
        assertThat(tender.getPurchaserName()).isEqualTo("旧采购单位");
        assertThat(tender.getDescription()).isEqualTo("旧描述");
        assertThat(tender.getTags()).isEqualTo("旧标签");
        assertThat(tender.getBudget()).isEqualByComparingTo("9900000.00");
        assertThat(tender.getRegion()).isEqualTo("北京");
        assertThat(tender.getIndustry()).isEqualTo("制造业");
        assertThat(tender.getPublishDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(tender.getDeadline()).isEqualTo(LocalDateTime.of(2026, 4, 20, 10, 0));
    }

    @Test
    void parseTenderDocument_shouldReturnSnapshotEvenWithoutGenerationStep() {
        allowTransactionCallbacks();
        MockMultipartFile file = sampleFile();
        Project project = Project.builder().id(11L).tenderId(22L).name("华东智慧园区改造项目").managerId(1L).build();
        Tender tender = Tender.builder().id(22L).title("旧标题").description("旧描述").tags("旧标签").build();

        when(projectRepository.findById(11L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(22L)).thenReturn(Optional.of(tender));
        when(documentStorage.store(eq(11L), eq("招标文件.docx"), any(), any()))
                .thenReturn(new StoredTenderDocument("bid-agent://tender-documents/11/file", "/tmp/file", "abc"));
        when(textExtractor.extract(eq("招标文件.docx"), any(), any()))
                .thenReturn(new ExtractedTenderDocument("招标文件.docx", file.getContentType(), "抽取正文", 4, "test-extractor"));
        when(documentAnalyzer.analyze(any())).thenReturn(sampleProfile());
        when(operatorResolver.currentOperator()).thenReturn(new BidAgentOperator(7L, "张经理"));
        when(projectDocumentRepository.save(any())).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(501L);
            return document;
        });
        when(documentSnapshotRepository.save(any())).thenAnswer(invocation -> {
            BidTenderDocumentSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(601L);
            return snapshot;
        });
        when(requirementItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = appService.parseTenderDocument(11L, file);

        assertThat(result.getDocument().getSnapshotId()).isEqualTo(601L);
        assertThat(result.getMessage()).isEqualTo("招标文件已解析，已更新招标要求快照");
        verify(projectDocumentRepository).save(any());
        verify(documentSnapshotRepository).save(any());
        verify(requirementItemRepository).saveAll(any());
    }

    @Test
    void latestParsedTenderDocument_shouldReturnLatestSnapshotWithoutReadingStoredFile() throws Exception {
        TenderRequirementProfile profile = sampleProfile();
        String profileJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(profile);
        ProjectDocument document = ProjectDocument.builder()
                .id(501L)
                .projectId(11L)
                .name("招标文件.docx")
                .size("12KB")
                .fileType("docx")
                .fileUrl("bid-agent://tender-documents/11/file.docx")
                .build();
        BidTenderDocumentSnapshot snapshot = BidTenderDocumentSnapshot.builder()
                .id(601L)
                .projectId(11L)
                .tenderId(22L)
                .projectDocumentId(501L)
                .fileName("招标文件.docx")
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileUrl("bid-agent://tender-documents/11/file.docx")
                .storagePath("/tmp/file.docx")
                .extractedText("抽取正文")
                .profileJson(profileJson)
                .extractorKey("test-extractor")
                .analyzerKey("test-analyzer")
                .build();

        when(documentSnapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(11L))
                .thenReturn(Optional.of(snapshot));
        when(projectDocumentRepository.findById(501L)).thenReturn(Optional.of(document));

        var result = appService.latestParsedTenderDocument(11L);

        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).isEqualTo("已复用已解析的招标文件");
        assertThat(result.get().getDocument().getSnapshotId()).isEqualTo(601L);
        assertThat(result.get().getDocument().getName()).isEqualTo("招标文件.docx");
        assertThat(result.get().getRequirementProfile().projectName()).isEqualTo("华东智慧园区改造项目");
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(11L);
        verify(documentStorage, never()).store(any(), any(), any(), any());
        verify(textExtractor, never()).extract(any(), any(), any());
        verify(documentAnalyzer, never()).analyze(any());
    }

    @Test
    void parseTenderDocument_shouldStopBeforeReadingFileWhenProjectAccessDenied() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "招标文件.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "正文".getBytes(StandardCharsets.UTF_8)
        );
        doThrow(new AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(11L);

        assertThatThrownBy(() -> appService.parseTenderDocument(11L, file))
                .isInstanceOf(AccessDeniedException.class);

        verify(documentStorage, never()).store(any(), any(), any(), any());
        verify(textExtractor, never()).extract(any(), any(), any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void allowTransactionCallbacks() {
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile(
                "file",
                "招标文件.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "资格要求：提供资质证书\n技术要求：提供实施方案\n评分标准：技术方案50分".getBytes(StandardCharsets.UTF_8)
        );
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
                List.of(
                        new TenderRequirementItemSnapshot("qualification", "资质证书", "提供有效资质证书", true, "资格要求", 95),
                        new TenderRequirementItemSnapshot("technical", "实施方案", "提供实施、运维和培训方案", true, "技术要求", 92)
                )
        );
    }
}
