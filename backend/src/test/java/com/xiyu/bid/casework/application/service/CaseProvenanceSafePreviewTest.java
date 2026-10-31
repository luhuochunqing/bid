package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.BidDocumentLoader;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Candidate;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class CaseProvenanceSafePreviewTest {
    private static final String SNIPPET = "唯一案例正文内容，证明完整对接效果。";
    private static final String MARKDOWN = "# 1. 成功案例\n\n" + SNIPPET;

    private CaseProvenanceBackfillPort port;
    private ProjectRepository projects;
    private BidDocumentLoader loader;
    private CaseProvenanceBackfillAppService service;

    @BeforeEach
    void setUp() {
        port = mock(CaseProvenanceBackfillPort.class);
        projects = mock(ProjectRepository.class);
        loader = mock(BidDocumentLoader.class);
        service = new CaseProvenanceBackfillAppService(port, projects, loader);
        when(projects.findById(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void previewClassifiesFillableWithoutWriting() {
        when(port.findActiveCandidates()).thenReturn(List.of(emptyCandidate(1L, 100L, SNIPPET)));
        when(loader.loadBidDocument("项目-100", 100L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", 3, true));

        CaseProvenanceBackfillPlan plan = service.preview();

        assertThat(plan.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.type()).isEqualTo(CaseProvenanceBackfillPlan.DecisionType.FILLABLE);
            assertThat(decision.proposed().title()).isEqualTo("1. 成功案例");
        });
        verify(port, never()).saveAll(anyList());
    }

    @Test
    void previewSkipsAnyExistingProvenanceWithoutLoadingDocument() {
        Candidate partial = new Candidate(2L, 100L, SNIPPET, "案例", null,
                "1. 成功案例", null, null, null);
        when(port.findActiveCandidates()).thenReturn(List.of(partial));

        CaseProvenanceBackfillPlan plan = service.preview();

        assertThat(plan.decisions()).singleElement().extracting(CaseProvenanceBackfillPlan.Decision::type)
                .isEqualTo(CaseProvenanceBackfillPlan.DecisionType.SKIP_EXISTING);
        verifyNoInteractions(loader);
        verify(port, never()).saveAll(anyList());
    }

    @Test
    void previewKeepsUnresolvedAndCountsClose() {
        when(port.findActiveCandidates()).thenReturn(List.of(
                emptyCandidate(3L, 100L, "不存在于标书"),
                new Candidate(4L, null, SNIPPET, "案例", null, null, null, null, null)));
        when(loader.loadBidDocument("项目-100", 100L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillPlan plan = service.preview();

        assertThat(plan.count(CaseProvenanceBackfillPlan.DecisionType.UNRESOLVED)).isEqualTo(1);
        assertThat(plan.count(CaseProvenanceBackfillPlan.DecisionType.ORPHAN)).isEqualTo(1);
        assertThat(plan.decisions()).hasSize(plan.scannedCount());
        verify(port, never()).saveAll(anyList());
    }

    private static Candidate emptyCandidate(Long id, Long projectId, String responseText) {
        return new Candidate(id, projectId, responseText, "案例", null, null, null, null, null);
    }
}
