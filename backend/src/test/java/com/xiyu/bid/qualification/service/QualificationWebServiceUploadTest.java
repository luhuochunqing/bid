package com.xiyu.bid.qualification.service;

import com.xiyu.bid.qualification.application.QualificationQueryService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-554 回归测试：uploadAttachment 必须把 fileUrlExplicitlySet=true 打上旗标，
 * 否则 UpdateQualificationAppService 守卫会保留 existing.fileUrl()，
 * 导致表单上传的 fileUrl 不写入主表 → 列表下载按钮缺失。
 */
@ExtendWith(MockitoExtension.class)
class QualificationWebServiceUploadTest {

    @Mock
    private QualificationService qualificationService;

    @Mock
    private QualificationQueryService qualificationQueryService;

    @Mock
    @SuppressWarnings("unused")
    private com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository attachmentRepo;

    @Mock
    @SuppressWarnings("unused")
    private com.xiyu.bid.businessqualification.application.service.ImportQualificationAppService importService;

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

    private MultipartFile mockPdfFile() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("cert.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        return file;
    }

    @Test
    @DisplayName("CO-554: uploadAttachment 传给 updateQualification 的 DTO 必须带 fileUrlExplicitlySet=true")
    void uploadAttachment_ShouldSetFileUrlExplicitlySetFlag() throws IOException {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var resultDto = buildDto();
        when(qualificationQueryService.getQualificationById(1L)).thenReturn(resultDto);
        when(qualificationService.updateQualification(eq(1L), any())).thenReturn(resultDto);

        webService.uploadAttachment(1L, mockPdfFile());

        // 核心断言：旗标必须为 true，否则守卫会丢弃新 fileUrl
        verify(qualificationService).updateQualification(eq(1L), argThat(dto ->
                Boolean.TRUE.equals(dto.getFileUrlExplicitlySet())
                        && dto.getFileUrl() != null
                        && dto.getFileUrl().contains("cert.pdf")
        ));
    }

    @Test
    @DisplayName("CO-554: uploadAttachment 未带旗标是 bug（回归守卫——旗标不能退回 null）")
    void uploadAttachment_FlagMustNotBeNull() throws IOException {
        ReflectionTestUtils.setField(webService, "storageRoot", tempDir.toString());

        var resultDto = buildDto();
        when(qualificationQueryService.getQualificationById(1L)).thenReturn(resultDto);
        when(qualificationService.updateQualification(eq(1L), any())).thenReturn(resultDto);

        webService.uploadAttachment(1L, mockPdfFile());

        // 旗标若退回 null，说明 CO-554 修复被回退，此测试会失败
        verify(qualificationService).updateQualification(eq(1L), argThat(dto ->
                dto.getFileUrlExplicitlySet() != null
        ));
    }
}
