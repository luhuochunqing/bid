package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationAttachmentEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.application.QualificationQueryService;
import com.xiyu.bid.qualification.dto.QualificationAttachmentDTO;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CO-368 真正根因修复测试：DELETE /qualifications/{id}/attachments/{attachmentId}
 * 之前的 PR !1200 走错方向（清 fileUrl 而非删 attachment 记录）。
 * 本次新增 DELETE 端点，真正删除 attachment 表记录并清理物理文件。
 *
 * CO-368 v2 重构：sync 走 clearFileUrl 轻量级 UPDATE（对称 retire 模式），
 * 避免全量 DTO 重建和意外审计日志。
 */
@ExtendWith(MockitoExtension.class)
class QualificationWebServiceDeleteTest {

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

    private QualificationDTO buildDto(Long id, String fileUrl, java.util.List<QualificationAttachmentDTO> attachments) {
        QualificationDTO dto = new QualificationDTO();
        dto.setId(id);
        dto.setName("测试资质");
        dto.setFileUrl(fileUrl);
        dto.setAttachments(attachments);
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
    void deleteAttachment_shouldRemoveRecordAndReturnUpdatedDto() {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var existing = buildAttachment(10L, 1L, "cert.pdf", "123_cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));

        var resultDto = buildDto(1L, null, List.of());
        when(qualificationQueryService.getQualificationById(1L)).thenReturn(resultDto);

        var result = webService.deleteAttachment(1L, 10L);

        assertThat(result).isNotNull();
        verify(attachmentRepo).delete(existing);
        // CO-368 v2: 强制 flush 避免 JPA 一级缓存返回旧数据
        verify(attachmentRepo).flush();
        // dto.fileUrl=null 与 deletedFileUrl="123_cert.pdf" 不相等 → 不应触发 clearFileUrl
        verify(qualificationService, never()).clearFileUrl(eq(1L));
    }

    @Test
    void deleteAttachment_shouldThrowWhenAttachmentNotFound() {
        when(attachmentRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webService.deleteAttachment(1L, 999L))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void deleteAttachment_shouldThrowWhenAttachmentNotBelongToQualification() {
        var existing = buildAttachment(10L, 999L, "cert.pdf", "123_cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> webService.deleteAttachment(1L, 10L))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void deleteAttachment_shouldCleanupPhysicalFile() throws IOException {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var existing = buildAttachment(10L, 1L, "cert.pdf", "123_cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));

        // 预先创建物理文件
        Path qualDir = tempDir.resolve("1");
        Files.createDirectories(qualDir);
        Path physicalFile = qualDir.resolve("123_cert.pdf");
        Files.write(physicalFile, new byte[]{1, 2, 3});

        var resultDto = buildDto(1L, null, List.of());
        when(qualificationQueryService.getQualificationById(1L)).thenReturn(resultDto);

        webService.deleteAttachment(1L, 10L);

        assertThat(Files.exists(physicalFile)).isFalse();
    }

    @Test
    void deleteAttachment_shouldSyncFileUrlToNullWhenDeletingMainAttachment() {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var existing = buildAttachment(10L, 1L, "cert.pdf", "123_cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));

        // 主实体 fileUrl 等于被删 attachment 的 fileUrl → 应该走 clearFileUrl 轻量级 UPDATE
        var resultDto = buildDto(1L, "123_cert.pdf", List.of());
        when(qualificationQueryService.getQualificationById(1L)).thenReturn(resultDto);

        webService.deleteAttachment(1L, 10L);

        // CO-368 v2: 走 clearFileUrl 轻量级 UPDATE，而非全量 updateQualification
        verify(qualificationService).clearFileUrl(eq(1L));
        // 不应触发全量 updateQualification
        verify(qualificationService, never()).updateQualification(eq(1L), any());
    }

    @Test
    void deleteAttachment_shouldNotSyncFileUrlWhenDeletingNonMainAttachment() {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var existing = buildAttachment(10L, 1L, "cert.pdf", "123_cert.pdf");
        when(attachmentRepo.findById(10L)).thenReturn(Optional.of(existing));

        // 主实体 fileUrl 是另一个文件，不应被清空
        var resultDto = buildDto(1L, "456_other.pdf", List.of());
        when(qualificationQueryService.getQualificationById(1L)).thenReturn(resultDto);

        webService.deleteAttachment(1L, 10L);

        // 不应该触发 clearFileUrl
        verify(qualificationService, never()).clearFileUrl(eq(1L));
    }
}
