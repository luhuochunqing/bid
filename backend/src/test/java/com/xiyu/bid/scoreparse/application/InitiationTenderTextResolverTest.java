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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void hasSourceWhenInitiationFileExtracts() {
        ProjectDocument document = ProjectDocument.builder()
                .id(1L).name("立项招标.pdf").fileUrl("doc-insight://t/a.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/a.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://t/a.pdf", "/tmp/a.pdf", "application/pdf", "bytes".getBytes())));
        when(textExtractor.extract(eq("立项招标.pdf"), eq("pdf"), any()))
                .thenReturn(new ExtractedTenderDocument("立项招标.pdf", "pdf", "商务部分 20 分", 8, "md"));

        assertThat(resolver.hasSource(PROJECT_ID)).isTrue();
        assertThat(resolver.resolve(PROJECT_ID)).isPresent();
    }

    @Test
    void contentLengthOverLimitDoesNotReadBody() {
        AtomicBoolean bodyRead = new AtomicBoolean(false);
        assertThatThrownBy(() -> BoundedHttpDownloader.rejectIfDeclaredTooLarge(
                Optional.of(String.valueOf(BoundedHttpDownloader.MAX_BYTES + 1L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("50MB");
        assertThat(bodyRead).isFalse();
    }

    @Test
    void streamedBytesOverLimitFail() {
        InputStream oversize = new InputStream() {
            private long remaining = BoundedHttpDownloader.MAX_BYTES + 1L;

            @Override
            public int read() {
                if (remaining <= 0) {
                    return -1;
                }
                remaining--;
                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (remaining <= 0) {
                    return -1;
                }
                int n = (int) Math.min(length, remaining);
                remaining -= n;
                return n;
            }
        };

        assertThatThrownBy(() -> BoundedHttpDownloader.readAtMost(oversize))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("50MB");
    }

    @Test
    void exactlyMaxBytesAllowed() throws IOException {
        byte[] exact = new byte[BoundedHttpDownloader.MAX_BYTES];
        assertThat(BoundedHttpDownloader.readAtMost(new ByteArrayInputStream(exact)))
                .hasSize(BoundedHttpDownloader.MAX_BYTES);
    }

    @Test
    void resolveFallsBackToSnapshotWhenInitiationUnreadable() {
        ProjectDocument document = ProjectDocument.builder()
                .id(3L).name("坏文件.pdf").fileUrl("doc-insight://t/bad.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/bad.pdf")).thenReturn(Optional.empty());
        when(tenderDocumentStorage.loadByFileUrl("doc-insight://t/bad.pdf")).thenReturn(Optional.empty());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .fileName("旧快照.pdf").fileUrl("bid-agent://x")
                        .extractedText("技术部分 50 分").build()));

        Optional<TenderTextSource> source = resolver.resolve(PROJECT_ID);

        assertThat(source).isPresent();
        assertThat(source.get().text()).contains("技术部分");
        assertThat(resolver.hasSource(PROJECT_ID)).isTrue();
    }

    @Test
    void resolveFallsBackToSnapshotWhenInitiationTextBlank() {
        ProjectDocument document = ProjectDocument.builder()
                .id(3L).name("扫面件.pdf").fileUrl("doc-insight://t/scan.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/scan.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://t/scan.pdf", "/tmp/scan.pdf", "application/pdf", "img".getBytes())));
        when(textExtractor.extract(eq("扫面件.pdf"), eq("pdf"), any()))
                .thenReturn(new ExtractedTenderDocument("扫面件.pdf", "pdf", "   ", 0, "md"));
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .fileName("旧快照.pdf").extractedText("技术部分 50 分").build()));

        Optional<TenderTextSource> source = resolver.resolve(PROJECT_ID);

        assertThat(source).isPresent();
        assertThat(source.get().text()).contains("技术部分");
        assertThat(resolver.hasSource(PROJECT_ID)).isEqualTo(source.isPresent());
    }

    @Test
    void hasSourceMatchesResolveWhenBothMissing() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                eq(PROJECT_ID), any(), eq(null), eq(null)))
                .thenReturn(List.of());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve(PROJECT_ID)).isEmpty();
        assertThat(resolver.hasSource(PROJECT_ID)).isFalse();
    }

    @Test
    void oversizedLocalFileFallsBackToSnapshot() {
        byte[] oversized = new byte[BoundedHttpDownloader.MAX_BYTES + 1];
        ProjectDocument document = ProjectDocument.builder()
                .id(4L).name("超大.pdf").fileUrl("doc-insight://t/huge.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/huge.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://t/huge.pdf", "/tmp/huge.pdf", "application/pdf", oversized)));
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .fileName("旧快照.pdf").extractedText("底稿正文").build()));

        Optional<TenderTextSource> source = resolver.resolve(PROJECT_ID);

        assertThat(source).isPresent();
        assertThat(source.get().text()).isEqualTo("底稿正文");
        verify(textExtractor, never()).extract(any(), any(), any());
    }

    @Test
    void oversizedLocalFileWithoutSnapshotReportsTooLarge() {
        byte[] oversized = new byte[BoundedHttpDownloader.MAX_BYTES + 1];
        ProjectDocument document = ProjectDocument.builder()
                .id(4L).name("超大.pdf").fileUrl("doc-insight://t/huge.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/huge.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://t/huge.pdf", "/tmp/huge.pdf", "application/pdf", oversized)));
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.empty());

        TenderIntake intake = resolver.resolveIntake(PROJECT_ID);

        assertThat(intake.source()).isEmpty();
        assertThat(intake.emptyReason()).isEqualTo(BoundedHttpDownloader.TOO_LARGE_MESSAGE);
        assertThat(resolver.hasSource(PROJECT_ID)).isFalse();
    }
}
