package com.xiyu.bid.warehouse.file;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WarehouseFileService 落盘路径回归。
 *
 * <p>回归背景：transferTo 收到相对路径时会解析到 Tomcat 临时目录（multipart 上传场景），
 * 导致附件上传 500「文件写入失败」。修复后写入路径必须为绝对路径，
 * 且文件真实落在 {root}/{warehouseId}/ 目录下（与 download/delete 的 NIO 相对解析一致）。
 */
class WarehouseFileServiceTest {

    @TempDir
    Path tempDir;

    private WarehouseAttachmentRepository attachmentRepository;
    private WarehouseRepository warehouseRepository;
    private WarehouseFileService service;

    @BeforeEach
    void setUp() throws Exception {
        attachmentRepository = mock(WarehouseAttachmentRepository.class);
        warehouseRepository = mock(WarehouseRepository.class);
        service = new WarehouseFileService(attachmentRepository, warehouseRepository);
        Field rootPath = WarehouseFileService.class.getDeclaredField("rootPath");
        rootPath.setAccessible(true);
        rootPath.set(service, tempDir.resolve("attachments").toString());
    }

    @Test
    void uploadWritesFileUnderWarehouseSubdirAndPersistsEntity() throws Exception {
        WarehouseEntity warehouse = WarehouseEntity.builder().id(15L).build();
        when(warehouseRepository.findById(15L)).thenReturn(Optional.of(warehouse));
        when(attachmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "lease.pdf", "application/pdf", "dummy-pdf-content".getBytes());

        WarehouseAttachmentEntity saved = service.upload(15L, WarehouseAttachmentType.LEASE_CONTRACT, file, 1L);

        ArgumentCaptor<WarehouseAttachmentEntity> captor = ArgumentCaptor.forClass(WarehouseAttachmentEntity.class);
        org.mockito.Mockito.verify(attachmentRepository).save(captor.capture());
        String storedFilename = captor.getValue().getStoredFilename();

        Path expectedDir = tempDir.resolve("attachments").resolve("15");
        assertThat(Files.exists(expectedDir)).as("仓库附件子目录应被创建").isTrue();
        try (var stream = Files.list(expectedDir)) {
            List<String> names = stream.map(p -> p.getFileName().toString()).toList();
            assertThat(names).contains(storedFilename);
        }
        Path written = expectedDir.resolve(storedFilename);
        assertThat(Files.readAllBytes(written)).isEqualTo("dummy-pdf-content".getBytes());
        assertThat(saved.getStoredFilename()).isEqualTo(storedFilename);
    }
}
