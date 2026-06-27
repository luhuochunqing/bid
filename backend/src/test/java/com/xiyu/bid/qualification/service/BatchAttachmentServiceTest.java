package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.domain.port.QualificationFileStorage;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationAttachmentEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import com.xiyu.bid.qualification.dto.BatchAttachResultDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchAttachmentServiceTest {

    @Mock
    private BusinessQualificationJpaRepository repository;

    @Mock
    private QualificationFileStorage fileStorage;

    @Mock
    private QualificationAttachmentJpaRepository attachmentRepository;

    @InjectMocks
    private BatchAttachmentService service;

    @Test
    void process_EmptyList_ShouldReturnEmpty() {
        var result = service.process(List.of());
        assertThat(result.getTotal()).isZero();
        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFailed()).isZero();
    }

    @Test
    void process_NullList_ShouldReturnEmpty() {
        var result = service.process(null);
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void process_NullFileName_ShouldCountAsUnmatched() {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(null);

        var result = service.process(List.of(file));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getUnmatched().get(0).getReason()).isEqualTo("文件名缺失");
    }

    @Test
    void process_UnmatchedFormat_ShouldCountAsUnmatched() {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("random.pdf");

        var result = service.process(List.of(file));

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getUnmatched().get(0).getReason()).contains("命名格式不符");
    }

    @Test
    void process_CertificateNoNotFound_ShouldCountAsUnmatched() {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_UNKNOWN_01_test.pdf");
        when(repository.findAllByCertificateNo("UNKNOWN")).thenReturn(List.of());

        var result = service.process(List.of(file));

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getUnmatched().get(0).getReason()).isEqualTo("证书编号不存在");
    }

    @Test
    void process_ValidFile_ShouldMatchAndSave() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_QC-001_01_doc.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        var entity = new BusinessQualificationEntity();
        entity.setId(1L);
        entity.setName("ISO认证");
        when(repository.findAllByCertificateNo("QC-001")).thenReturn(List.of(entity));
        when(fileStorage.storeAttachmentWithNaming(eq(1L), any(byte[].class), eq("QC-001"),
                eq(1), eq("ISO认证"), eq("QUAL_QC-001_01_doc.pdf"), isNull()))
                .thenReturn("QUAL_QC-001_01_ISO认证.pdf");

        var result = service.process(List.of(file));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
        assertThat(result.getMatched().get(0).getCertificateNo()).isEqualTo("QC-001");
        verify(repository).save(entity);
        verify(attachmentRepository).save(any(QualificationAttachmentEntity.class));
    }

    @Test
    void process_MultipleFiles_ShouldHandleMixedResults() throws IOException {
        var validFile = mock(MultipartFile.class);
        when(validFile.isEmpty()).thenReturn(false);
        when(validFile.getOriginalFilename()).thenReturn("QUAL_VALID_01_ok.pdf");
        when(validFile.getBytes()).thenReturn(new byte[]{1, 2, 3});

        var invalidFile = mock(MultipartFile.class);
        when(invalidFile.isEmpty()).thenReturn(false);
        when(invalidFile.getOriginalFilename()).thenReturn("bad_name.txt");

        var badNameFile = mock(MultipartFile.class);
        when(badNameFile.isEmpty()).thenReturn(false);
        when(badNameFile.getOriginalFilename()).thenReturn("QUAL_INVALID_01_test.pdf");

        var entity = new BusinessQualificationEntity();
        entity.setId(10L);
        entity.setName("有效证书");
        when(repository.findAllByCertificateNo("VALID")).thenReturn(List.of(entity));
        when(repository.findAllByCertificateNo("INVALID")).thenReturn(List.of());
        when(fileStorage.storeAttachmentWithNaming(eq(10L), any(byte[].class), eq("VALID"),
                eq(1), eq("有效证书"), eq("QUAL_VALID_01_ok.pdf"), isNull()))
                .thenReturn("QUAL_VALID_01_有效证书.pdf");

        var result = service.process(List.of(validFile, invalidFile, badNameFile));

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(2);
        assertThat(result.getMatched()).hasSize(1);
        assertThat(result.getUnmatched()).hasSize(2);
    }

    @Test
    void process_MultipleRecordsWithSameCertificateNo_ShouldUseLatest() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_QC-001_01_doc.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        var oldEntity = new BusinessQualificationEntity();
        oldEntity.setId(10L);
        oldEntity.setName("旧版ISO认证");

        var latestEntity = new BusinessQualificationEntity();
        latestEntity.setId(20L);
        latestEntity.setName("新版ISO认证");

        when(repository.findAllByCertificateNo("QC-001"))
                .thenReturn(List.of(oldEntity, latestEntity));
        when(fileStorage.storeAttachmentWithNaming(eq(20L), any(byte[].class), eq("QC-001"),
                eq(1), eq("新版ISO认证"), eq("QUAL_QC-001_01_doc.pdf"), isNull()))
                .thenReturn("QUAL_QC-001_01_新版ISO认证.pdf");

        var result = service.process(List.of(file));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
        assertThat(result.getMatched().get(0).getQualificationId()).isEqualTo(20L);
        assertThat(result.getMatched().get(0).getQualificationName()).isEqualTo("新版ISO认证");
        verify(repository).save(latestEntity);
    }

    @Test
    void process_SingleRecordWithCertificateNo_ShouldMatchNormally() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_SINGLE_01_test.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        var entity = new BusinessQualificationEntity();
        entity.setId(5L);
        entity.setName("单条证书");

        when(repository.findAllByCertificateNo("SINGLE")).thenReturn(List.of(entity));
        when(fileStorage.storeAttachmentWithNaming(eq(5L), any(byte[].class), eq("SINGLE"),
                eq(1), eq("单条证书"), eq("QUAL_SINGLE_01_test.pdf"), isNull()))
                .thenReturn("QUAL_SINGLE_01_单条证书.pdf");

        var result = service.process(List.of(file));

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getMatched().get(0).getQualificationId()).isEqualTo(5L);
    }

    @Test
    void process_NoRecordWithCertificateNo_ShouldUnmatched() {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_NOEXIST_01_test.pdf");

        when(repository.findAllByCertificateNo("NOEXIST")).thenReturn(List.of());

        var result = service.process(List.of(file));

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getUnmatched().get(0).getReason()).isEqualTo("证书编号不存在");
    }

    @Test
    void process_CertificateNoWithLeadingSpaces_ShouldTrimAndMatch() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_ 5201267890_02_文件的名.docx");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        var entity = new BusinessQualificationEntity();
        entity.setId(7L);
        entity.setName("测试证书");

        when(repository.findAllByCertificateNo("5201267890")).thenReturn(List.of(entity));
        when(fileStorage.storeAttachmentWithNaming(eq(7L), any(byte[].class), eq("5201267890"),
                eq(2), eq("测试证书"), eq("QUAL_ 5201267890_02_文件的名.docx"), isNull()))
                .thenReturn("QUAL_5201267890_02_测试证书.docx");

        var result = service.process(List.of(file));

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getMatched().get(0).getQualificationId()).isEqualTo(7L);
        assertThat(result.getMatched().get(0).getCertificateNo()).isEqualTo("5201267890");
        verify(repository).findAllByCertificateNo("5201267890");
    }

    @Test
    void process_ValidFile_ShouldCreateAttachmentRecordWithCorrectFields() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_QC-001_01_doc.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        var entity = new BusinessQualificationEntity();
        entity.setId(1L);
        entity.setName("ISO认证");
        when(repository.findAllByCertificateNo("QC-001")).thenReturn(List.of(entity));
        when(fileStorage.storeAttachmentWithNaming(eq(1L), any(byte[].class), eq("QC-001"),
                eq(1), eq("ISO认证"), eq("QUAL_QC-001_01_doc.pdf"), isNull()))
                .thenReturn("QUAL_QC-001_01_ISO认证.pdf");

        service.process(List.of(file));

        ArgumentCaptor<QualificationAttachmentEntity> captor = ArgumentCaptor.forClass(QualificationAttachmentEntity.class);
        verify(attachmentRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getQualificationId()).isEqualTo(1L);
        assertThat(saved.getFileName()).isEqualTo("QUAL_QC-001_01_doc.pdf");
        assertThat(saved.getFileUrl()).isEqualTo("QUAL_QC-001_01_ISO认证.pdf");
        assertThat(saved.getUploadedAt()).isNotNull();
    }

    @Test
    void process_ValidFile_ShouldSetFileUrlToFilenameNotApiUrl() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_QC-001_01_doc.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        var entity = new BusinessQualificationEntity();
        entity.setId(1L);
        entity.setName("ISO认证");
        when(repository.findAllByCertificateNo("QC-001")).thenReturn(List.of(entity));
        when(fileStorage.storeAttachmentWithNaming(eq(1L), any(byte[].class), eq("QC-001"),
                eq(1), eq("ISO认证"), eq("QUAL_QC-001_01_doc.pdf"), isNull()))
                .thenReturn("QUAL_QC-001_01_ISO认证.pdf");

        service.process(List.of(file));

        assertThat(entity.getFileUrl()).isEqualTo("QUAL_QC-001_01_ISO认证.pdf");
        assertThat(entity.getFileUrl()).doesNotStartWith("/api/");
    }
}
