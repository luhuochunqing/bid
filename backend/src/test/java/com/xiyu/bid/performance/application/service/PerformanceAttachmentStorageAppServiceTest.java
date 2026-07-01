package com.xiyu.bid.performance.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PerformanceAttachmentStorageAppService 单元测试。
 *
 * <p>覆盖点：
 * <ul>
 *   <li>上传成功返回 fileName/fileUrl，文件落盘到 uploadDir/{fileType}/ 下</li>
 *   <li>uploadDir 相对路径时按 JVM 工作目录归一化为绝对路径（参考 LL-028 与 brandAuth 教训）</li>
 *   <li>fileUrl 必须是绝对路径</li>
 *   <li>文件为空 / 超过 20MB / 不支持的 MIME / 非法 fileType 时抛 IllegalArgumentException</li>
 *   <li>上传后的文件名保留原始文件名（用于回显），磁盘文件名做 sanitize</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerformanceAttachmentStorageAppServiceTest {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "CONTRACT_AGREEMENT", "MALL_SCREENSHOT", "SOE_DIRECTORY",
            "CATEGORY_PAGE", "RELATIONSHIP_PROOF", "BID_NOTICE", "OTHER");

    private PerformanceAttachmentStorageAppService service;

    @TempDir
    Path tempDir;

    private Path originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = Paths.get(System.getProperty("user.dir"));
        service = new PerformanceAttachmentStorageAppService();
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir.toString());
    }

    @Test
    @DisplayName("uploadDir 为绝对路径时，文件成功写入绝对路径目录")
    void upload_withAbsoluteUploadDir_writesToAbsoluteDir() throws IOException {
        Path absUploadDir = tempDir.resolve("uploads/performance-attachments");
        ReflectionTestUtils.setField(service, "uploadDir", absUploadDir.toString());

        MultipartFile file = new MockMultipartFile(
                "file", "合同协议.pdf", "application/pdf", "hello".getBytes());

        var result = service.upload("CONTRACT_AGREEMENT", file);

        assertEquals("合同协议.pdf", result.fileName());
        assertFalse(result.fileUrl().isBlank(), "fileUrl 不能为空");
        // 验证文件确实写入绝对路径
        Path expectedDir = absUploadDir.resolve("CONTRACT_AGREEMENT");
        assertEquals(1, Files.list(expectedDir).count());
        // 验证 fileUrl 是绝对路径
        assertTrue(Paths.get(result.fileUrl()).isAbsolute(),
                "fileUrl 应为绝对路径，实际: " + result.fileUrl());
        assertTrue(Files.exists(Paths.get(result.fileUrl())),
                "fileUrl 指向的文件应存在: " + result.fileUrl());
    }

    @Test
    @DisplayName("uploadDir 为相对路径时，按 JVM 工作目录归一化为绝对路径")
    void upload_withRelativeUploadDir_normalizesToJvmUserDir() throws IOException {
        System.setProperty("user.dir", tempDir.toString());
        ReflectionTestUtils.setField(service, "uploadDir", "uploads/performance-attachments");

        MultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "hello".getBytes());

        var result = service.upload("MALL_SCREENSHOT", file);

        Path expectedDir = tempDir.resolve("uploads/performance-attachments/MALL_SCREENSHOT");
        assertEquals(1, Files.list(expectedDir).count(),
                "文件应写入 JVM 工作目录下的相对路径");
        Path savedUrl = Paths.get(result.fileUrl());
        assertTrue(savedUrl.isAbsolute(),
                "fileUrl 必须是绝对路径，否则会触发 Tomcat 临时目录陷阱。实际: " + savedUrl);
    }

    @Test
    @DisplayName("文件为空时抛 IllegalArgumentException")
    void upload_emptyFile_throwsIllegalArgument() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        MultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> service.upload("CONTRACT_AGREEMENT", empty));
    }

    @Test
    @DisplayName("文件超过 20MB 时抛 IllegalArgumentException")
    void upload_oversizedFile_throwsIllegalArgument() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        byte[] big = new byte[(int) (20 * 1024 * 1024 + 1)];
        MultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", big);

        assertThrows(IllegalArgumentException.class,
                () -> service.upload("CONTRACT_AGREEMENT", file));
    }

    @Test
    @DisplayName("文件 MIME 不支持时抛 IllegalArgumentException")
    void upload_unsupportedType_throwsIllegalArgument() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        MultipartFile file = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", "x".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.upload("CONTRACT_AGREEMENT", file));
    }

    @Test
    @DisplayName("非法 fileType 时抛 IllegalArgumentException")
    void upload_invalidFileType_throwsIllegalArgument() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        MultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "x".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.upload("INVALID_TYPE", file));
    }

    @Test
    @DisplayName("所有合法 fileType 均可上传成功")
    void upload_allAllowedFileTypes_succeed() throws IOException {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        for (String type : ALLOWED_TYPES) {
            MultipartFile file = new MockMultipartFile(
                    "file", type + ".pdf", "application/pdf", "x".getBytes());
            var result = service.upload(type, file);
            assertEquals(type + ".pdf", result.fileName());
            assertTrue(Files.exists(Paths.get(result.fileUrl())),
                    "fileUrl 指向的文件应存在: " + result.fileUrl());
        }
    }
}
