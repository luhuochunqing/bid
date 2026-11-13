// Input: projectId + taskId（定位投标文件并按二态加载）
// Output: LoadedBidFile（OBS 流式自适应 spill / 本地与 doc-insight 内存快路径）
// Pos: scoreparse/application — spec 045 T010：loadBytes 单态 → loadBidFile 二态，capSize 字节硬限废止
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
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
 * 投标文件查找与二态加载（spec 045）。
 * <p>OBS 直传走 StreamingBidFileFetcher 流式自适应 spill（≤50MB 内存 / 超限落盘）；
 * 本地 ProjectDocumentFileStorage 与 TenderDocumentStorage 链路维持 byte[] 快路径。
 * 字节大小硬限（capSize/OversizedBidFileException）已废止，限制口径统一为文本量/磁盘配额。
 */
@Slf4j
final class ScoreBidDocumentLookup {

    private final ProjectDocumentRepository projectDocumentRepository;
    private final TenderDocumentStorage documentStorage;
    private final ProjectDocumentFileStorage fileStorage;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final StreamingBidFileFetcher streamingFetcher;

    ScoreBidDocumentLookup(ProjectDocumentRepository projectDocumentRepository,
                           TenderDocumentStorage documentStorage,
                           ProjectDocumentFileStorage fileStorage,
                           ObsShareUrlSigner obsShareUrlSigner,
                           StreamingBidFileFetcher streamingFetcher) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.documentStorage = documentStorage;
        this.fileStorage = fileStorage;
        this.obsShareUrlSigner = obsShareUrlSigner;
        this.streamingFetcher = streamingFetcher;
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

    /** 加载项目最新投标文件（二态）；OnDisk 临时文件以 {tempDir}/{taskId}.part 落盘。 */
    LoadedBidFile loadBidFile(Long projectId, String taskId) {
        List<ProjectDocument> bidDocs = findBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            throw new IllegalStateException("投标文件不存在，项目: " + projectId);
        }
        return loadBidFile(bidDocs.get(0), taskId);
    }

    LoadedBidFile loadBidFile(ProjectDocument bidDoc, String taskId) {
        return loadBidFile(bidDoc,taskId,Long.MAX_VALUE);
    }
    LoadedBidFile loadBidFile(ProjectDocument bidDoc, String taskId,long deadline) {
        ScoringDeadline.check(deadline);
        String fileUrl = bidDoc.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalStateException("投标文件 fileUrl 为空");
        }
        // 1. OBS 直传链接 (obs-direct:xxx)：流式自适应 spill（配额拒绝立即中止，不走 fallback）
        if (fileUrl.startsWith(FileUrlPrefixes.OBS_DIRECT) && obsShareUrlSigner != null && streamingFetcher != null) {
            Optional<String> signedUrl = obsShareUrlSigner.trySign(fileUrl);
            if (signedUrl.isPresent()) {
                try {
                    return deadline==Long.MAX_VALUE
                            ? streamingFetcher.fetch(signedUrl.get(),taskId,bidDoc.getName(),bidDoc.getFileType())
                            : streamingFetcher.fetchWithDeadline(signedUrl.get(),taskId,bidDoc.getName(),bidDoc.getFileType(),deadline);
                } catch (BidFileQuotaException ex) {
                    throw ex;
                } catch (IOException | InterruptedException | RuntimeException ex) {
                    ScoringDeadline.check(deadline);
                    if(deadline!=Long.MAX_VALUE) {
                        if(ex instanceof InterruptedException)Thread.currentThread().interrupt();
                        throw new IllegalStateException("SCORING_DOWNLOAD_FAILED",ex);
                    }
                    log.warn("通过 OBS 签名链接下载投标文件失败: fileUrl={}, msg={}", fileUrl, ex.getMessage());
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        // 2. ProjectDocumentFileStorage 本地/挂载存储（内存快路径，无字节上限）
        if (fileStorage != null) {
            Optional<byte[]> stored = fileStorage.load(fileUrl).map(LoadedProjectDocumentFile::content);
            if (stored.isPresent() && stored.get().length > 0) {
                return new LoadedBidFile.InMemory(stored.get(), bidDoc.getName(), bidDoc.getFileType());
            }
        }
        // 3. TenderDocumentStorage (doc-insight://xxx 等，内存快路径)
        if (documentStorage != null) {
            Optional<byte[]> loaded = documentStorage.loadByFileUrl(fileUrl).map(LoadedTenderDocument::content);
            if (loaded.isPresent() && loaded.get().length > 0) {
                return new LoadedBidFile.InMemory(loaded.get(), bidDoc.getName(), bidDoc.getFileType());
            }
        }
        throw new IllegalStateException("投标文件加载失败: " + fileUrl);
    }
}
