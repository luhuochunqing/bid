package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.file.domain.FileUrlPrefixes;
import com.xiyu.bid.scoreparse.application.LoadedBidFile;
import com.xiyu.bid.scoreparse.application.SidecarStreamTextExtractor;
import com.xiyu.bid.scoreparse.application.StreamingBidFileFetcher;
import com.xiyu.bid.scoreparse.application.TempBidFileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * 标书文档装载器（负责下载、解包、流式转换与文档元数据提取）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BidDocumentLoader {

    /**
     * 标书装载结果。
     *
     * @param markdown     标书正文；内容语义与既有实现一致（取不到正文时为占位文本或空串），
     *                     既有调用方无需因 {@code available} 而改动
     * @param documentName 标书文件名（可能为 null）
     * @param pageNumber   逻辑页码（可能为 null）
     * @param available    是否真的取到了标书正文（false = 无归档/无 BID 文件/读取失败/解析为空）。
     *                     仅对需要判断"是否具证据价值"的调用方有意义（如存量来源回填）：
     *                     false 时 markdown 不能作为"标书里没有这一节"的证据。
     */
    public record BidDocument(String markdown, String documentName, Integer pageNumber, boolean available) {}

    private final ProjectArchiveRepository archiveRepository;
    private final ArchiveFileRepository fileRepository;
    private final DocumentTextExtractor docTextExtractor;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final StreamingBidFileFetcher streamingFetcher;
    private final SidecarStreamTextExtractor streamTextExtractor;
    private final TempBidFileStore tempBidFileStore;

    /**
     * 按项目 ID 装载标书正文（projectName 仅用于无标书文件时的占位文本）。
     *
     * @return 标书正文；<b>项目无归档时返回 null</b>；归档存在但取不到正文（无 BID 文件、读取失败、
     *         解析为空）时返回 {@code available() == false} 的结果，其 {@code markdown} 为占位文本
     *         或空串（内容与既有实现一致，未变更主链路语义）。需要判断来源是否可查证的调用方
     *         （存量来源回填）必须看 {@code available()}：false 只说明来源不可查证，不等于
     *         "标书里没有这一节"。
     */
    public BidDocument loadBidDocument(String projectName, Long projectId) {
        Optional<ProjectArchive> archiveOpt = archiveRepository.findByProjectId(projectId);
        if (archiveOpt.isEmpty()) {
            log.warn("No archive found for closed project: {}", projectId);
            return null;
        }

        ProjectArchive archive = archiveOpt.get();
        List<ArchiveFile> files = fileRepository.findByArchiveId(archive.getId());
        ArchiveFile bidFile = files.stream()
                .filter(f -> "BID".equals(f.getDocumentCategory()))
                .findFirst()
                .orElse(null);

        String fileName = bidFile != null ? bidFile.getFileName() : null;
        String fallback = "# " + projectName + "\n暂无标书正文。";
        String filePath = bidFile == null ? null : bidFile.getFilePath();
        if (filePath == null) {
            return new BidDocument(fallback, fileName, null, false);
        }
        String taskId = "case-" + projectId;
        try {
            if (filePath.startsWith(FileUrlPrefixes.OBS_DIRECT)) {
                return loadObsBidDocument(filePath, bidFile, taskId);
            }
            return extractLocalBidDocument(filePath, bidFile, fallback);
        } finally {
            tempBidFileStore.release(taskId);
        }
    }

    private BidDocument loadObsBidDocument(String filePath, ArchiveFile bidFile, String taskId) {
        Optional<String> signedUrl = obsShareUrlSigner.trySign(filePath);
        if (signedUrl.isEmpty()) {
            throw new IllegalStateException("OBS 签名失败，无法下载标书: " + filePath);
        }
        LoadedBidFile loaded;
        try {
            loaded = streamingFetcher.fetch(signedUrl.get(), taskId, bidFile.getFileName(), "application/octet-stream");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("流式下载 OBS 标书失败: " + filePath, e);
        }
        switch (loaded) {
            case LoadedBidFile.InMemory mem -> {
                ExtractedDocument doc = docTextExtractor.extract(mem.fileName(), mem.contentType(), mem.content());
                String text = doc != null && doc.text() != null ? doc.text() : "";
                Integer page = extractLogicalPageNumber(doc);
                return new BidDocument(text, bidFile.getFileName(), page, hasText(text));
            }
            case LoadedBidFile.OnDisk disk -> {
                try {
                    String text = streamTextExtractor.extract(disk.tempFile(), disk.fileName());
                    return new BidDocument(text, bidFile.getFileName(), null, hasText(text));
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    String detail = e.getMessage() == null || e.getMessage().isBlank()
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                    throw new IllegalStateException("流式提取 OBS 标书文本失败: " + filePath + "，原因：" + detail, e);
                }
            }
        }
    }

    private BidDocument extractLocalBidDocument(String filePath, ArchiveFile bidFile, String fallback) {
        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            ExtractedDocument doc = docTextExtractor.extract(
                    bidFile.getFileName(), "application/octet-stream", fileBytes);
            // 正文取值与既有实现一致（null → 占位文本），不因本次新增 available 分量而改变主链路拿到的内容：
            // 事件驱动的案例沉淀会把 markdown 交给 AI 提取，喂占位文本比喂空值更糟
            String text = doc != null && doc.text() != null ? doc.text() : fallback;
            // available 只看解析结果，不看最终 text——占位文本不是正文
            boolean available = doc != null && hasText(doc.text());
            if (!available) {
                log.warn("本地标书解析为空，按文档不可用处理: {}", filePath);
            }
            return new BidDocument(text, bidFile.getFileName(), extractLogicalPageNumber(doc), available);
        } catch (RuntimeException | IOException e) {
            log.error("读取本地标书失败，按文档不可用处理: {}", filePath, e);
            return new BidDocument(fallback, bidFile.getFileName(), null, false);
        }
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private Integer extractLogicalPageNumber(ExtractedDocument doc) {
        if (doc == null || doc.extras() == null) {
            return null;
        }
        Object pageVal = doc.extras().get("pageNumber");
        if (pageVal == null) {
            pageVal = doc.extras().get("page");
        }
        if (pageVal instanceof Number num) {
            return num.intValue();
        }
        if (pageVal instanceof String str && str.matches("\\d+")) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
