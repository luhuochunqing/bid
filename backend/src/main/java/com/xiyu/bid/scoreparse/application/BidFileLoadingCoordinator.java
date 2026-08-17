// Input: projectId + taskId（加载投标文件并提取正文）
// Output: LoadedBidDocument(text, contentHash)；OnDisk 临时文件用毕即释（finally release）
// Pos: scoreparse/application — spec 045 T011：二态分派（InMemory 既有提取器 / OnDisk sidecar 流式）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 打分链路投标文件加载协调器（spec 045 T011）。
 * <p>loadBidFile 二态分派：InMemory 走既有 textExtractor（快路径，行为与现网一致）；
 * OnDisk 走 SidecarStreamTextExtractor（流式上传 + char 计数）。
 * contentHash 与 BidScoreSkipPolicy.hashBytes 同口径，skip 策略跨加载路径可比。
 * OnDisk 临时文件在提取完成后立即 release（早于任务终态，配额占用最小化）。
 */
@Component
@Slf4j
public class BidFileLoadingCoordinator {

    /** 提取结果值对象：正文文本 + 原始字节哈希（skip 策略输入）。 */
    public record LoadedBidDocument(String text, String contentHash) {}

    private final ProjectDocumentRepository projectDocumentRepository;
    private final TenderDocumentStorage documentStorage;
    private final TenderDocumentTextExtractor textExtractor;
    private final ProjectDocumentFileStorage fileStorage;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final StreamingBidFileFetcher streamingFetcher;
    private final SidecarStreamTextExtractor streamTextExtractor;
    private final TempBidFileStore tempFileStore;

    public BidFileLoadingCoordinator(
            ProjectDocumentRepository projectDocumentRepository,
            TenderDocumentStorage documentStorage,
            TenderDocumentTextExtractor textExtractor,
            ProjectDocumentFileStorage fileStorage,
            ObsShareUrlSigner obsShareUrlSigner,
            StreamingBidFileFetcher streamingFetcher,
            SidecarStreamTextExtractor streamTextExtractor,
            TempBidFileStore tempFileStore) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.documentStorage = documentStorage;
        this.textExtractor = textExtractor;
        this.fileStorage = fileStorage;
        this.obsShareUrlSigner = obsShareUrlSigner;
        this.streamingFetcher = streamingFetcher;
        this.streamTextExtractor = streamTextExtractor;
        this.tempFileStore = tempFileStore;
    }

    public LoadedBidDocument load(Long projectId, String taskId) {
        LoadedBidFile file = newLookup().loadBidFile(projectId, taskId);
        try {
            if (file instanceof LoadedBidFile.InMemory inMemory) {
                return extractInMemory(inMemory);
            }
            return extractOnDisk((LoadedBidFile.OnDisk) file);
        } finally {
            if (file instanceof LoadedBidFile.OnDisk) {
                tempFileStore.release(taskId);
            }
        }
    }

    /** 同步触发段 skip 判断用：仅计算内容哈希（不提取文本）；OnDisk 用毕即释。 */
    public String contentHashOnly(ProjectDocument bidDoc, String taskId) {
        LoadedBidFile file = newLookup().loadBidFile(bidDoc, taskId);
        try {
            return file instanceof LoadedBidFile.InMemory inMemory
                    ? BidScoreSkipPolicy.hashBytes(inMemory.content())
                    : BidScoreSkipPolicy.hashFile(((LoadedBidFile.OnDisk) file).tempFile());
        } finally {
            if (file instanceof LoadedBidFile.OnDisk) {
                tempFileStore.release(taskId);
            }
        }
    }

    public List<ProjectDocument> findBidDocuments(Long projectId) {
        return newLookup().findBidDocuments(projectId);
    }

    private ScoreBidDocumentLookup newLookup() {
        return new ScoreBidDocumentLookup(
                projectDocumentRepository, documentStorage, fileStorage, obsShareUrlSigner, streamingFetcher);
    }

    private LoadedBidDocument extractInMemory(LoadedBidFile.InMemory file) {
        ExtractedTenderDocument extracted =
                textExtractor.extract(file.fileName(), file.contentType(), file.content());
        String text = extracted == null ? null : extracted.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("投标文件未能提取出有效正文: " + file.fileName());
        }
        return new LoadedBidDocument(text, BidScoreSkipPolicy.hashBytes(file.content()));
    }

    private LoadedBidDocument extractOnDisk(LoadedBidFile.OnDisk file) {
        String contentHash = BidScoreSkipPolicy.hashFile(file.tempFile());
        String text;
        try {
            text = streamTextExtractor.extract(file.tempFile(), file.fileName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("投标文件流式提取中断: " + file.fileName(), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("投标文件流式提取失败: " + file.fileName(), ex);
        }
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("投标文件未能提取出有效正文: " + file.fileName());
        }
        return new LoadedBidDocument(text, contentHash);
    }
}
