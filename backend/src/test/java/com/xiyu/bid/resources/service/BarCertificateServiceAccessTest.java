package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.BarCertificateBorrowRequest;
import com.xiyu.bid.resources.dto.BarCertificateReturnRequest;
import com.xiyu.bid.resources.entity.BarCertificate;
import com.xiyu.bid.resources.entity.BarCertificateBorrowRecord;
import com.xiyu.bid.resources.repository.BarAssetRepository;
import com.xiyu.bid.resources.repository.BarCertificateBorrowRecordRepository;
import com.xiyu.bid.resources.repository.BarCertificateRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarCertificateServiceAccessTest {

    @Mock
    private BarAssetRepository barAssetRepository;

    @Mock
    private BarCertificateRepository barCertificateRepository;

    @Mock
    private BarCertificateBorrowRecordRepository borrowRecordRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Test
    void borrowCertificate_ShouldRejectInvisibleProjectBeforeSaving() {
        BarCertificateService service = newService();
        when(barAssetRepository.existsById(1L)).thenReturn(true);
        when(barCertificateRepository.findById(2L)).thenReturn(Optional.of(certificate(2L, BarCertificate.CertificateStatus.AVAILABLE)));
        doThrow(new AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(99L);

        BarCertificateBorrowRequest request = new BarCertificateBorrowRequest();
        request.setBorrower("小王");
        request.setProjectId(99L);
        request.setExpectedReturnDate(LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> service.borrowCertificate(1L, 2L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(barCertificateRepository, never()).save(any());
        verify(borrowRecordRepository, never()).save(any());
    }

    @Test
    void getBorrowRecords_ShouldFilterInvisibleProjectRecordsForNonAdmin() {
        BarCertificateService service = newService();
        when(barAssetRepository.existsById(1L)).thenReturn(true);
        when(barCertificateRepository.findById(2L)).thenReturn(Optional.of(certificate(2L, BarCertificate.CertificateStatus.BORROWED)));
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(borrowRecordRepository.findByCertificateIdOrderByBorrowedAtDesc(2L)).thenReturn(List.of(
                record(1L, 2L, 10L),
                record(2L, 2L, null),
                record(3L, 2L, 99L)
        ));

        assertThat(service.getBorrowRecords(1L, 2L))
                .extracting("projectId")
                .containsExactly(10L, null);
    }

    @Test
    void returnCertificate_ShouldRejectInvisibleProjectBorrowRecordBeforeSaving() {
        BarCertificateService service = newService();
        when(barAssetRepository.existsById(1L)).thenReturn(true);
        when(barCertificateRepository.findById(2L)).thenReturn(Optional.of(certificate(2L, BarCertificate.CertificateStatus.BORROWED)));
        when(borrowRecordRepository.findByCertificateIdOrderByBorrowedAtDesc(2L))
                .thenReturn(List.of(record(1L, 2L, 99L)));
        doThrow(new AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(99L);

        assertThatThrownBy(() -> service.returnCertificate(1L, 2L, new BarCertificateReturnRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(borrowRecordRepository, never()).save(any());
        verify(barCertificateRepository, never()).save(any());
    }

    private BarCertificateService newService() {
        return new BarCertificateService(
                barAssetRepository,
                barCertificateRepository,
                borrowRecordRepository,
                projectAccessScopeService
        );
    }

    private BarCertificate certificate(Long id, BarCertificate.CertificateStatus status) {
        return BarCertificate.builder()
                .id(id)
                .barAssetId(1L)
                .type("CA")
                .serialNo("CA-001")
                .status(status)
                .build();
    }

    private BarCertificateBorrowRecord record(Long id, Long certificateId, Long projectId) {
        return BarCertificateBorrowRecord.builder()
                .id(id)
                .certificateId(certificateId)
                .borrower("小王")
                .projectId(projectId)
                .borrowedAt(LocalDateTime.now().minusDays(id))
                .status(BarCertificateBorrowRecord.BorrowStatus.BORROWED)
                .build();
    }
}
