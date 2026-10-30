package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PrimaryScoreParseCompletionTest {
    @Test void versionedTaskUsesFrozenSnapshotInsteadOfLegacyItemOverwrite() {
        var task = ScoreParseTask.builder().id(5L).projectId(1135L).taskId("primary-versioned").build();
        var candidates = List.of(new ScoreCandidate("1", "评分", "规则满分100分", new BigDecimal("100"),
                "OBJECTIVE", null, "规则满分100分", "text", "DUAL_TRACK_PRIMARY"));
        var result = DualTrackScoreExtractor.dispatch(candidates, "规则满分100分", new BigDecimal("100"), List::of);
        var writer = mock(ScoreExtractionSnapshotWriter.class);
        var activation = mock(ScoreVersionActivationService.class);
        var finalizer = mock(ScoreExtractionTaskFinalizer.class);
        var versionProgress = mock(ScoreParseProgressService.class);
        var completion = new VersionedScoreParseCompletion(writer, activation, finalizer, versionProgress);
        var tasks = mock(ScoreParseTaskRepository.class);
        var items = mock(ScoreItemPersistenceService.class);
        var estimates = mock(EstimatedScoreService.class);
        var state = mock(ScoreParseTaskStateService.class);
        var progress = mock(ScoreParseProgressService.class);
        when(finalizer.finish(task, null)).thenReturn(true);
        var total = new OriginalScoreEvidencePolicy.Total(new BigDecimal("100"), "TENDER_TEXT", "", "text", List.of());
        var route = new ScoreParseEntryRouter.Route(result, null, total, "boundary");
        PrimaryScoreParseCompletion.complete(task, route, new TenderTextSource("synthetic.pdf", "local", "规则满分100分"),
                completion, tasks, items, estimates, state, progress);
        var frozen = org.mockito.ArgumentCaptor.forClass(ScoreExtractionSnapshot.class);
        verify(writer).freeze(org.mockito.ArgumentMatchers.eq(1135L), org.mockito.ArgumentMatchers.eq(5L), frozen.capture());
        assertThat(frozen.getValue().schemes()).hasSize(1);
        assertThat(frozen.getValue().schemes().getFirst().computableTotal()).isEqualByComparingTo("100");
        verifyNoInteractions(items, estimates, tasks, state);
        assertThat(task.getPipelineVersion()).isEqualTo("DUAL_TRACK_PRIMARY");
        assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
        assertThat(task.getFallbackReason()).isNull();
        verify(activation).tryAutoActivate(1135L, "primary-versioned");
    }
}
