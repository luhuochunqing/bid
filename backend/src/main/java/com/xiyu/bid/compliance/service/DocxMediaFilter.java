// Input: Path of docx file
// Output: Filtered lightweight docx Path
// Pos: Service/合规与文档处理
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.compliance.service;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Docx 媒体前置流式过滤器.
 * 在向 Sidecar 发送大体积 docx 之前，流式剔除 word/media/ 中的媒体资源，
 * 将百兆/GB 级 docx 缩减至 1~2MB，消除 HTTP 传输超限 (413) 与超时。
 */
@Slf4j
public final class DocxMediaFilter {

    /** 默认触发过滤的文件大小阈值：20MB */
    public static final long DEFAULT_THRESHOLD_BYTES = 20L * 1024 * 1024;

    private static final String DOCX_EXTENSION = ".docx";
    private static final String MEDIA_PREFIX = "word/media/";

    private DocxMediaFilter() {
        // utility class
    }

    /**
     * 判断文件是否需要过滤媒体流.
     *
     * @param filePath 物理文件路径
     * @param fileName 原始文件名
     * @param thresholdBytes 触发过滤的文件大小阈值
     * @return true 若为 .docx 且大小超过阈值
     */
    public static boolean shouldFilter(final Path filePath, final String fileName, final long thresholdBytes) {
        if (filePath == null || !Files.exists(filePath) || fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(DOCX_EXTENSION)) {
            return false;
        }
        try {
            return Files.size(filePath) > thresholdBytes;
        } catch (IOException e) {
            log.warn("获取文件大小失败 {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 流式过滤 docx 文件的 word/media/ 条目，生成临时精简 docx 文件.
     * 若解压过滤异常，则返回原始文件路径（优雅降级）.
     *
     * @param inputPath 原始 docx 路径
     * @return 过滤后的临时 docx 路径（若过滤成功生成了新文件），或 inputPath（若未过滤/失败）
     */
    public static Path filterMediaToTempFile(final Path inputPath) {
        if (inputPath == null || !Files.exists(inputPath)) {
            return inputPath;
        }
        Path tempFile = null;
        boolean keepTempFile = false;
        try {
            tempFile = Files.createTempFile("xiyu-slim-docx-", ".docx");
            int entryCount = 0;
            int mediaCount = 0;
            try (InputStream fis = new BufferedInputStream(Files.newInputStream(inputPath));
                 ZipInputStream zis = new ZipInputStream(fis);
                 OutputStream fos = new BufferedOutputStream(Files.newOutputStream(tempFile));
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                byte[] buffer = new byte[64 * 1024];
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    entryCount++;
                    String name = entry.getName();
                    if (name.startsWith(MEDIA_PREFIX)) {
                        mediaCount++;
                        zis.closeEntry();
                        continue;
                    }
                    ZipEntry newEntry = new ZipEntry(name);
                    if (entry.getTime() != -1) {
                        newEntry.setTime(entry.getTime());
                    }
                    zos.putNextEntry(newEntry);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                    zis.closeEntry();
                }
            }
            if (entryCount > 0 && mediaCount > 0) {
                keepTempFile = true;
                log.info("Docx 媒体流前置过滤完成: 原文件={}, 滤除媒体条目数={}, 瘦身临时文件={}",
                        inputPath.getFileName(), mediaCount, tempFile.getFileName());
                return tempFile;
            }
            return inputPath;
        } catch (Throwable t) {
            log.warn("Docx 媒体流过滤异常，回退至原始文件 {}: {}", inputPath, t.getMessage());
            return inputPath;
        } finally {
            if (!keepTempFile && tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
