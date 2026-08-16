package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitiationTenderTextResolverTest {

    private static final Long PROJECT_ID = 11L;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private BidTenderDocumentSnapshotRepository snapshotRepository;
    @Mock
    private ProjectDocumentFileStorage fileStorage;
    @Mock
    private TenderDocumentStorage tenderDocumentStorage;
    @Mock
    private TenderDocumentTextExtractor textExtractor;
    @Mock
    private ObsShareUrlSigner obsShareUrlSigner;

    private InitiationTenderTextResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new InitiationTenderTextResolver(
                projectDocumentRepository, snapshotRepository, fileStorage,
                tenderDocumentStorage, textExtractor, obsShareUrlSigner,
                url -> new byte[0]);
    }

    @Test
    void prefersInitiationTenderOverTenderFile() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(ProjectDocument.builder().id(1L).name("立项招标.pdf").build()));

        Optional<ProjectDocument> found = resolver.findLatestTenderDocument(PROJECT_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("立项招标.pdf");
        verify(projectDocumentRepository, never()).findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER_FILE", null, null);
    }

    @Test
    void resolveExtractsInitiationFileText() {
        ProjectDocument document = ProjectDocument.builder()
                .id(3L).name("立项招标.pdf").fileUrl("doc-insight://t/a.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/a.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://t/a.pdf", "/tmp/a.pdf", "application/pdf", "bytes".getBytes())));
        when(textExtractor.extract(eq("立项招标.pdf"), eq("pdf"), any()))
                .thenReturn(new ExtractedTenderDocument("立项招标.pdf", "pdf", "商务部分 20 分", 8, "md"));

        Optional<TenderTextSource> source = resolver.resolve(PROJECT_ID);

        assertThat(source).isPresent();
        assertThat(source.get().fileName()).isEqualTo("立项招标.pdf");
        assertThat(source.get().text()).isEqualTo("商务部分 20 分");
        verify(snapshotRepository, never()).findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID);
    }

    @Test
    void resolveFallsBackToSnapshotWhenNoInitiationFile() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                eq(PROJECT_ID), any(), eq(null), eq(null)))
                .thenReturn(List.of());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .fileName("旧快照.pdf").fileUrl("bid-agent://x")
                        .extractedText("技术部分 50 分").build()));

        Optional<TenderTextSource> source = resolver.resolve(PROJECT_ID);

        assertThat(source).isPresent();
        assertThat(source.get().text()).contains("技术部分");
    }

    @Test
    void hasSourceWhenInitiationFileExists() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(ProjectDocument.builder().id(1L).build()));

        assertThat(resolver.hasSource(PROJECT_ID)).isTrue();
    }
}
