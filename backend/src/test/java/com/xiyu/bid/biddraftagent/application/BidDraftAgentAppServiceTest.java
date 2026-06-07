package com.xiyu.bid.biddraftagent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentApplyResponseDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentRunDTO;
import com.xiyu.bid.biddraftagent.entity.BidAgentArtifact;
import com.xiyu.bid.biddraftagent.entity.BidAgentRun;
import com.xiyu.bid.biddraftagent.repository.BidAgentArtifactRepository;
import com.xiyu.bid.biddraftagent.repository.BidAgentRunRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.xiyu.bid.biddraftagent.application.BidDraftAgentAppServiceFixtures.baseRun;
import static com.xiyu.bid.biddraftagent.application.BidDraftAgentAppServiceFixtures.sampleSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidDraftAgentAppServiceTest {

    @Mock
    private BidDraftSnapshotAssembler snapshotAssembler;

    @Mock
    private BidDraftTextGenerator textGenerator;

    @Mock
    private BidAgentRunRepository runRepository;

    @Mock
    private BidAgentArtifactRepository artifactRepository;

    @Mock
    private BidDraftAgentDocumentWriter documentWriter;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private BidDraftAgentAppService appService;

    @BeforeEach
    void setUp() {
        BidDraftAgentJsonCodec jsonCodec = new BidDraftAgentJsonCodec(objectMapper);
        appService = new BidDraftAgentAppService(
                snapshotAssembler,
                new BidDraftAgentEvaluator(),
                textGenerator,
                new BidDraftAgentEntityFactory(jsonCodec),
                new BidDraftAgentRunMapper(jsonCodec),
                jsonCodec,
                new BidDraftAgentDocumentWritePlanner(),
                documentWriter,
                runRepository,
                artifactRepository,
                projectAccessScopeService
        );
    }

    @Test
    void createRun_shouldPersistRunAndArtifactsForSelectedSnapshot() {
        BidDraftSnapshot snapshot = sampleSnapshot();
        RequirementClassification classification = new RequirementClassification(
                List.of("价格"),
                List.of("合同"),
                List.of("资质"),
                List.of("技术"),
                List.of("交付"),
                List.of("商务"),
                List.of("项目背景")
        );
        MaterialMatchScore materialMatchScore = new MaterialMatchScore(100, 6, 6,
                List.of("pricing", "legal", "qualification", "technical", "delivery", "commercial"),
                List.of(), List.of("pricing:supported"), List.of());
        GapCheckResult gapCheck = new GapCheckResult(true, List.of(), List.of());
        ManualConfirmationDecision manualConfirmation = new ManualConfirmationDecision(true, true, true, List.of("需要人工确认"));
        WriteCoverageDecision writeCoverage = new WriteCoverageDecision(100, true,
                List.of("项目概况", "商务响应"), List.of(), List.of("项目概况"));
        BidDraftGenerationResult generationResult = new BidDraftGenerationResult(
                "draft text",
                "review summary",
                List.of(
                        new GeneratedArtifactSpec("DRAFT_TEXT", "自动生成投标草稿", "draft text", "document-writer"),
                        new GeneratedArtifactSpec("REVIEW_SUMMARY", "草稿审阅摘要", "review summary", "bid-reviewer")
                )
        );

        when(snapshotAssembler.assemble(11L, 601L)).thenReturn(snapshot);
        when(textGenerator.generate(any(), any(), any(), any(), any(), any())).thenReturn(generationResult);
        when(runRepository.save(any())).thenAnswer(invocation -> {
            BidAgentRun run = invocation.getArgument(0);
            run.setId(100L);
            run.setCreatedAt(LocalDateTime.of(2026, 4, 22, 10, 0));
            run.setUpdatedAt(LocalDateTime.of(2026, 4, 22, 10, 0));
            return run;
        });
        when(artifactRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<BidAgentArtifact> artifacts = invocation.getArgument(0);
            for (int i = 0; i < artifacts.size(); i++) {
                artifacts.get(i).setId(200L + i);
                artifacts.get(i).setCreatedAt(LocalDateTime.of(2026, 4, 22, 10, 0));
                artifacts.get(i).setUpdatedAt(LocalDateTime.of(2026, 4, 22, 10, 0));
            }
            return artifacts;
        });

        BidDraftAgentRunDTO dto = appService.createRun(11L, 601L);

        assertThat(dto.getId()).isEqualTo(100L);
        assertThat(dto.getProjectId()).isEqualTo(11L);
        assertThat(dto.getArtifacts()).hasSize(2);
        assertThat(dto.getArtifacts().get(0).getArtifactType()).isEqualTo("DRAFT_TEXT");
        assertThat(dto.getDraftText()).isEqualTo("draft text");

        ArgumentCaptor<BidAgentRun> runCaptor = ArgumentCaptor.forClass(BidAgentRun.class);
        verify(runRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getProjectName()).isEqualTo("华东智慧园区改造项目");
        assertThat(runCaptor.getValue().getStatus()).isEqualTo(BidAgentRun.Status.DRAFTED);
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(11L);
        verify(snapshotAssembler).assemble(11L, 601L);
    }

    @Test
    void createRun_shouldStopBeforeSnapshotWhenProjectAccessDenied() {
        doThrow(new AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(11L);

        assertThatThrownBy(() -> appService.createRun(11L, 601L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("权限不足");

        verify(snapshotAssembler, never()).assemble(11L, 601L);
        verify(runRepository, never()).save(any());
        verify(artifactRepository, never()).saveAll(any());
    }

    @Test
    void reviewCurrentDraft_shouldUpdateStoredRunAndReturnReviewView() throws Exception {
        BidDraftSnapshot snapshot = sampleSnapshot();
        BidAgentRun run = baseRun(snapshot);
        run.setId(100L);
        run.setStatus(BidAgentRun.Status.DRAFTED);
        run.setReviewText("old review");

        BidDraftGenerationResult generationResult = new BidDraftGenerationResult(
                run.getDraftText(),
                "updated review",
                List.of(new GeneratedArtifactSpec("REVIEW_SUMMARY", "草稿审阅摘要", "updated review", "bid-reviewer"))
        );
        BidAgentArtifact reviewArtifact = BidAgentArtifact.builder()
                .id(300L)
                .runId(100L)
                .projectId(11L)
                .artifactType("REVIEW_SUMMARY")
                .title("草稿审阅摘要")
                .content("old review")
                .handoffTarget("bid-reviewer")
                .status(BidAgentArtifact.Status.DRAFTED)
                .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .build();

        when(runRepository.findTopByProjectIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.of(run));
        when(textGenerator.generate(any(), any(), any(), any(), any(), any())).thenReturn(generationResult);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(artifactRepository.findByRunIdAndArtifactType(100L, "REVIEW_SUMMARY")).thenReturn(Optional.of(reviewArtifact));
        when(artifactRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var reviewDto = appService.reviewCurrentDraft(11L);

        assertThat(reviewDto.getRunId()).isEqualTo(100L);
        assertThat(reviewDto.getReviewSummary()).isEqualTo("updated review");
        assertThat(reviewDto.getNextActions()).isNotEmpty();
        verify(artifactRepository).save(reviewArtifact);
    }

    @Test
    void reviewRun_shouldUseRequestedRunInsteadOfLatestProjectRun() throws Exception {
        BidAgentRun run = baseRun(sampleSnapshot());
        run.setId(101L);
        BidDraftGenerationResult generationResult = new BidDraftGenerationResult(
                run.getDraftText(),
                "targeted review",
                List.of(new GeneratedArtifactSpec("REVIEW_SUMMARY", "草稿审阅摘要", "targeted review", "bid-reviewer"))
        );

        when(runRepository.findByIdAndProjectId(101L, 11L)).thenReturn(Optional.of(run));
        when(textGenerator.generate(any(), any(), any(), any(), any(), any())).thenReturn(generationResult);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(artifactRepository.findByRunIdAndArtifactType(101L, "REVIEW_SUMMARY")).thenReturn(Optional.empty());

        var reviewDto = appService.reviewRun(11L, 101L);

        assertThat(reviewDto.getRunId()).isEqualTo(101L);
        assertThat(reviewDto.getReviewSummary()).isEqualTo("targeted review");
        verify(runRepository).findByIdAndProjectId(101L, 11L);
    }

    @Test
    void applyRun_shouldMarkPrimaryArtifactReadyForWriter() throws Exception {
        BidAgentRun run = baseRun(sampleSnapshot());
        run.setId(100L);
        run.setStatus(BidAgentRun.Status.REVIEWED);

        BidAgentArtifact draftArtifact = BidAgentArtifact.builder()
                .id(200L)
                .runId(100L)
                .projectId(11L)
                .artifactType("DRAFT_TEXT")
                .title("自动生成投标草稿")
                .content("draft text")
                .handoffTarget("document-writer")
                .status(BidAgentArtifact.Status.DRAFTED)
                .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .build();
        BidAgentArtifact reviewArtifact = BidAgentArtifact.builder()
                .id(201L)
                .runId(100L)
                .projectId(11L)
                .artifactType("REVIEW_SUMMARY")
                .title("草稿审阅摘要")
                .content("review summary")
                .handoffTarget("bid-reviewer")
                .status(BidAgentArtifact.Status.DRAFTED)
                .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .build();

        when(runRepository.findByIdAndProjectId(100L, 11L)).thenReturn(Optional.of(run));
        when(artifactRepository.findByRunIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(draftArtifact, reviewArtifact));
        when(documentWriter.write(eq(11L), any())).thenReturn(new BidDraftAgentDocumentWriteResult(
                11L,
                400L,
                true,
                3,
                3,
                0,
                0,
                List.of()
        ));
        when(artifactRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BidDraftAgentApplyResponseDTO response = appService.applyRun(11L, 100L);

        assertThat(response.isReadyForWriter()).isTrue();
        assertThat(response.getArtifactType()).isEqualTo("DRAFT_TEXT");
        assertThat(response.getStructureId()).isEqualTo(400L);
        assertThat(response.getCreatedSections()).isEqualTo(3);
        assertThat(draftArtifact.getStatus()).isEqualTo(BidAgentArtifact.Status.READY_FOR_WRITER);
        verify(documentWriter).write(eq(11L), any(BidDraftAgentDocumentWritePlan.class));
        verify(artifactRepository).save(draftArtifact);
    }

}
