package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceArtifactAcquisitionTest {
    @Test void versionedLegacyFreezeRetainsOcrCauseInSnapshot() {
        var writer = org.mockito.Mockito.mock(ScoreExtractionSnapshotWriter.class);
        var finalizer = org.mockito.Mockito.mock(ScoreExtractionTaskFinalizer.class);
        var completion = new VersionedScoreParseCompletion(writer,
                org.mockito.Mockito.mock(ScoreVersionActivationService.class), finalizer,
                org.mockito.Mockito.mock(ScoreParseProgressService.class));
        var task = com.xiyu.bid.scoreparse.entity.ScoreParseTask.builder()
                .id(182L).projectId(43L).taskId("task").runSummary("{\"sourceDiagnostics\":[\"" + FAILURE + "\"]}").build();
        var draft = com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot.from("task", List.of(), List.of(), List.of("RECALL_CHUNKS_SKIPPED"));
        completion.legacyFinish(task, draft, null);
        var frozen = org.mockito.ArgumentCaptor.forClass(com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot.class);
        org.mockito.Mockito.verify(writer).freeze(org.mockito.ArgumentMatchers.eq(43L), org.mockito.ArgumentMatchers.eq(182L), frozen.capture());
        assertThat(frozen.getValue().sourceDiagnostics()).contains("RECALL_CHUNKS_SKIPPED", FAILURE);
    }
    private static final String FAILURE = "BAIDU_OCR_PAGE_FAILED:24:PROVIDER_ERROR_6";

    @Test void incompleteTaskSummaryRetainsOcrCause() throws Exception {
        var task = new com.xiyu.bid.scoreparse.entity.ScoreParseTask();
        var structured = StructuredSourceDiagnostics.attach(StructuredTableResult.structureIncomplete("STRUCTURE_DEFECT"),
                List.of(FAILURE));
        StructuredTableExtractionHandler.markIncomplete(task, "task", structured,
                org.mockito.Mockito.mock(com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository.class),
                org.mockito.Mockito.mock(ScoreParseTaskStateService.class),
                org.mockito.Mockito.mock(ScoreParseProgressService.class));
        assertThat(new ObjectMapper().readTree(task.getRunSummary()).path("sourceDiagnostics").toString()).contains(FAILURE);
    }

    @Test void singleFileDeterministicEntryPreservesOcrDiagnosticsWithoutCandidates() {
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "sidecar"),
                List.of(), List.of(FAILURE));
        var extractor = new SidecarDocumentArtifactExtractor("http://unused", "", 1, new ObjectMapper()) {
            @Override public DocumentArtifact extract(byte[] content, String fileName) { return artifact; }
        };
        var source = new TenderTextSource("project43.pdf", "", "评分标准 总分100分");
        var result = StructuredTableExtractionHandler.extract(source,
                new LoadedBidFile.InMemory(new byte[]{1}, "project43.pdf", "application/pdf"), extractor, null);
        assertThat(result.sourceDiagnostics()).contains(FAILURE);
    }
}
