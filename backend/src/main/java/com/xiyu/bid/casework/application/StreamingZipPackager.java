package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 归档项目文件包打包器（同步生成完整 ZIP 字节数组）。
 *
 * <p>历史实现走 Spring {@code StreamingResponseBody} + Tomcat 异步流式写入，触发了三类回归：
 * <ol>
 *   <li>try-with-resources 关闭 {@code ZipOutputStream} 会级联关闭 ServletOutputStream，导致
 *       Spring 异步 dispatch 阶段抛 "Cannot dispatch without an AsyncContext"。</li>
 *   <li>异步线程在 lambda 体内跑 Hibernate 触发 Servlet 路径二次 Security 过滤，
 *       SecurityContext 丢失被 403 Rejecting access 覆盖。</li>
 *   <li>Content-Disposition 中文硬编码被 Tomcat 主动删除。</li>
 * </ol>
 *
 * <p>改为同步生成 {@code byte[]} 后由 controller 一次性写入 response，避免以上全部副作用。
 * demo 数据量级（KB 级）完全够用；如未来归档体量提升到 MB 级，再切换到 {@code StreamingResponseBody}
 * + 修正的 {@code ZipOutputStream} 生命周期管理。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreamingZipPackager {

    private final ArchiveFileRepository archiveFileRepository;

    public byte[] buildZipBytes(List<ProjectArchive> archives, Path tempExcelPath) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);
        try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
            // 1. 打包 _台账.xlsx
            if (tempExcelPath != null) {
                try {
                    if (Files.exists(tempExcelPath)) {
                        ZipEntry excelEntry = new ZipEntry("_台账.xlsx");
                        zipOut.putNextEntry(excelEntry);
                        Files.copy(tempExcelPath, zipOut);
                        zipOut.closeEntry();
                    }
                } catch (IOException e) {
                    log.error("Failed to copy Excel index in ZIP packaging: {}", e.getMessage(), e);
                    ZipEntry errorEntry = new ZipEntry("台账生成失败说明.txt");
                    zipOut.putNextEntry(errorEntry);
                    zipOut.write(("台账生成失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                    zipOut.closeEntry();
                } finally {
                    try {
                        Files.deleteIfExists(tempExcelPath);
                    } catch (IOException ignored) {
            log.debug("{}: caught {} ({})", "StreamingZipPackager", ignored.getClass().getSimpleName(), ignored.getMessage());
                    }
                }
            }

            // 2. 遍历归档，打包其关联的文件
            for (ProjectArchive archive : archives) {
                List<ArchiveFile> files = archiveFileRepository.findByArchiveId(archive.getId());
                String projectFolder = safeFileName(archive.getProjectName());

                for (ArchiveFile file : files) {
                    String category = getCategoryDirLabel(file.getDocumentCategory());
                    String fileName = safeFileName(file.getFileName());
                    String zipPath = projectFolder + "/" + category + "/" + fileName;

                    ZipEntry fileEntry = new ZipEntry(zipPath);
                    zipOut.putNextEntry(fileEntry);

                    if (file.getFilePath() != null && !file.getFilePath().isBlank()) {
                        Path physicalPath = Paths.get(file.getFilePath());
                        if (Files.exists(physicalPath) && !Files.isDirectory(physicalPath)) {
                            Files.copy(physicalPath, zipOut);
                        } else {
                            zipOut.write(("未找到该物理文件: " + file.getFilePath()).getBytes(StandardCharsets.UTF_8));
                        }
                    } else {
                        zipOut.write("该归档记录未配置有效文件路径".getBytes(StandardCharsets.UTF_8));
                    }
                    zipOut.closeEntry();
                }
            }
            zipOut.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            log.error("Failed to build ZIP bytes for archives", e);
            throw new IllegalStateException("Failed to package archives into ZIP", e);
        }
    }

    private String safeFileName(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String getCategoryDirLabel(String cat) {
        if (cat == null) return "其他";
        return switch (cat) {
            case "TENDER" -> "招标文件";
            case "BID" -> "标书文件";
            case "OPEN_LIST" -> "开标一览表";
            case "WIN_NOTICE" -> "中标通知书";
            case "DEPOSIT_RECEIPT" -> "保证金银行回单";
            default -> "其他";
        };
    }
}
