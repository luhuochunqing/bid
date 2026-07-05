package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamingZipPackagerCategoryFilterTest {

    @Mock
    private ArchiveFileRepository archiveFileRepository;

    @InjectMocks
    private StreamingZipPackager streamingZipPackager;

    private ProjectArchive archive;
    private ArchiveFile tenderFile;
    private ArchiveFile bidFile;
    private ArchiveFile otherFile;
    private Path tempExcelPath;

    @BeforeEach
    void setUp() throws IOException {
        archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectName("测试项目");

        tenderFile = new ArchiveFile();
        tenderFile.setId(1L);
        tenderFile.setArchiveId(1L);
        tenderFile.setFileName("招标文件.pdf");
        tenderFile.setDocumentCategory("TENDER");
        tenderFile.setFilePath(createTempFile("tender-content").toString());

        bidFile = new ArchiveFile();
        bidFile.setId(2L);
        bidFile.setArchiveId(1L);
        bidFile.setFileName("标书文件.docx");
        bidFile.setDocumentCategory("BID");
        bidFile.setFilePath(createTempFile("bid-content").toString());

        otherFile = new ArchiveFile();
        otherFile.setId(3L);
        otherFile.setArchiveId(1L);
        otherFile.setFileName("其他资料.txt");
        otherFile.setDocumentCategory("OTHER");
        otherFile.setFilePath(createTempFile("other-content").toString());

        tempExcelPath = createTempFile("excel-content");

        when(archiveFileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of(tenderFile, bidFile, otherFile));
    }

    private Path createTempFile(String content) throws IOException {
        Path path = Files.createTempFile("test-zip-", ".txt");
        Files.write(path, content.getBytes());
        path.toFile().deleteOnExit();
        return path;
    }

    private int countFileEntries(byte[] zipBytes) throws IOException {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && !entry.getName().equals("_台账.xlsx")
                        && !entry.getName().contains("失败说明")) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    @DisplayName("无分类过滤时打包全部文件")
    void buildZipBytes_noCategoryFilter_packsAllFiles() throws IOException {
        byte[] zipBytes = streamingZipPackager.buildZipBytes(List.of(archive), tempExcelPath, null);
        assertThat(countFileEntries(zipBytes)).isEqualTo(3);
    }

    @Test
    @DisplayName("空分类集合时打包全部文件")
    void buildZipBytes_emptyCategoryFilter_packsAllFiles() throws IOException {
        byte[] zipBytes = streamingZipPackager.buildZipBytes(List.of(archive), tempExcelPath, Set.of());
        assertThat(countFileEntries(zipBytes)).isEqualTo(3);
    }

    @Test
    @DisplayName("单个分类过滤时只打包匹配文件")
    void buildZipBytes_singleCategoryFilter_onlyPacksMatching() throws IOException {
        byte[] zipBytes = streamingZipPackager.buildZipBytes(List.of(archive), tempExcelPath, Set.of("TENDER"));
        assertThat(countFileEntries(zipBytes)).isEqualTo(1);
    }

    @Test
    @DisplayName("多个分类过滤时只打包匹配文件")
    void buildZipBytes_multiCategoryFilter_onlyPacksMatching() throws IOException {
        byte[] zipBytes = streamingZipPackager.buildZipBytes(List.of(archive), tempExcelPath, Set.of("TENDER", "BID"));
        assertThat(countFileEntries(zipBytes)).isEqualTo(2);
    }

    @Test
    @DisplayName("无匹配分类时不打包任何文件")
    void buildZipBytes_noMatchingCategory_packsZeroFiles() throws IOException {
        byte[] zipBytes = streamingZipPackager.buildZipBytes(List.of(archive), tempExcelPath, Set.of("WIN_NOTICE"));
        assertThat(countFileEntries(zipBytes)).isZero();
    }

    @Test
    @DisplayName("原方法签名（无分类参数）行为不变")
    void buildZipBytes_originalSignature_packsAllFiles() throws IOException {
        byte[] zipBytes = streamingZipPackager.buildZipBytes(List.of(archive), tempExcelPath);
        assertThat(countFileEntries(zipBytes)).isEqualTo(3);
    }
}
