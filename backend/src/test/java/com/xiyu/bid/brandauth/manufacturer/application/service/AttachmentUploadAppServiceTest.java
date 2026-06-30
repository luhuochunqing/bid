package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AttachmentType;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttachmentUploadAppService 单元测试。
 *
 * <p>核心覆盖点：验证修复后的绝对路径归一化逻辑，避免回归到
 * "Files.createDirectories 按 JVM 工作目录解析 / MultipartFile.transferTo(File) 按 Tomcat 临时目录解析"
 * 的相对路径陷阱（生产事故 traceId=1d5fa3c3c9e8459298b10d001aca6538）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttachmentUploadAppServiceTest {

    @Mock
    private BrandAuthAttachmentJpaRepository attachmentRepository;
    @Mock
    private ManufacturerAuthorizationRepository authorizationRepository;

    @InjectMocks
    private AttachmentUploadAppService service;

    @TempDir
    Path tempDir;

    private Path originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = Paths.get(System.getProperty("user.dir"));
        ManufacturerAuthorization auth = ManufacturerAuthorization.create(
                com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine.TOOLS,
                "BR-001", "品牌", "国产", "原厂",
                java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(180), null, 1L);
        lenient().when(authorizationRepository.findById(any())).thenReturn(Optional.of(auth));
        lenient().when(attachmentRepository.save(any())).thenAnswer(inv -> {
            BrandAuthAttachmentEntity e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 1L);
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir.toString());
    }

    @Test
    @DisplayName("uploadDir 为绝对路径时，文件成功写入绝对路径目录（生产配置场景）")
    void upload_withAbsoluteUploadDir_writesToAbsoluteDir() throws IOException {
        Path absUploadDir = tempDir.resolve("uploads/brand-auth");
        ReflectionTestUtils.setField(service, "uploadDir", absUploadDir.toString());

        MultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "hello".getBytes());

        List<ManufacturerAuthorizationDTO.AttachmentDTO> result =
                service.upload(100L, "AUTH_DOC", List.of(file));

        assertEquals(1, result.size());
        // 验证文件确实写入绝对路径
        Path expectedDir = absUploadDir.resolve("100");
        assertEquals(1, Files.list(expectedDir).count());
        // 验证 fileUrl 是绝对路径
        assertTrue(result.get(0).fileUrl().startsWith(absUploadDir.toString()),
                "fileUrl 应为绝对路径，实际: " + result.get(0).fileUrl());
    }

    @Test
    @DisplayName("uploadDir 为相对路径时，按 JVM 工作目录归一化为绝对路径（修复核心）")
    void upload_withRelativeUploadDir_normalizesToJvmUserDir() throws IOException {
        // 模拟 systemd WorkingDirectory 临时切换到 tempDir
        System.setProperty("user.dir", tempDir.toString());
        // 默认相对路径（application.yml 兜底值）
        ReflectionTestUtils.setField(service, "uploadDir", "uploads/brand-auth");

        MultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "hello".getBytes());

        List<ManufacturerAuthorizationDTO.AttachmentDTO> result =
                service.upload(101L, "AUTH_DOC", List.of(file));

        assertEquals(1, result.size());
        // 验证文件实际写入了 tempDir/uploads/brand-auth/101/
        Path expectedFile = tempDir.resolve("uploads/brand-auth/101");
        assertEquals(1, Files.list(expectedFile).count(),
                "文件应写入 JVM 工作目录下的相对路径");
        // 验证 fileUrl 是绝对路径（不再走 Tomcat 临时目录）
        Path savedUrl = Paths.get(result.get(0).fileUrl());
        assertTrue(savedUrl.isAbsolute(),
                "fileUrl 必须是绝对路径，否则会触发 Tomcat 临时目录陷阱。实际: " + savedUrl);
        assertTrue(savedUrl.startsWith(tempDir.resolve("uploads/brand-auth")),
                "fileUrl 应在 tempDir/uploads/brand-auth 下，实际: " + savedUrl);
    }

    @Test
    @DisplayName("upload 成功后 entity.fileUrl 与磁盘文件一致")
    void upload_persistsFileUrlMatchingDiskPath() throws IOException {
        Path absUploadDir = tempDir.resolve("uploads/brand-auth");
        ReflectionTestUtils.setField(service, "uploadDir", absUploadDir.toString());

        MultipartFile file = new MockMultipartFile(
                "file", "原厂授权.pdf", "application/pdf", "content".getBytes());

        service.upload(102L, "AUTH_DOC", List.of(file));

        ArgumentCaptor<BrandAuthAttachmentEntity> captor =
                ArgumentCaptor.forClass(BrandAuthAttachmentEntity.class);
        verify(attachmentRepository).save(captor.capture());
        BrandAuthAttachmentEntity saved = captor.getValue();

        assertEquals(102L, saved.getAuthorizationId());
        assertEquals(AttachmentType.AUTH_DOC, saved.getAttachmentType());
        assertEquals("原厂授权.pdf", saved.getFileName());
        assertEquals("application/pdf", saved.getFileType());
        // fileUrl 必须是磁盘上真实存在的文件
        assertTrue(Files.exists(Paths.get(saved.getFileUrl())),
                "fileUrl 指向的文件应存在: " + saved.getFileUrl());
    }

    @Test
    @DisplayName("文件为空时抛 IllegalArgumentException")
    void upload_emptyFile_throwsIllegalArgument() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        MultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> service.upload(1L, "AUTH_DOC", List.of(empty)));
    }

    @Test
    @DisplayName("文件超 20MB 时抛 IllegalArgumentException")
    void upload_oversizedFile_throwsIllegalArgument() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        byte[] big = new byte[(int) (20 * 1024 * 1024 + 1)];
        MultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", big);

        assertThrows(IllegalArgumentException.class,
                () -> service.upload(1L, "AUTH_DOC", List.of(file)));
    }

    @Test
    @DisplayName("文件类型不支持时抛 IllegalArgumentException")
    void upload_unsupportedType_throwsIllegalArgument() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        MultipartFile file = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", "x".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.upload(1L, "AUTH_DOC", List.of(file)));
    }

    @Test
    @DisplayName("authorizationId 不存在时抛 NoSuchElementException")
    void upload_authNotFound_throwsNoSuchElement() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        when(authorizationRepository.findById(99999L)).thenReturn(Optional.empty());

        MultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "x".getBytes());

        assertThrows(java.util.NoSuchElementException.class,
                () -> service.upload(99999L, "AUTH_DOC", List.of(file)));
    }
}
