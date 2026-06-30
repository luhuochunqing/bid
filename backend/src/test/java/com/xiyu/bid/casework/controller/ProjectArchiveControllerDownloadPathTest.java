package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.ProjectArchiveDetailService;
import com.xiyu.bid.casework.application.ProjectArchiveExportService;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.casework.application.StreamingZipPackager;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CO-430: 项目档案文件下载"文件路径越界"问题修复验证。
 *
 * 根因：ARCHIVE_FILE_BASE_DIR 硬编码为 "data"，而数据库 archive_file.file_path 存的是
 * LocalDocumentStorage.store() 返回的绝对路径（uploadRoot 下的子目录）。
 * resolveAbsoluteWithin(rawPath, "data") 在 rawPath 是绝对路径时永远无法匹配 baseDir。
 *
 * 修复：从 app.doc-insight.upload-dir 配置读取实际的 upload 根目录作为 baseDir。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("CO-430 文件下载路径越界修复验证")
class ProjectArchiveControllerDownloadPathTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArchiveFileRepository fileRepository;

    @Autowired
    private com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository archiveRepository;

    @Value("${app.doc-insight.upload-dir:}")
    private String configuredUploadDir;

    private String getUploadRoot() {
        return (configuredUploadDir == null || configuredUploadDir.isBlank())
                ? System.getProperty("java.io.tmpdir") + "/xiyu-doc-insight-uploads"
                : configuredUploadDir;
    }

    @Test
    @DisplayName("filePath 在 upload-dir 根目录下时，可以正常下载（200）")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void downloadFile_withinUploadDir_shouldReturn200() throws Exception {
        // 创建临时文件，路径在 uploadRoot 下
        String uploadRoot = getUploadRoot();
        Path tempDir = Path.of(uploadRoot, "tender-file", "1");
        Files.createDirectories(tempDir);
        Path tempFile = tempDir.resolve("test-doc.pdf");
        Files.writeString(tempFile, "test content");

        // 创建归档记录
        com.xiyu.bid.casework.infrastructure.ProjectArchive archive = new com.xiyu.bid.casework.infrastructure.ProjectArchive();
        archive.setProjectId(1L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");
        archive = archiveRepository.save(archive);

        // 创建文件记录，filePath 指向 uploadRoot 下的文件
        ArchiveFile file = new ArchiveFile();
        file.setArchiveId(archive.getId());
        file.setFileName("test-doc.pdf");
        file.setDocumentCategory("BID");
        file.setFilePath(tempFile.toAbsolutePath().toString());
        file.setFileSize(Files.size(tempFile));
        file.setUploadUserId(1L);
        file.setUploadUserName("admin");
        file = fileRepository.save(file);

        // 验证下载接口返回 200
        mockMvc.perform(get("/api/archive/files/{fileId}/download", file.getId())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk());

        // 清理
        Files.deleteIfExists(tempFile);
        fileRepository.deleteById(file.getId());
        archiveRepository.deleteById(archive.getId());
    }

    @Test
    @DisplayName("filePath 不在 upload-dir 根目录下时，应返回 400（文件路径越界）")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void downloadFile_outsideUploadDir_shouldReturn400() throws Exception {
        // 创建临时文件，路径不在 uploadRoot 下
        Path tempDir = Files.createTempDirectory("outside-upload-root");
        Path tempFile = tempDir.resolve("malicious.pdf");
        Files.writeString(tempFile, "malicious content");

        // 创建归档记录
        com.xiyu.bid.casework.infrastructure.ProjectArchive archive = new com.xiyu.bid.casework.infrastructure.ProjectArchive();
        archive.setProjectId(1L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");
        archive = archiveRepository.save(archive);

        // 创建文件记录，filePath 指向 uploadRoot 外的文件
        ArchiveFile file = new ArchiveFile();
        file.setArchiveId(archive.getId());
        file.setFileName("malicious.pdf");
        file.setDocumentCategory("BID");
        file.setFilePath(tempFile.toAbsolutePath().toString()); // 越界路径
        file.setFileSize(Files.size(tempFile));
        file.setUploadUserId(1L);
        file.setUploadUserName("admin");
        file = fileRepository.save(file);

        // 验证下载接口返回 400（路径越界）
        mockMvc.perform(get("/api/archive/files/{fileId}/download", file.getId())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isBadRequest());

        // 清理
        Files.deleteIfExists(tempFile);
        Files.deleteIfExists(tempDir);
        fileRepository.deleteById(file.getId());
        archiveRepository.deleteById(archive.getId());
    }
}
