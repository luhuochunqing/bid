// Input: 项目文档 fileUrl + 文件名
// Output: 按存储类型分派取流后的本地临时文件（交 Sidecar 解析）
// Pos: compliance/service/ - 投标文件存储分派解析器（spec 049 US1，research.md R1）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentStorageType;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 投标文件存储分派解析器.
 *
 * <p>按 {@link ProjectDocumentStorageType} 三前缀分派真实取流（复用
 * ProjectDocumentDownloadService 的分派模式，research.md R1），输出可供
 * Sidecar 解析的本地临时文件。禁止元数据降级：未知前缀与存储缺失均抛业务异常.
 */
@Component
@Slf4j
public class BidDocumentStorageResolver {

    /** 项目文档存储（bid-agent:// / doc-insight:// 流式读取）. */
    private final ProjectDocumentFileStorage fileStorage;
    /** OBS 直传预签名签发器. */
    private final ObsShareUrlSigner obsShareUrlSigner;

    public BidDocumentStorageResolver(
            final ProjectDocumentFileStorage fileStorage,
            final ObsShareUrlSigner obsShareUrlSigner) {
        this.fileStorage = fileStorage;
        this.obsShareUrlSigner = obsShareUrlSigner;
    }

    /**
     * 按存储类型分派取流，输出本地临时文件.
     *
     * @param fileUrl 项目文档 fileUrl
     * @param fileName 文件名（用于临时文件后缀）
     * @return 本地临时文件路径（调用方负责清理）
     * @throws BusinessException 未知前缀或存储读不到文件时（禁止降级）
     */
    public Path resolvePhysicalFile(final String fileUrl, final String fileName) {
        return resolvePhysicalFile(fileUrl, fileName, "投标文件");
    }

    /**
     * 按存储类型分派取流，输出本地临时文件（带文档类型标签，用于错误文案）.
     */
    public Path resolvePhysicalFile(final String fileUrl, final String fileName, final String docLabel) {
        ProjectDocumentStorageType storageType = ProjectDocumentStorageType.fromFileUrl(fileUrl);
        switch (storageType) {
            case LOCAL_BID_AGENT:
            case DOC_INSIGHT:
                LoadedProjectDocumentFile loaded = fileStorage.load(fileUrl)
                        .orElseThrow(() -> new BusinessException(404,
                                docLabel + "在存储中不存在，请重新上传：" + fileUrl));
                return writeTempFile(loaded.content(), docLabel);
            case OBS_DIRECT:
                String presignedUrl = obsShareUrlSigner.trySign(fileUrl)
                        .orElseThrow(() -> new BusinessException(404,
                                docLabel + "预签名下载失败，请重新上传：" + fileUrl));
                return downloadToTempFile(presignedUrl, fileName);
            default:
                throw new BusinessException(422,
                        docLabel + "存储类型无法识别，请重新上传：" + fileUrl);
        }
    }

    /**
     * 将存储读取的字节写入临时文件.
     */
    private Path writeTempFile(final byte[] content, final String docLabel) {
        if (content == null || content.length == 0) {
            throw new BusinessException(404, docLabel + "内容为空，请重新上传");
        }
        try {
            Path tempFile = Files.createTempFile("xiyu-quality-doc-", ".pdf");
            Files.write(tempFile, content);
            return tempFile;
        } catch (IOException e) {
            throw new BusinessException(500, "写入" + docLabel + "临时文件失败", e);
        }
    }

    /**
     * 从预签名 URL 下载文件到临时文件（OBS 直传链路）.
     * protected 以便单测覆写，避免真实 HTTP 调用.
     */
    protected Path downloadToTempFile(final String url, final String fileName) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(502,
                        "投标文件下载失败，OBS 返回状态码 " + response.statusCode());
            }
            Path tempFile = Files.createTempFile("xiyu-quality-bid-", suffixOf(fileName));
            try (InputStream in = response.body()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "投标文件下载异常：" + e.getMessage(), e);
        }
    }

    private static String suffixOf(final String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : ".pdf";
    }
}
