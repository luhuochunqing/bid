package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.dto.PerformanceAttachmentUploadDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 业绩附件文件存储服务（CO-442）。
 *
 * <p>负责把前端上传的 multipart 文件落盘到本地目录，返回 fileName/fileUrl。
 * 不依赖 performanceId（新增业绩时还没有 id），文件路径按 fileType 分目录。
 *
 * <p>路径陷阱防护参考 LL-028 与 brandAuth AttachmentUploadAppService：
 * uploadDir 相对路径时按 JVM 工作目录归一化为绝对路径，
 * 避免 MultipartFile.transferTo(File) 走 Tomcat 临时目录。
 */
@Service
public class PerformanceAttachmentStorageAppService {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/jpg");
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "CONTRACT_AGREEMENT", "MALL_SCREENSHOT", "SOE_DIRECTORY",
            "CATEGORY_PAGE", "RELATIONSHIP_PROOF", "BID_NOTICE", "OTHER");

    @Value("${app.upload.performance-dir:uploads/performance-attachments}")
    private String uploadDir;

    /** 批量导入附件的存储根目录（与 PerformanceImportAttachmentProcessor 共用同一配置） */
    @Value("${performance.attachment.root:/data/attachments/performance}")
    private String attachmentRoot;

    public PerformanceAttachmentUploadDTO upload(String fileType, MultipartFile file) throws IOException {
        validateFileType(fileType);
        validateFile(file);

        Path uploadPath = resolveAbsoluteUploadPath().resolve(fileType);
        Files.createDirectories(uploadPath);

        String storedName = UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path dest = uploadPath.resolve(storedName).toAbsolutePath().normalize();
        file.transferTo(dest);

        return new PerformanceAttachmentUploadDTO(file.getOriginalFilename(), dest.toString());
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空: " + file.getOriginalFilename());
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件超过20MB: " + file.getOriginalFilename());
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "不支持的文件格式: " + file.getContentType() + " (仅支持 PDF/JPG/PNG)");
        }
    }

    private void validateFileType(String fileType) {
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new IllegalArgumentException("非法附件类型: " + fileType);
        }
    }

    private static String sanitizeFilename(String name) {
        return name == null ? "file" : name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
    }

    /**
     * 将 uploadDir 归一化为绝对路径。
     * 相对路径按 JVM 工作目录解析，确保
     * Files.createDirectories 与 MultipartFile.transferTo 使用同一基准。
     */
    private Path resolveAbsoluteUploadPath() {
        Path p = Paths.get(uploadDir);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
        }
        return p;
    }

    // ── 文件读取（供 ZIP 导出 + 附件下载端点使用） ──────────────

    /**
     * 根据 fileUrl 读取附件文件字节.
     *
     * <p>fileUrl 有两种来源格式：
     * <ol>
     *   <li>页面上传 → 本地磁盘绝对路径（如 /opt/xiyu-bid/.../uuid_file.pdf）</li>
     *   <li>批量导入 → 相对路径（如 /123/PF_123_CONTRACT_AGREEMENT_xxx.pdf）</li>
     * </ol>
     * 两种格式都不是 HTTP URL，必须从本地磁盘读取。
     *
     * @param fileUrl 数据库中存储的附件路径
     * @return 文件字节数组
     * @throws IOException 文件不存在或读取失败
     */
    public byte[] readAttachmentFile(String fileUrl) throws IOException {
        Path localPath = resolveLocalPath(fileUrl);
        if (localPath != null && Files.exists(localPath)) {
            return Files.readAllBytes(localPath);
        }
        throw new IOException("附件文件不存在: " + fileUrl);
    }

    /**
     * 解析 fileUrl 到本地文件路径.
     *
     * <p>解析策略：
     * 1. 优先尝试作为绝对路径（页面上传存储的格式）
     * 2. 若绝对路径不存在，拼接 attachmentRoot（批量导入存储的格式）
     *
     * @param fileUrl 数据库中存储的附件路径
     * @return 解析后的本地路径，null 表示 fileUrl 为空
     */
    Path resolveLocalPath(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;

        // 1. 绝对路径（页面上传：dest.toAbsolutePath().normalize().toString()）
        Path absolutePath = Path.of(fileUrl).normalize();
        if (absolutePath.isAbsolute() && Files.exists(absolutePath)) {
            return absolutePath;
        }

        // 2. 相对路径（批量导入："/" + performanceId + "/" + storedFilename）
        String relativePart = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
        Path importPath = Path.of(attachmentRoot).resolve(relativePart).normalize();

        // 路径穿越防护
        if (!importPath.startsWith(Path.of(attachmentRoot).normalize())) {
            return null;
        }
        if (Files.exists(importPath)) {
            return importPath;
        }

        // 3. 回退：返回最佳猜测用于错误信息
        return absolutePath.isAbsolute() ? absolutePath : importPath;
    }
}
