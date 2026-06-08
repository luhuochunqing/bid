package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.qualification.dto.BatchAttachResultDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchAttachmentServiceTest {

    @Mock
    private BusinessQualificationJpaRepository repository;

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
        when(repository.findByCertificateNo("UNKNOWN")).thenReturn(Optional.empty());

        var result = service.process(List.of(file));

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getUnmatched().get(0).getReason()).isEqualTo("证书编号不存在");
    }

    @Test
    void process_ValidFile_ShouldMatchAndSave() {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("QUAL_QC-001_01_doc.pdf");

        var entity = new BusinessQualificationEntity();
        entity.setId(1L);
        entity.setName("ISO认证");
        when(repository.findByCertificateNo("QC-001")).thenReturn(Optional.of(entity));

        var result = service.process(List.of(file));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
        assertThat(result.getMatched().get(0).getCertificateNo()).isEqualTo("QC-001");
        verify(repository).save(entity);
    }

    @Test
    void process_MultipleFiles_ShouldHandleMixedResults() {
        var validFile = mock(MultipartFile.class);
        when(validFile.isEmpty()).thenReturn(false);
        when(validFile.getOriginalFilename()).thenReturn("QUAL_VALID_01_ok.pdf");

        var invalidFile = mock(MultipartFile.class);
        when(invalidFile.isEmpty()).thenReturn(false);
        when(invalidFile.getOriginalFilename()).thenReturn("bad_name.txt");

        var badNameFile = mock(MultipartFile.class);
        when(badNameFile.isEmpty()).thenReturn(false);
        when(badNameFile.getOriginalFilename()).thenReturn("QUAL_INVALID_01_test.pdf");

        var entity = new BusinessQualificationEntity();
        entity.setId(10L);
        entity.setName("有效证书");
        when(repository.findByCertificateNo("VALID")).thenReturn(Optional.of(entity));
        when(repository.findByCertificateNo("INVALID")).thenReturn(Optional.empty());

        var result = service.process(List.of(validFile, invalidFile, badNameFile));

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(2);
        assertThat(result.getMatched()).hasSize(1);
        assertThat(result.getUnmatched()).hasSize(2);
    }
}
