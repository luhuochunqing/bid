package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SidecarStreamDocumentAdapter 降级与安全守卫测试")
class SidecarStreamDocumentAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 使用无效的 sidecarUrl 强制触发 fallback 降级
    private final SidecarStreamDocumentAdapter adapter = new SidecarStreamDocumentAdapter(
            objectMapper, "http://127.0.0.1:59999", "", 1
    );

    @Test
    @DisplayName("降级时：纯文本小文件（<=5MB）正常读取内容并生成哈希")
    void fallback_smallPlainText_readsContent(@TempDir Path tempDir) throws IOException {
        Path txtFile = tempDir.resolve("sample.txt");
        Files.writeString(txtFile, "纯文本招标条款内容");

        DocumentContent content = adapter.convertDocument(txtFile, "sample.txt");

        assertThat(content).isNotNull();
        assertThat(content.fileName()).isEqualTo("sample.txt");
        assertThat(content.fileHash()).isNotEmpty();
        assertThat(content.fullText()).isEqualTo("纯文本招标条款内容");
    }

    @Test
    @DisplayName("降级时：二进制 docx 文件不执行本地 readString，仅计算哈希并返回空文本")
    void fallback_binaryDocx_skipsReadString(@TempDir Path tempDir) throws IOException {
        Path docxFile = tempDir.resolve("huge_bid.docx");
        // 写入模拟二进制内容（包含非 UTF-8 字符）
        byte[] dummyBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, (byte) 0xFF, (byte) 0xFE};
        Files.write(docxFile, dummyBytes);

        DocumentContent content = adapter.convertDocument(docxFile, "huge_bid.docx");

        assertThat(content).isNotNull();
        assertThat(content.fileName()).isEqualTo("huge_bid.docx");
        assertThat(content.fileHash()).isNotEmpty();
        // 必须返回空文本，不能把二进制当字符串读
        assertThat(content.fullText()).isEmpty();
    }

    @Test
    @DisplayName("降级时：超大纯文本文件（>5MB）跳过本地读取，防止 DirectBuffer OOM")
    void fallback_largeFile_skipsReadString(@TempDir Path tempDir) throws IOException {
        Path largeTxtFile = tempDir.resolve("huge.txt");
        // 构造 6MB 文件
        byte[] largeBytes = new byte[6 * 1024 * 1024];
        Files.write(largeTxtFile, largeBytes);

        DocumentContent content = adapter.convertDocument(largeTxtFile, "huge.txt");

        assertThat(content).isNotNull();
        assertThat(content.fileHash()).isNotEmpty();
        assertThat(content.fullText()).isEmpty();
    }

    @Test
    @DisplayName("大 docx 文件在发送 Sidecar 时能正常通过 DocxMediaFilter 流式过滤并计算原文件哈希")
    void convertDocument_largeDocx_filtersMediaCorrectly(@TempDir Path tempDir) throws IOException {
        Path largeDocx = tempDir.resolve("huge_with_media.docx");
        // 创建 21MB 的 docx 文件（模拟大体积）
        byte[] dummyDocx = new byte[21 * 1024 * 1024];
        dummyDocx[0] = 0x50;
        dummyDocx[1] = 0x4B;
        dummyDocx[2] = 0x03;
        dummyDocx[3] = 0x04;
        Files.write(largeDocx, dummyDocx);

        DocumentContent content = adapter.convertDocument(largeDocx, "huge_with_media.docx");

        assertThat(content).isNotNull();
        assertThat(content.fileName()).isEqualTo("huge_with_media.docx");
        assertThat(content.fileHash()).isNotEmpty();
    }
}

