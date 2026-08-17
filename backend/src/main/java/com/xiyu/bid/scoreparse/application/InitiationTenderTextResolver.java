package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.file.domain.FileUrlPrefixes;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 从立项招标文件取出正文。不经过 Bid Agent 导入。
 */
@Component
@Slf4j
public class InitiationTenderTextResolver {

    static final List<String> TENDER_CATEGORIES = List.of("TENDER", "TENDER_FILE");
    static final String NO_TENDER_MESSAGE = "请先在立项阶段上传招标文件";

    @FunctionalInterface
    interface UrlContentFetcher {
        byte[] get(String url) throws IOException, InterruptedException;
    }

    private final ProjectDocumentRepository projectDocumentRepository;
    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final ProjectDocumentFileStorage fileStorage;
    private final TenderDocumentStorage tenderDocumentStorage;
    private final TenderDocumentTextExtractor textExtractor;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final UrlContentFetcher urlContentFetcher;

    public InitiationTenderTextResolver(
            ProjectDocumentRepository projectDocumentRepository,
            BidTenderDocumentSnapshotRepository snapshotRepository,
            ProjectDocumentFileStorage fileStorage,
            TenderDocumentStorage tenderDocumentStorage,
            TenderDocumentTextExtractor textExtractor,
            ObsShareUrlSigner obsShareUrlSigner
    ) {
        this(projectDocumentRepository, snapshotRepository, fileStorage, tenderDocumentStorage,
                textExtractor, obsShareUrlSigner, new BoundedHttpDownloader()::get);
    }

    InitiationTenderTextResolver(
            ProjectDocumentRepository projectDocumentRepository,
            BidTenderDocumentSnapshotRepository snapshotRepository,
            ProjectDocumentFileStorage fileStorage,
            TenderDocumentStorage tenderDocumentStorage,
            TenderDocumentTextExtractor textExtractor,
            ObsShareUrlSigner obsShareUrlSigner,
            UrlContentFetcher urlContentFetcher
    ) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.snapshotRepository = snapshotRepository;
        this.fileStorage = fileStorage;
        this.tenderDocumentStorage = tenderDocumentStorage;
        this.textExtractor = textExtractor;
        this.obsShareUrlSigner = obsShareUrlSigner;
        this.urlContentFetcher = urlContentFetcher;
    }

    public boolean hasSource(Long projectId) {
        return resolveIntake(projectId).source().isPresent();
    }

    public Optional<ProjectDocument> findLatestTenderDocument(Long projectId) {
        for (String category : TENDER_CATEGORIES) {
            List<ProjectDocument> docs = projectDocumentRepository
                    .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, category, null, null);
            if (!docs.isEmpty()) {
                return docs.stream().findFirst();
            }
        }
        return Optional.empty();
    }

    public Optional<TenderTextSource> resolve(Long projectId) {
        return resolveIntake(projectId).source();
    }

    public TenderIntake resolveIntake(Long projectId) {
        Optional<ProjectDocument> document = findLatestTenderDocument(projectId);
        boolean tooLarge = false;
        if (document.isPresent()) {
            ExtractAttempt attempt = tryExtract(document.get());
            if (attempt.source().isPresent()) {
                return TenderIntake.found(attempt.source().get());
            }
            tooLarge = attempt.tooLarge();
            log.warn("立项招标文件不可用，回退历史底稿: projectId={}", projectId);
        }
        Optional<TenderTextSource> snapshot = snapshotSource(projectId);
        if (snapshot.isPresent()) {
            return TenderIntake.found(snapshot.get());
        }
        return TenderIntake.unavailable(
                tooLarge ? BoundedHttpDownloader.TOO_LARGE_MESSAGE : NO_TENDER_MESSAGE);
    }

    private ExtractAttempt tryExtract(ProjectDocument document) {
        try {
            byte[] content = loadBytes(document.getFileUrl());
            if (content == null || content.length == 0) {
                log.warn("立项招标文件无法读取: name={}", document.getName());
                return ExtractAttempt.miss();
            }
            var extracted = textExtractor.extract(document.getName(), document.getFileType(), content);
            if (extracted == null || extracted.text() == null || extracted.text().isBlank()) {
                log.warn("立项招标文件未能提取正文: name={}", document.getName());
                return ExtractAttempt.miss();
            }
            return ExtractAttempt.ok(new TenderTextSource(document.getName(), document.getFileUrl(), extracted.text()));
        } catch (RuntimeException exception) {
            log.warn("立项招标文件读取失败: name={}, msg={}", document.getName(), exception.getMessage());
            return BoundedHttpDownloader.TOO_LARGE_MESSAGE.equals(exception.getMessage())
                    ? ExtractAttempt.oversized() : ExtractAttempt.miss();
        }
    }

    private record ExtractAttempt(Optional<TenderTextSource> source, boolean tooLarge) {
        static ExtractAttempt ok(TenderTextSource source) {
            return new ExtractAttempt(Optional.of(source), false);
        }

        static ExtractAttempt miss() {
            return new ExtractAttempt(Optional.empty(), false);
        }

        static ExtractAttempt oversized() {
            return new ExtractAttempt(Optional.empty(), true);
        }
    }

    private Optional<TenderTextSource> snapshotSource(Long projectId) {
        return snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .filter(snapshot -> snapshot.getExtractedText() != null && !snapshot.getExtractedText().isBlank())
                .map(this::fromSnapshot);
    }

    private TenderTextSource fromSnapshot(BidTenderDocumentSnapshot snapshot) {
        return new TenderTextSource(snapshot.getFileName(), snapshot.getFileUrl(), snapshot.getExtractedText());
    }

    private byte[] loadBytes(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        if (fileUrl.startsWith(FileUrlPrefixes.OBS_DIRECT)) {
            return capSize(obsShareUrlSigner.trySign(fileUrl).map(this::fetchSigned).orElse(null));
        }
        Optional<byte[]> stored = fileStorage.load(fileUrl).map(LoadedProjectDocumentFile::content);
        if (stored.isPresent() && stored.get().length > 0) {
            return capSize(stored.get());
        }
        return capSize(tenderDocumentStorage.loadByFileUrl(fileUrl)
                .map(LoadedTenderDocument::content)
                .orElse(null));
    }

    private byte[] capSize(byte[] content) {
        if (content == null) {
            return null;
        }
        if (content.length > BoundedHttpDownloader.MAX_BYTES) {
            log.warn("招标文件超过 50MB，跳过该来源: bytes={}", content.length);
            throw new IllegalStateException(BoundedHttpDownloader.TOO_LARGE_MESSAGE);
        }
        return content;
    }

    private byte[] fetchSigned(String signedUrl) {
        try {
            return urlContentFetcher.get(signedUrl);
        } catch (IOException | InterruptedException exception) {
            log.warn("下载立项招标文件失败: {}", exception.getMessage());
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
}
