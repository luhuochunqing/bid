package com.xiyu.bid.compliance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Docx 媒体流前置过滤器测试")
class DocxMediaFilterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("shouldFilter 针对小文件或非 docx 返回 false")
    void testShouldFilter_falseCases() throws IOException {
        Path txtFile = tempDir.resolve("test.txt");
        Files.writeString(txtFile, "hello");

        assertThat(DocxMediaFilter.shouldFilter(txtFile, "test.txt", 10)).isFalse();
        assertThat(DocxMediaFilter.shouldFilter(null, "test.docx", 10)).isFalse();
        assertThat(DocxMediaFilter.shouldFilter(txtFile, null, 10)).isFalse();

        Path docxFile = tempDir.resolve("small.docx");
        Files.write(docxFile, new byte[100]);
        assertThat(DocxMediaFilter.shouldFilter(docxFile, "small.docx", 1000)).isFalse();
        assertThat(DocxMediaFilter.shouldFilter(docxFile, "small.docx", 50)).isTrue();
    }

    @Test
    @DisplayName("filterMediaToTempFile 成功剔除 word/media/ 条目并保留其他 XML")
    void testFilterMediaToTempFile_success() throws IOException {
        Path mockDocx = tempDir.resolve("mock_large.docx");
        createMockDocxWithMedia(mockDocx);

        Path slimDocx = DocxMediaFilter.filterMediaToTempFile(mockDocx);
        try {
            assertThat(slimDocx).isNotEqualTo(mockDocx);
            assertThat(Files.exists(slimDocx)).isTrue();

            List<String> entryNames = listZipEntries(slimDocx);
            assertThat(entryNames).contains("word/document.xml", "[Content_Types].xml");
            assertThat(entryNames).noneMatch(name -> name.startsWith("word/media/"));
        } finally {
            Files.deleteIfExists(slimDocx);
        }
    }

    @Test
    @DisplayName("filterMediaToTempFile 遇到非 zip 损坏文件时优雅回退原始路径")
    void testFilterMediaToTempFile_corruptFallback() throws IOException {
        Path corruptFile = tempDir.resolve("corrupt.docx");
        Files.writeString(corruptFile, "Not a valid zip content");

        Path result = DocxMediaFilter.filterMediaToTempFile(corruptFile);
        assertThat(result).isEqualTo(corruptFile);
    }

    private void createMockDocxWithMedia(Path zipPath) throws IOException {
        try (OutputStream fos = new BufferedOutputStream(Files.newOutputStream(zipPath));
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1. word/document.xml
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("<w:document><w:body><w:p><w:t>标书正文测试</w:t></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<Types></Types>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. word/media/image1.png (假装是大图片)
            zos.putNextEntry(new ZipEntry("word/media/image1.png"));
            zos.write(new byte[1024 * 50]);
            zos.closeEntry();

            // 4. word/media/image2.jpeg
            zos.putNextEntry(new ZipEntry("word/media/image2.jpeg"));
            zos.write(new byte[1024 * 50]);
            zos.closeEntry();
        }
    }

    private List<String> listZipEntries(Path zipPath) throws IOException {
        List<String> entries = new ArrayList<>();
        try (InputStream fis = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }
        return entries;
    }
}
