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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 从立项招标文件取出正文。不经过 Bid Agent 导入。
 */
@Component
@Slf4j
public class InitiationTenderTextResolver {

    static final List<String> TENDER_CATEGORIES = List.of("TENDER", "TENDER_FILE");
    static final String NO_TENDER_MESSAGE = "请先在立项阶段上传招标文件";

    private final ProjectDocumentRepository projectDocumentRepository;
    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final ProjectDocumentFileStorage fileStorage;
    private final TenderDocumentStorage tenderDocumentStorage;
    private final TenderDocumentTextExtractor textExtractor;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final StreamingBidFileFetcher streamingFetcher;
    private final SidecarStreamTextExtractor streamTextExtractor;
    private final TempBidFileStore tempFileStore;
    private final long tenderRamBufferBytes;
    private final long tenderMaxTextChars;
    private final long tenderMaxFileBytes;

    @Autowired
    public InitiationTenderTextResolver(
            ProjectDocumentRepository projectDocumentRepository,
            BidTenderDocumentSnapshotRepository snapshotRepository,
            ProjectDocumentFileStorage fileStorage,
            TenderDocumentStorage tenderDocumentStorage,
            TenderDocumentTextExtractor textExtractor,
            ObsShareUrlSigner obsShareUrlSigner,
            StreamingBidFileFetcher streamingFetcher,
            SidecarStreamTextExtractor streamTextExtractor,
            TempBidFileStore tempFileStore,
            @Value("${app.score-parse.tender-file.ram-buffer-bytes:52428800}") long tenderRamBufferBytes,
            @Value("${app.score-parse.tender-file.max-text-chars:5000000}") long tenderMaxTextChars,
            @Value("${app.score-parse.tender-file.max-file-bytes:536870912}") long tenderMaxFileBytes
    ) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.snapshotRepository = snapshotRepository;
        this.fileStorage = fileStorage;
        this.tenderDocumentStorage = tenderDocumentStorage;
        this.textExtractor = textExtractor;
        this.obsShareUrlSigner = obsShareUrlSigner;
        this.streamingFetcher = streamingFetcher;
        this.streamTextExtractor = streamTextExtractor;
        this.tempFileStore = tempFileStore;
        this.tenderRamBufferBytes = tenderRamBufferBytes;
        this.tenderMaxTextChars = tenderMaxTextChars;
        this.tenderMaxFileBytes = tenderMaxFileBytes;
    }

    InitiationTenderTextResolver(
            ProjectDocumentRepository projectDocumentRepository,
            BidTenderDocumentSnapshotRepository snapshotRepository,
            ProjectDocumentFileStorage fileStorage,
            TenderDocumentStorage tenderDocumentStorage,
            TenderDocumentTextExtractor textExtractor,
            ObsShareUrlSigner obsShareUrlSigner,
            StreamingBidFileFetcher streamingFetcher,
            SidecarStreamTextExtractor streamTextExtractor,
            TempBidFileStore tempFileStore,
            long tenderMaxTextChars
    ) {
        this(projectDocumentRepository, snapshotRepository, fileStorage, tenderDocumentStorage,
                textExtractor, obsShareUrlSigner, streamingFetcher, streamTextExtractor,
                tempFileStore, 52428800L, tenderMaxTextChars, 536870912L);
    }

    InitiationTenderTextResolver(
            ProjectDocumentRepository projectDocumentRepository,
            BidTenderDocumentSnapshotRepository snapshotRepository,
            ProjectDocumentFileStorage fileStorage,
            TenderDocumentStorage tenderDocumentStorage,
            TenderDocumentTextExtractor textExtractor,
            ObsShareUrlSigner obsShareUrlSigner,
            StreamingBidFileFetcher streamingFetcher,
            SidecarStreamTextExtractor streamTextExtractor,
            TempBidFileStore tempFileStore,
            long tenderMaxTextChars,
            long tenderMaxFileBytes
    ) {
        this(projectDocumentRepository, snapshotRepository, fileStorage, tenderDocumentStorage,
                textExtractor, obsShareUrlSigner, streamingFetcher, streamTextExtractor,
                tempFileStore, 52428800L, tenderMaxTextChars, tenderMaxFileBytes);
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
        String oversizedReason = null;
        if (document.isPresent()) {
            ExtractAttempt attempt = tryExtract(document.get());
            if (attempt.source().isPresent()) {
                return TenderIntake.found(attempt.source().get());
            }
            oversizedReason = attempt.oversizedReason();
            log.warn("立项招标文件不可用，回退历史底稿: projectId={}", projectId);
        }
        Optional<TenderTextSource> snapshot = snapshotSource(projectId);
        if (snapshot.isPresent()) {
            return TenderIntake.found(snapshot.get());
        }
        return TenderIntake.unavailable(
                oversizedReason != null ? oversizedReason : NO_TENDER_MESSAGE);
    }

    private ExtractAttempt tryExtract(ProjectDocument document) {
        LoadedBidFile loaded = null;
        try {
            loaded = loadTenderFile(
                    document.getFileUrl(), document.getName(), document.getFileType());
            String text = loaded == null ? null : extractLoadedTextNoRelease(loaded, document);
            if (text == null || text.isBlank()) {
                log.warn("立项招标文件未能提取正文: name={}", document.getName());
                releaseTenderFile(loaded);
                return ExtractAttempt.miss();
            }
            return ExtractAttempt.ok(new TenderTextSource(document.getName(), document.getFileUrl(), text, loaded));
        } catch (OversizedTenderTextException | TenderFileQuotaException exception) {
            releaseTenderFile(loaded);
            return ExtractAttempt.oversized(exception.getMessage());
        } catch (BidFileQuotaException exception) {
            releaseTenderFile(loaded);
            return ExtractAttempt.oversized(TenderFileQuotaException.MESSAGE);
        } catch (RuntimeException exception) {
            releaseTenderFile(loaded);
            log.warn("立项招标文件读取失败: name={}, msg={}", document.getName(), exception.getMessage());
            return ExtractAttempt.miss();
        }
    }

    private record ExtractAttempt(Optional<TenderTextSource> source, String oversizedReason) {
        static ExtractAttempt ok(TenderTextSource source) {
            return new ExtractAttempt(Optional.of(source), null);
        }

        static ExtractAttempt miss() {
            return new ExtractAttempt(Optional.empty(), null);
        }

        static ExtractAttempt oversized(String reason) {
            return new ExtractAttempt(Optional.empty(), reason);
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

    /** 供 {@link ScoreParseAppService} 结构化表格提取通道复用，加载招标文件（内存快路径或落盘）。 */
    public LoadedBidFile loadTenderFile(String fileUrl, String fileName, String contentType) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        if (fileUrl.startsWith(FileUrlPrefixes.OBS_DIRECT)) {
            Optional<String> signed = obsShareUrlSigner.trySign(fileUrl);
            if (signed.isEmpty() || signed.get().isBlank()) {
                return null;
            }
            try {
                // 流式自适应 spill：≤内存快路径走内存，超限落盘；单文件超配额抛 BidFileQuotaException
                return streamingFetcher.fetch(signed.get(), "tender-" + UUID.randomUUID(), fileName, contentType, tenderRamBufferBytes, tenderMaxFileBytes);
            } catch (IOException | InterruptedException exception) {
                log.warn("下载立项招标文件失败: {}", exception.getMessage());
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }
        }
        Optional<byte[]> stored = fileStorage.load(fileUrl).map(LoadedProjectDocumentFile::content);
        if (stored.isPresent() && stored.get().length > 0) {
            requireWithinFileQuota(stored.get().length, fileName);
            return new LoadedBidFile.InMemory(stored.get(), fileName, contentType);
        }
        return tenderDocumentStorage.loadByFileUrl(fileUrl)
                .map(LoadedTenderDocument::content)
                .filter(content -> content.length > 0)
                .map(content -> {
                    requireWithinFileQuota(content.length, fileName);
                    return (LoadedBidFile) new LoadedBidFile.InMemory(content, fileName, contentType);
                })
                .orElse(null);
    }

    private void requireWithinFileQuota(long bytes, String fileName) {
        if (bytes > tenderMaxFileBytes) {
            log.warn("招标文件超过字节配额，跳过该来源: name={}, bytes={}", fileName, bytes);
            throw new TenderFileQuotaException();
        }
    }

    /** 释放落盘临时文件（配额归还）；内存态、null 与非托管路径为 no-op。 */
    void releaseTenderFile(LoadedBidFile loaded) {
        if (loaded instanceof LoadedBidFile.OnDisk disk) {
            tempFileStore.releaseFile(disk.tempFile());
        }
    }

    private String extractLoadedTextNoRelease(LoadedBidFile loaded, ProjectDocument document) {
        if (loaded instanceof LoadedBidFile.InMemory memory) {
            var extracted = textExtractor.extract(document.getName(), document.getFileType(), memory.content());
            String text = extracted == null ? null : extracted.text();
            if (text != null && text.length() > tenderMaxTextChars) {
                throw new OversizedTenderTextException(tenderMaxTextChars);
            }
            return text;
        }
        LoadedBidFile.OnDisk disk = (LoadedBidFile.OnDisk) loaded;
        try {
            return streamTextExtractor.extract(disk.tempFile(), document.getName(), tenderMaxTextChars);
        } catch (OversizedBidTextException exception) {
            throw new OversizedTenderTextException(tenderMaxTextChars);
        } catch (IOException | InterruptedException exception) {
            log.warn("超大招标文件流式提取失败: name={}, msg={}", document.getName(), exception.getMessage());
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
}
