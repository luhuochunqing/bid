package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationAttachmentEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.application.QualificationQueryService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QualificationWebServiceReplaceTest {

    @Mock
    private QualificationService qualificationService;

    @Mock
    private QualificationQueryService qualificationQueryService;

    @Mock
    private QualificationAttachmentJpaRepository attachmentRepo;

    @InjectMocks
    private QualificationWebService webService;

    @TempDir
    Path tempDir;

    private QualificationDTO buildDto() {
        QualificationDTO dto = new QualificationDTO();
        dto.setId(1L);
        dto.setName("测试资质");
        return dto;
    }

    private QualificationAttachmentEntity buildAttachment(Long attachmentId, Long qualificationId, String fileName, String fileUrl) {
        QualificationAttachmentEntity entity = new QualificationAttachmentEntity();
        entity.setId(attachmentId);
        entity.setQualificationId(qualificationId);
        entity.setFileName(fileName);
        entity.setFileUrl(fileUrl);
        return entity;
    }

    @Test
    void replaceAttachment_shouldUpdateExistingRecord() throws IOException {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var existing = buildAttachment(10L, 1L, "old-cert.pdf", "123_old-cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));
        when(attachmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultDto = buildDto();
        when(qualificationQueryService.getQualificationById(1L)).thenReturn(resultDto);
        when(qualificationService.updateQualification(eq(1L), any())).thenReturn(resultDto);

        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("new-cert.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        var result = webService.replaceAttachment(1L, 10L, file);

        assertThat(result).isNotNull();
        verify(attachmentRepo).save(argThat(saved ->
                saved.getFileName().equals("new-cert.pdf") &&
                saved.getFileUrl().contains("new-cert.pdf")
        ));
    }

    @Test
    void replaceAttachment_shouldSyncFileUrlToMainEntity() throws IOException {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var existing = buildAttachment(10L, 1L, "old-cert.pdf", "123_old-cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));
        when(attachmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultDto = buildDto();
        when(qualificationQueryService.getQualificationById(1L)).thenReturn(resultDto);
        when(qualificationService.updateQualification(eq(1L), any())).thenReturn(resultDto);

        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("new-cert.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        webService.replaceAttachment(1L, 10L, file);

        // 验证主实体 fileUrl 被同步更新
        verify(qualificationService).updateQualification(eq(1L), argThat(dto ->
                dto.getFileUrl() != null && dto.getFileUrl().contains("new-cert.pdf")
        ));
    }

    @Test
    void replaceAttachment_shouldThrowWhenAttachmentNotBelongToQualification() throws IOException {
        var existing = buildAttachment(10L, 999L, "old-cert.pdf", "123_old-cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));

        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);

        assertThatThrownBy(() -> webService.replaceAttachment(1L, 10L, file))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void replaceAttachment_shouldThrowWhenAttachmentNotFound() throws IOException {
        when(attachmentRepo.findById(999L)).thenReturn(Optional.empty());

        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);

        assertThatThrownBy(() -> webService.replaceAttachment(1L, 999L, file))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void replaceAttachment_shouldThrowWhenFileEmpty() {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> webService.replaceAttachment(1L, 10L, file))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("上传文件不能为空");
    }

    @Test
    void replaceAttachment_shouldCleanUpNewFileOnDbFailure() throws IOException {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var existing = buildAttachment(10L, 1L, "old-cert.pdf", "123_old-cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));
        when(attachmentRepo.save(any())).thenThrow(new RuntimeException("DB error"));

        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("new-cert.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> webService.replaceAttachment(1L, 10L, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");

        // 验证新写入的物理文件已被清理
        Path qualDir = tempDir.resolve("1");
        if (Files.exists(qualDir)) {
            assertThat(Files.list(qualDir).findFirst()).isEmpty();
        }
    }
}
