package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.BidDocumentLoader;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Candidate;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Update;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class CaseProvenanceBackfillBoundaryTest {
    private static final String MARKDOWN = "# 1. 成功案例\n\n唯一案例正文内容，证明完整对接效果。";
    private static final String SNIPPET = "唯一案例正文内容，证明完整对接效果。";
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final BidDocumentLoader loader = mock(BidDocumentLoader.class);
    private final MemoryPort port = new MemoryPort();
    private final CaseProvenanceBackfillAppService service =
            new CaseProvenanceBackfillAppService(port, projects, loader);

    private static class MemoryPort implements CaseProvenanceBackfillPort {
        private List<Candidate> candidates = List.of();
        private final List<List<Update>> batches = new ArrayList<>();
        private boolean failWrite;
        public List<Candidate> findActiveCandidates() { return candidates; }
        public void saveAll(List<Update> updates) {
            if (failWrite) { throw new IllegalStateException("write failed"); }
            batches.add(List.copyOf(updates));
        }
        public void saveAllIfProvenanceEmpty(List<Update> updates) {
            saveAll(updates);
        }
    }

    @Test
    void countsOrphanWithoutLoadingOrWriting() {
        port.candidates = List.of(new Candidate(20L, null, SNIPPET, "案例", null, null, null, null, null));
        assertThat(service.recomputeAll())
                .isEqualTo(new CaseProvenanceBackfillAppService.BackfillResult(0, 1, 0, 0, 1, 0));
        verifyNoInteractions(loader);
        assertThat(port.batches).isEmpty();
    }

    @Test
    void propagatesWriteFailure() {
        prepare();
        port.failWrite = true;
        assertThatThrownBy(service::recomputeAll).isInstanceOf(IllegalStateException.class).hasMessage("write failed");
    }

    @Test
    void secondRunDoesNotWriteVerifiedProvenance() {
        prepare();
        assertThat(service.recomputeAll().casesUpdated()).isEqualTo(1);
        assertThat(port.batches).hasSize(1);
        var first = port.batches.get(0).get(0);
        assertThat(first.sourceSectionTitle()).isEqualTo("1. 成功案例");
        port.candidates = List.of(new Candidate(first.id(), 100L, SNIPPET, "案例", first.sourceSectionTitle(),
                first.sourceSectionPath(), first.sourceSectionIndex(), first.sourceDocumentName(), first.sourcePageNumber()));
        assertThat(service.recomputeAll().casesScanned()).isZero();
        assertThat(port.batches).hasSize(1);
        verify(loader, times(1)).loadBidDocument("项目-100", 100L);
    }

    private void prepare() {
        port.candidates = List.of(new Candidate(21L, 100L, SNIPPET, "案例", null, null, null, null, null));
        when(projects.findById(anyLong())).thenReturn(Optional.empty());
        when(loader.loadBidDocument("项目-100", 100L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", 12, true));
    }
}
