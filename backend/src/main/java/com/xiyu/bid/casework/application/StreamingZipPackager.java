package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamingZipPackager {

    private final ArchiveFileRepository archiveFileRepository;

    public StreamingResponseBody packageArchives(List<ProjectArchive> archives, Path tempExcelPath) {
        return outputStream -> {
            try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
                // 1. 打包 _台账.xlsx
                if (tempExcelPath != null) {
                    try {
                        if (Files.exists(tempExcelPath)) {
                            ZipEntry excelEntry = new ZipEntry("_台账.xlsx");
                            zipOut.putNextEntry(excelEntry);
                            Files.copy(tempExcelPath, zipOut);
                            zipOut.closeEntry();
                        }
                    } catch (Exception e) {
                        log.error("Failed to copy Excel index in ZIP packaging: {}", e.getMessage(), e);
                        ZipEntry errorEntry = new ZipEntry("台账生成失败说明.txt");
                        zipOut.putNextEntry(errorEntry);
                        zipOut.write(("台账生成失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                        zipOut.closeEntry();
                    } finally {
                        try {
                            Files.deleteIfExists(tempExcelPath);
                        } catch (IOException ignored) {
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
            } catch (IOException e) {
                log.error("ZIP streaming failed", e);
                throw e;
            }
        };
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
