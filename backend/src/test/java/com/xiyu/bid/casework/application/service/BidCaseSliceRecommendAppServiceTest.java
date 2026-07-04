package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.casework.application.BidCaseSliceRecommendationAssembler;
import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCandidate;
import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCriteria;
import com.xiyu.bid.casework.domain.model.BidCaseSliceRecommendation;
import com.xiyu.bid.casework.domain.policy.BidCaseSliceMatchPolicy;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceVectorCache;
import com.xiyu.bid.casework.infrastructure.QueryEmbeddingCache;
import com.xiyu.bid.exception.BusinessUnavailableException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidCaseSliceRecommendAppServiceTest {

    @Mock
    private BidCaseSliceVectorCache cache;
    @Mock
    private ProjectScoreDraftRepository scoreDraftRepository;
    @Mock
    private BidCaseSliceRepository sliceRepository;
    @Mock
    private AiProvider aiProvider;
    @Mock
    private BidCaseSliceMatchPolicy matchPolicy;
    @Mock
    private BidCaseSliceRecommendationAssembler assembler;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private QueryEmbeddingCache queryEmbeddingCache;

    private BidCaseSliceRecommendAppService service;

    @BeforeEach
    void setUp() {
        service = new BidCaseSliceRecommendAppService(
                cache,
                scoreDraftRepository,
                sliceRepository,
                aiProvider,
                assembler,
                projectAccessScopeService,
                matchPolicy,
                queryEmbeddingCache
        );
    }

    @Test
    void recommendByScoringItem_shouldReturnRankedResults() {
        ProjectScoreDraft draft = ProjectScoreDraft.builder()
                .id(2L)
                .projectId(1L)
                .scoreItemTitle("技术方案")
                .scoreRuleText("规则原文")
                .category("技术")
                .build();
        when(scoreDraftRepository.findById(2L)).thenReturn(Optional.of(draft));

        float[] vector = {1.0f, 0.0f};
        when(aiProvider.embed(anyString())).thenReturn(vector);

        BidCaseSliceMatchCriteria criteria = new BidCaseSliceMatchCriteria(
                "技术方案\n规则原文", vector, "技术", Set.of("技术", "方案")
        );
        when(assembler.buildCriteria(anyString(), eq(vector), eq("技术"))).thenReturn(criteria);

        List<BidCaseSliceVectorCache.BidCaseSliceVector> vectors = List.of();
        when(cache.findAll()).thenReturn(vectors);

        List<BidCaseSliceMatchCandidate> candidates = List.of();
        when(assembler.toCandidates(vectors)).thenReturn(candidates);

        List<BidCaseSliceRecommendation> expected = List.of(
                new BidCaseSliceRecommendation(1L, "p1", "a.docx", "技术", "标题", "正文", 100, 5, 0.9, 90, "语义相似")
        );
        when(matchPolicy.match(eq(criteria), eq(candidates), eq(20))).thenReturn(expected);

        List<BidCaseSliceRecommendation> result = service.recommendByScoringItem(1L, 2L, null);

        assertEquals(expected, result);
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
    }

    @Test
    void recommendByScoringItem_scoringItemNotFound_shouldThrowResourceNotFound() {
        when(scoreDraftRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.recommendByScoringItem(1L, 2L, null));
    }

    @Test
    void recommendByScoringItem_scoringItemBelongsToAnotherProject_shouldThrowResourceNotFound() {
        ProjectScoreDraft draft = ProjectScoreDraft.builder()
                .id(2L)
                .projectId(99L)
                .scoreItemTitle("技术方案")
                .scoreRuleText("规则原文")
                .category("技术")
                .build();
        when(scoreDraftRepository.findById(2L)).thenReturn(Optional.of(draft));

        assertThrows(ResourceNotFoundException.class,
                () -> service.recommendByScoringItem(1L, 2L, null));
        verify(aiProvider, never()).embed(anyString());
    }

    @Test
    void recommendByScoringItem_projectAccessDenied_shouldPropagate() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.recommendByScoringItem(1L, 2L, null));
        verify(scoreDraftRepository, never()).findById(any());
    }

    @Test
    void recommendByScoringItem_embeddingUnavailable_shouldThrowBusinessUnavailable() {
        ProjectScoreDraft draft = ProjectScoreDraft.builder()
                .id(2L)
                .projectId(1L)
                .scoreItemTitle("技术方案")
                .scoreRuleText("规则")
                .category("技术")
                .build();
        when(scoreDraftRepository.findById(2L)).thenReturn(Optional.of(draft));
        when(aiProvider.embed(anyString())).thenThrow(new IllegalStateException("AI disabled"));

        assertThrows(BusinessUnavailableException.class,
                () -> service.recommendByScoringItem(1L, 2L, null));
    }

    @Test
    void recommendByScoringItem_emptyCache_shouldThrowBusinessUnavailable() {
        ProjectScoreDraft draft = ProjectScoreDraft.builder()
                .id(2L)
                .projectId(1L)
                .scoreItemTitle("技术方案")
                .scoreRuleText("规则")
                .category("技术")
                .build();
        when(scoreDraftRepository.findById(2L)).thenReturn(Optional.of(draft));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{1.0f});
        when(cache.isEmpty()).thenReturn(true);

        assertThrows(BusinessUnavailableException.class,
                () -> service.recommendByScoringItem(1L, 2L, null));
        verify(matchPolicy, never()).match(any(), anyList(), anyInt());
    }

    @Test
    void recommendByQuery_shouldReturnRankedResults() {
        float[] vector = {1.0f, 0.0f};
        when(aiProvider.embed(anyString())).thenReturn(vector);

        BidCaseSliceMatchCriteria criteria = new BidCaseSliceMatchCriteria(
                "售后服务", vector, null, Set.of("售后", "服务")
        );
        when(assembler.buildCriteria(eq("售后服务"), eq(vector), eq((String) null))).thenReturn(criteria);

        List<BidCaseSliceVectorCache.BidCaseSliceVector> vectors = List.of();
        when(cache.findAll()).thenReturn(vectors);

        List<BidCaseSliceMatchCandidate> candidates = List.of();
        when(assembler.toCandidates(vectors)).thenReturn(candidates);

        List<BidCaseSliceRecommendation> expected = List.of(
                new BidCaseSliceRecommendation(2L, "p2", "b.docx", "商务", "售后", "正文", 100, 5, 0.85, 85, "语义相似")
        );
        when(matchPolicy.match(eq(criteria), eq(candidates), eq(20))).thenReturn(expected);

        List<BidCaseSliceRecommendation> result = service.recommendByQuery("售后服务", null);

        assertEquals(expected, result);
    }

    @Test
    void recommendByQuery_emptyQuery_shouldThrowIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.recommendByQuery("  ", null));
    }

    @Test
    void recommendByQuery_queryTooLong_shouldThrowIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recommendByQuery("x".repeat(3001), null));
    }

    @Test
    void getSliceDetail_shouldReturnDetailAndCheckAccess() {
        BidCaseSlice slice = new BidCaseSlice();
        slice.setId(123L);
        slice.setProjectDir("2026.01.05-中广核办公");
        slice.setDocxFile("技术文件/中广核办公技术方案.docx");
        slice.setDocxLabel("技术");
        slice.setTitle("狮行物流技术与系统优势");
        slice.setTextPreview("强大的计划管理系统PMS...");
        slice.setTextLength(308);
        slice.setParaCount(5);
        slice.setCreatedAt(java.time.LocalDateTime.of(2026, 7, 4, 10, 0, 0));
        when(sliceRepository.findById(123L)).thenReturn(Optional.of(slice));

        var result = service.getSliceDetail(123L, 1L);

        assertEquals(123L, result.sliceId());
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
    }

    @Test
    void getSliceDetail_projectAccessDenied_shouldPropagate() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getSliceDetail(123L, 1L));
        verify(sliceRepository, never()).findById(any());
    }

    @Test
    void getSliceDetail_notFound_shouldThrowResourceNotFound() {
        when(sliceRepository.findById(123L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.getSliceDetail(123L, 1L));
    }
}
