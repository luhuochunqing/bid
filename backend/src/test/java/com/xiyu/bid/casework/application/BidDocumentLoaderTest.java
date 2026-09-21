package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.scoreparse.application.SidecarStreamTextExtractor;
import com.xiyu.bid.scoreparse.application.StreamingBidFileFetcher;
import com.xiyu.bid.scoreparse.application.TempBidFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BidDocumentLoader 单元测试：装载结果的三态语义（可归档但不可用 / 可用 / 无归档）。
 *
 * <p>契约：{@code available() == false} 表示"取不到正文、来源不可查证"，它必须与"标书里没有这一节"区分开
 * ——存量来源回填据此整批跳过而绝不清空字段（见 CaseProvenanceBackfillAppService）。
 * 同时钉住"新增 available 分量不改变既有调用方拿到的 markdown 内容"这一不变量。
 */
class BidDocumentLoaderTest {

    private static final String MARKDOWN = "# 13.2. 对接平台对接成功案例及证明材料\n";

    private ProjectArchiveRepository archiveRepository;
    private ArchiveFileRepository fileRepository;
    private DocumentTextExtractor docTextExtractor;
    private BidDocumentLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        archiveRepository = mock(ProjectArchiveRepository.class);
        fileRepository = mock(ArchiveFileRepository.class);
        docTextExtractor = mock(DocumentTextExtractor.class);
        loader = new BidDocumentLoader(
                archiveRepository,
                fileRepository,
                docTextExtractor,
                mock(ObsShareUrlSigner.class),
                mock(StreamingBidFileFetcher.class),
                mock(SidecarStreamTextExtractor.class),
                mock(TempBidFileStore.class));
    }

    private void archiveWith(ArchiveFile bidFile) {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(9L);
        archive.setProjectId(1L);
        when(archiveRepository.findByProjectId(anyLong())).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(anyLong()))
                .thenReturn(bidFile == null ? List.of() : List.of(bidFile));
    }

    private ArchiveFile localBidFile() throws IOException {
        Path file = tempDir.resolve("bid.pdf");
        Files.writeString(file, "fake-pdf-bytes");
        ArchiveFile f = new ArchiveFile();
        f.setFileName("商务技术标书.pdf");
        f.setDocumentCategory("BID");
        f.setFilePath(file.toAbsolutePath().toString());
        return f;
    }

    @Test
    @DisplayName("项目无归档：返回 null（调用方据此跳过，不得当作标书为空）")
    void loadBidDocument_noArchive_returnsNull() {
        when(archiveRepository.findByProjectId(anyLong())).thenReturn(Optional.empty());

        assertThat(loader.loadBidDocument("项目-1", 1L)).isNull();
    }

    @Test
    @DisplayName("归档存在但无 BID 文件：不可用 + 占位文本")
    void loadBidDocument_noBidFile_returnsUnavailablePlaceholder() {
        archiveWith(null);

        BidDocumentLoader.BidDocument doc = loader.loadBidDocument("项目-1", 1L);

        assertThat(doc.available()).isFalse();
        assertThat(doc.markdown()).isEqualTo("# 项目-1\n暂无标书正文。");
        assertThat(doc.documentName()).isNull();
    }

    @Test
    @DisplayName("本地解析成功：可用 + 正文与页码；markdown 内容与既有实现一致")
    void loadBidDocument_localParseSucceeds_availableWithPage() throws IOException {
        archiveWith(localBidFile());
        when(docTextExtractor.extract(anyString(), anyString(), any()))
                .thenReturn(new ExtractedDocument(MARKDOWN, MARKDOWN.length(), null, "test", Map.of("pageNumber", 12)));

        BidDocumentLoader.BidDocument doc = loader.loadBidDocument("项目-1", 1L);

        assertThat(doc.available()).isTrue();
        assertThat(doc.markdown()).isEqualTo(MARKDOWN);
        assertThat(doc.documentName()).isEqualTo("商务技术标书.pdf");
        assertThat(doc.pageNumber()).isEqualTo(12);
    }

    @Test
    @DisplayName("本地解析为空文本：不可用，但 markdown 保持解析原值（不得替换成占位文本喂给 AI 提取）")
    void loadBidDocument_localParseBlankText_keepsParsedTextAndFlagsUnavailable() throws IOException {
        archiveWith(localBidFile());
        when(docTextExtractor.extract(anyString(), anyString(), any()))
                .thenReturn(new ExtractedDocument("   ", 3, null, "test", null));

        BidDocumentLoader.BidDocument doc = loader.loadBidDocument("项目-1", 1L);

        assertThat(doc.available()).isFalse();
        assertThat(doc.markdown()).isEqualTo("   ");
        assertThat(doc.markdown()).isNotEqualTo("# 项目-1\n暂无标书正文。");
    }

    @Test
    @DisplayName("本地解析返回空文档：不可用 + 占位文本（与既有实现一致）")
    void loadBidDocument_localParseNullDoc_fallsBackToPlaceholder() throws IOException {
        archiveWith(localBidFile());
        when(docTextExtractor.extract(anyString(), anyString(), any())).thenReturn(null);

        BidDocumentLoader.BidDocument doc = loader.loadBidDocument("项目-1", 1L);

        assertThat(doc.available()).isFalse();
        assertThat(doc.markdown()).isEqualTo("# 项目-1\n暂无标书正文。");
    }

    @Test
    @DisplayName("本地读取失败：不可用 + 占位文本，不抛异常")
    void loadBidDocument_localReadFails_returnsUnavailablePlaceholder() {
        ArchiveFile f = new ArchiveFile();
        f.setFileName("商务技术标书.pdf");
        f.setDocumentCategory("BID");
        f.setFilePath(tempDir.resolve("missing.pdf").toAbsolutePath().toString());
        archiveWith(f);

        BidDocumentLoader.BidDocument doc = loader.loadBidDocument("项目-1", 1L);

        assertThat(doc.available()).isFalse();
        assertThat(doc.markdown()).isEqualTo("# 项目-1\n暂无标书正文。");
    }
}
