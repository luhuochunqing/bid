package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StreamingZipPackager 单元测试。
 *
 * <p>CO-428: 修复 ZIP 打包在同名同分类文件场景下抛 {@code ZipException: duplicate entry} 的问题。
 * 测试构造两份 {@code fileName} + {@code documentCategory} 完全相同的 ArchiveFile，
 * 断言打包过程不抛异常且生成的 ZIP 字节数组有效。
 */
class StreamingZipPackagerTest {

    private ArchiveFileRepository archiveFileRepository;
    private StreamingZipPackager packager;

    @BeforeEach
    void setUp() {
        archiveFileRepository = mock(ArchiveFileRepository.class);
        packager = new StreamingZipPackager(archiveFileRepository);
    }

    @Test
    @DisplayName("buildZipBytes: 同名同分类文件应去重，不抛 duplicate entry 异常")
    void buildZipBytes_DuplicateFileNameAndCategory_ShouldNotThrow() throws Exception {
        // Given: 同一 archive 下两份同名、同分类文件（复现 CO-428 真实数据场景）
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(10L);
        archive.setProjectName("智慧园区MRO年度采购项目");
        archive.setArchiveStatus("ACTIVE");

        ArchiveFile file1 = new ArchiveFile();
        file1.setId(101L);
        file1.setArchiveId(1L);
        file1.setFileName("001-开发-租赁合同-202512.docx");
        file1.setDocumentCategory("OTHER");
        file1.setFilePath("");
        file1.setFileSize(0L);
        file1.setUploadUserId(1L);
        file1.setUploadUserName("test");

        ArchiveFile file2 = new ArchiveFile();
        file2.setId(102L);
        file2.setArchiveId(1L);
        file2.setFileName("001-开发-租赁合同-202512.docx");
        file2.setDocumentCategory("OTHER"); // 与 file1 完全同名同分类
        file2.setFilePath("");
        file2.setFileSize(0L);
        file2.setUploadUserId(1L);
        file2.setUploadUserName("test");

        when(archiveFileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of(file1, file2));

        Path tempExcel = Files.createTempFile("test_index_", ".xlsx");
        Files.write(tempExcel, "fake-excel".getBytes());

        // When & Then: 不抛 IllegalStateException(ZipException)
        byte[] zipBytes = assertDoesNotThrow(
                () -> packager.buildZipBytes(List.of(archive), tempExcel),
                "同名同分类文件应通过去重处理，不应抛 duplicate entry 异常");

        // 生成的 ZIP 应包含 3 个条目：1 个台账 + 2 个文件（去重后路径不同）
        List<String> entryNames = readZipEntryNames(zipBytes);
        assertTrue(entryNames.contains("_台账.xlsx"), "应包含台账 Excel 条目");
        assertEquals(2, entryNames.stream()
                        .filter(n -> n.startsWith("智慧园区MRO年度采购项目/其他/"))
                        .count(),
                "应包含 2 个文件条目（去重后保留两份）");
        // 临时文件应已清理
        assertFalse(Files.exists(tempExcel), "临时 Excel 文件应被清理");
    }

    @Test
    @DisplayName("buildZipBytes: 正常场景（无重名）仍可正确打包")
    void buildZipBytes_NormalCase_ShouldPackAllFiles() throws Exception {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(2L);
        archive.setProjectId(20L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        ArchiveFile file1 = new ArchiveFile();
        file1.setId(201L);
        file1.setArchiveId(2L);
        file1.setFileName("招标文件.pdf");
        file1.setDocumentCategory("TENDER");
        file1.setFilePath("");
        file1.setFileSize(0L);
        file1.setUploadUserId(1L);
        file1.setUploadUserName("test");

        ArchiveFile file2 = new ArchiveFile();
        file2.setId(202L);
        file2.setArchiveId(2L);
        file2.setFileName("标书文件.docx");
        file2.setDocumentCategory("BID");
        file2.setFilePath("");
        file2.setFileSize(0L);
        file2.setUploadUserId(1L);
        file2.setUploadUserName("test");

        when(archiveFileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of(file1, file2));

        Path tempExcel = Files.createTempFile("test_index_", ".xlsx");
        Files.write(tempExcel, "fake-excel".getBytes());

        byte[] zipBytes = assertDoesNotThrow(
                () -> packager.buildZipBytes(List.of(archive), tempExcel));

        List<String> entryNames = readZipEntryNames(zipBytes);
        assertTrue(entryNames.contains("测试项目/招标文件/招标文件.pdf"));
        assertTrue(entryNames.contains("测试项目/标书文件/标书文件.docx"));
    }

    private List<String> readZipEntryNames(byte[] zipBytes) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.List<String> names = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            return names;
        }
    }
}
