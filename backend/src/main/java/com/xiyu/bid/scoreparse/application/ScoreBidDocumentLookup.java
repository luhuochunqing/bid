package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.file.domain.FileUrlPrefixes;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 投标文件查找与正文读取。
 * <p>支持华为云 OBS 对象存储直传 (obs-direct:xxx)、本地 ProjectDocumentFileStorage 和 TenderDocumentStorage。
 */
@Slf4j
final class ScoreBidDocumentLookup {

    @FunctionalInterface
    interface UrlFetcher {
        byte[] get(String url) throws IOException, InterruptedException;
    }

    private final ProjectDocumentRepository projectDocumentRepository;
    private final TenderDocumentStorage documentStorage;
    private final TenderDocumentTextExtractor textExtractor;
    private final ProjectDocumentFileStorage fileStorage;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final UrlFetcher urlFetcher;

    ScoreBidDocumentLookup(ProjectDocumentRepository projectDocumentRepository,
                           TenderDocumentStorage documentStorage,
                           TenderDocumentTextExtractor textExtractor) {
        this(projectDocumentRepository, documentStorage, textExtractor, null, null, null);
    }

    ScoreBidDocumentLookup(ProjectDocumentRepository projectDocumentRepository,
                           TenderDocumentStorage documentStorage,
                           TenderDocumentTextExtractor textExtractor,
                           ProjectDocumentFileStorage fileStorage,
                           ObsShareUrlSigner obsShareUrlSigner) {
        this(projectDocumentRepository, documentStorage, textExtractor, fileStorage, obsShareUrlSigner, null);
    }

    ScoreBidDocumentLookup(ProjectDocumentRepository projectDocumentRepository,
                           TenderDocumentStorage documentStorage,
                           TenderDocumentTextExtractor textExtractor,
                           ProjectDocumentFileStorage fileStorage,
                           ObsShareUrlSigner obsShareUrlSigner,
                           UrlFetcher urlFetcher) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.documentStorage = documentStorage;
        this.textExtractor = textExtractor;
        this.fileStorage = fileStorage;
        this.obsShareUrlSigner = obsShareUrlSigner;
        this.urlFetcher = urlFetcher != null ? urlFetcher : new BoundedHttpDownloader()::get;
    }

    String latestName(Long projectId) {
        List<ProjectDocument> docs = findBidDocuments(projectId);
        return docs.isEmpty() ? null : docs.get(0).getName();
    }

    List<ProjectDocument> findBidDocuments(Long projectId) {
        for (String cat : List.of("BID", "BID_FILE", "BID_DOCUMENT")) {
            List<ProjectDocument> docs = projectDocumentRepository
                    .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, cat, null, null);
            if (!docs.isEmpty()) {
                return docs;
            }
        }
        return List.of();
    }

    byte[] loadBytes(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalStateException("投标文件 fileUrl 为空");
        }
        // 1. OBS 直传链接 (obs-direct:xxx)
        if (fileUrl.startsWith(FileUrlPrefixes.OBS_DIRECT) && obsShareUrlSigner != null) {
            Optional<String> signedUrl = obsShareUrlSigner.trySign(fileUrl);
            if (signedUrl.isPresent()) {
                try {
                    byte[] data = urlFetcher.get(signedUrl.get());
                    if (data != null && data.length > 0) {
                        return capSize(data);
                    }
                } catch (IOException | InterruptedException | RuntimeException ex) {
                    // 超限必须立即中止，不能吞掉后走 fallback 链路（fallback 同样会 OOM）
                    if (BoundedHttpDownloader.TOO_LARGE_MESSAGE.equals(ex.getMessage())) {
                        throw new OversizedBidFileException();
                    }
                    log.warn("通过 OBS 签名链接下载投标文件失败: fileUrl={}, msg={}", fileUrl, ex.getMessage());
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        // 2. ProjectDocumentFileStorage 本地/挂载存储
        if (fileStorage != null) {
            Optional<byte[]> stored = fileStorage.load(fileUrl).map(LoadedProjectDocumentFile::content);
            if (stored.isPresent() && stored.get().length > 0) {
                return capSize(stored.get());
            }
        }
        // 3. TenderDocumentStorage (doc-insight://xxx 等)
        if (documentStorage != null) {
            Optional<byte[]> loaded = documentStorage.loadByFileUrl(fileUrl).map(LoadedTenderDocument::content);
            if (loaded.isPresent() && loaded.get().length > 0) {
                return capSize(loaded.get());
            }
        }
        throw new IllegalStateException("投标文件加载失败: " + fileUrl);
    }

    private static byte[] capSize(byte[] content) {
        if (content.length > BoundedHttpDownloader.MAX_BYTES) {
            log.warn("投标文件超过 {}MB 上限，拒绝加载: bytes={}",
                    BoundedHttpDownloader.MAX_BYTES / 1024 / 1024, content.length);
            throw new OversizedBidFileException();
        }
        return content;
    }

    String loadText(Long projectId) {
        List<ProjectDocument> bidDocs = findBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            throw new IllegalStateException("投标文件不存在，项目: " + projectId);
        }
        ProjectDocument bidDoc = bidDocs.get(0);
        byte[] content = loadBytes(bidDoc.getFileUrl());
        ExtractedTenderDocument extracted = textExtractor.extract(bidDoc.getName(), bidDoc.getFileType(), content);
        if (extracted == null || extracted.text() == null || extracted.text().isBlank()) {
            throw new IllegalStateException("投标文件未能提取出有效正文: " + bidDoc.getName());
        }
        return extracted.text();
    }
}
