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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
                textExtractor, obsShareUrlSigner, InitiationTenderTextResolver::httpGet);
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
        return findLatestTenderDocument(projectId).isPresent()
                || snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId).isPresent();
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
        Optional<TenderTextSource> fromInitiation = findLatestTenderDocument(projectId)
                .map(this::extractFromDocument);
        if (fromInitiation.isPresent()) {
            return fromInitiation;
        }
        return snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .filter(snapshot -> snapshot.getExtractedText() != null && !snapshot.getExtractedText().isBlank())
                .map(this::fromSnapshot);
    }

    private TenderTextSource extractFromDocument(ProjectDocument document) {
        byte[] content = loadBytes(document.getFileUrl());
        if (content == null || content.length == 0) {
            throw new IllegalStateException("立项招标文件无法读取，请重新上传");
        }
        var extracted = textExtractor.extract(document.getName(), document.getFileType(), content);
        if (extracted == null || extracted.text() == null || extracted.text().isBlank()) {
            throw new IllegalStateException("立项招标文件未能提取正文，请检查文件是否为可读 PDF/Word");
        }
        return new TenderTextSource(document.getName(), document.getFileUrl(), extracted.text());
    }

    private TenderTextSource fromSnapshot(BidTenderDocumentSnapshot snapshot) {
        return new TenderTextSource(snapshot.getFileName(), snapshot.getFileUrl(), snapshot.getExtractedText());
    }

    private byte[] loadBytes(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        if (fileUrl.startsWith(FileUrlPrefixes.OBS_DIRECT)) {
            return obsShareUrlSigner.trySign(fileUrl)
                    .map(this::fetchSigned)
                    .orElse(null);
        }
        Optional<byte[]> stored = fileStorage.load(fileUrl).map(LoadedProjectDocumentFile::content);
        if (stored.isPresent() && stored.get().length > 0) {
            return stored.get();
        }
        return tenderDocumentStorage.loadByFileUrl(fileUrl)
                .map(LoadedTenderDocument::content)
                .orElse(null);
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

    private static byte[] httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("下载招标文件失败 HTTP " + response.statusCode());
        }
        return response.body();
    }
}
