// Input: projectId + taskId（加载投标文件并提取正文与图片）
// Output: LoadedBidDocument(text, contentHash, images)；OnDisk 临时文件用毕即释（finally release）
// Pos: scoreparse/application — spec 045 T011 二态分派 + spec 048 T013 图片接入（双路径抽图）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 打分链路投标文件加载协调器（spec 045 T011 + spec 048 T013）。
 * <p>loadBidFile 二态分派：InMemory 走既有 textExtractor（快路径，行为与现网一致）；
 * OnDisk 走 SidecarStreamTextExtractor（流式上传 + char 计数）。
 * contentHash 与 BidScoreSkipPolicy.hashBytes 同口径，skip 策略跨加载路径可比。
 * OnDisk 临时文件在提取完成后立即 release（早于任务终态，配额占用最小化）。
 * <p>spec 048：两路径均经 SidecarImageExtractor 抽图（InMemory byte[] 直传 / OnDisk ofFile 流式，
 * review H2），抽图失败/超限降级空图不阻断打分（Principle VII）；multimodal 开关关闭时跳过抽图（FR-006）。
 */
@Component
@Slf4j
public class BidFileLoadingCoordinator {

    /** 提取结果值对象：正文文本 + 原始字节哈希（skip 策略输入）+ 图片清单（多模态打分输入）。 */
    public record LoadedBidDocument(String text, String contentHash, List<DocumentImage> images) {

        public LoadedBidDocument(String text, String contentHash) {
            this(text, contentHash, List.of());
        }
    }

    private final ProjectDocumentRepository projectDocumentRepository;
    private final TenderDocumentStorage documentStorage;
    private final TenderDocumentTextExtractor textExtractor;
    private final ProjectDocumentFileStorage fileStorage;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final StreamingBidFileFetcher streamingFetcher;
    private final SidecarStreamTextExtractor streamTextExtractor;
    private final TempBidFileStore tempFileStore;
    private final SidecarImageExtractor imageExtractor;
    private final boolean multimodalEnabled;

    public BidFileLoadingCoordinator(
            ProjectDocumentRepository projectDocumentRepository,
            TenderDocumentStorage documentStorage,
            TenderDocumentTextExtractor textExtractor,
            ProjectDocumentFileStorage fileStorage,
            ObsShareUrlSigner obsShareUrlSigner,
            StreamingBidFileFetcher streamingFetcher,
            SidecarStreamTextExtractor streamTextExtractor,
            TempBidFileStore tempFileStore,
            SidecarImageExtractor imageExtractor,
            @Value("${app.score-parse.multimodal.enabled:true}") boolean multimodalEnabled) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.documentStorage = documentStorage;
        this.textExtractor = textExtractor;
        this.fileStorage = fileStorage;
        this.obsShareUrlSigner = obsShareUrlSigner;
        this.streamingFetcher = streamingFetcher;
        this.streamTextExtractor = streamTextExtractor;
        this.tempFileStore = tempFileStore;
        this.imageExtractor = imageExtractor;
        this.multimodalEnabled = multimodalEnabled;
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
        String text;
        if ("application/pdf".equalsIgnoreCase(file.contentType())
                || file.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            try {
                text = streamTextExtractor.extractForScoring(file.content(), file.fileName());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("投标文件提取中断", ex);
            } catch (IOException ex) {
                throw new IllegalStateException("投标文件提取失败", ex);
            }
        } else {
            ExtractedTenderDocument extracted = textExtractor.extract(file.fileName(), file.contentType(), file.content());
            text = extracted == null ? null : extracted.text();
        }
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("投标文件未能提取出有效正文: " + file.fileName());
        }
        return new LoadedBidDocument(
                text,
                BidScoreSkipPolicy.hashBytes(file.content()),
                extractImages(file.content(), file.fileName()));
    }

    private LoadedBidDocument extractOnDisk(LoadedBidFile.OnDisk file) {
        String contentHash = BidScoreSkipPolicy.hashFile(file.tempFile());
        String text;
        try {
            text = streamTextExtractor.extractForScoring(file.tempFile(), file.fileName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("投标文件流式提取中断: " + file.fileName(), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("投标文件流式提取失败: " + file.fileName(), ex);
        }
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("投标文件未能提取出有效正文: " + file.fileName());
        }
        return new LoadedBidDocument(text, contentHash, extractImages(file.tempFile(), file.fileName()));
    }

    /** InMemory 抽图：全量 byte[] 直传（review H2）。 */
    private List<DocumentImage> extractImages(byte[] content, String fileName) {
        if (!multimodalEnabled) {
            return List.of();
        }
        try {
            return imageExtractor.extract(content, fileName).images();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("投标文件图片抽取中断，打分降级纯文本: file={}", fileName);
            return List.of();
        } catch (IOException | RuntimeException ex) {
            log.warn("投标文件图片抽取失败，打分降级纯文本: file={}, msg={}", fileName, ex.getMessage());
            return List.of();
        }
    }

    /** OnDisk 抽图：流式上传（ofFile）。 */
    private List<DocumentImage> extractImages(Path file, String fileName) {
        if (!multimodalEnabled) {
            return List.of();
        }
        try {
            return imageExtractor.extract(file, fileName).images();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("投标文件图片抽取中断，打分降级纯文本: file={}", fileName);
            return List.of();
        } catch (IOException | RuntimeException ex) {
            log.warn("投标文件图片抽取失败，打分降级纯文本: file={}, msg={}", fileName, ex.getMessage());
            return List.of();
        }
    }
}
