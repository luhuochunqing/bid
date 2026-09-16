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

    public record BidDocument(String markdown, String documentName, Integer pageNumber) {}

    private final ProjectArchiveRepository archiveRepository;
    private final ArchiveFileRepository fileRepository;
    private final DocumentTextExtractor docTextExtractor;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final StreamingBidFileFetcher streamingFetcher;
    private final SidecarStreamTextExtractor streamTextExtractor;
    private final TempBidFileStore tempBidFileStore;

    public BidDocument loadBidDocument(ProjectClosedEvent event, Long projectId) {
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
        String fallback = "# " + event.getProjectName() + "\n暂无标书正文。";
        String filePath = bidFile == null ? null : bidFile.getFilePath();
        if (filePath == null) {
            return new BidDocument(fallback, fileName, null);
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
                return new BidDocument(text, bidFile.getFileName(), page);
            }
            case LoadedBidFile.OnDisk disk -> {
                try {
                    String text = streamTextExtractor.extract(disk.tempFile(), disk.fileName());
                    return new BidDocument(text, bidFile.getFileName(), null);
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
            String text = doc != null && doc.text() != null ? doc.text() : fallback;
            Integer page = extractLogicalPageNumber(doc);
            return new BidDocument(text, bidFile.getFileName(), page);
        } catch (RuntimeException | IOException e) {
            log.error("读取本地标书失败，回退占位文本: {}", filePath, e);
            return new BidDocument(fallback, bidFile.getFileName(), null);
        }
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
