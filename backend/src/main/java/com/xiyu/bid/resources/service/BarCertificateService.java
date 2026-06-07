// Input: resources repositories, DTOs, project access scope, and visibility policy
// Output: Bar Certificate business service operations with project-linked record access control
// Pos: Service/业务编排层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.service;

import com.xiyu.bid.access.core.ProjectLinkedRecordVisibilityPolicy;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.resources.dto.BarCertificateBorrowRecordDTO;
import com.xiyu.bid.resources.dto.BarCertificateBorrowRequest;
import com.xiyu.bid.resources.dto.BarCertificateCreateRequest;
import com.xiyu.bid.resources.dto.BarCertificateResponseDTO;
import com.xiyu.bid.resources.dto.BarCertificateReturnRequest;
import com.xiyu.bid.resources.dto.BarCertificateUpdateRequest;
import com.xiyu.bid.resources.dto.ResourceResponseMapper;
import com.xiyu.bid.resources.entity.BarCertificate;
import com.xiyu.bid.resources.entity.BarCertificateBorrowRecord;
import com.xiyu.bid.resources.repository.BarAssetRepository;
import com.xiyu.bid.resources.repository.BarCertificateBorrowRecordRepository;
import com.xiyu.bid.resources.repository.BarCertificateRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarCertificateService {

    private final BarAssetRepository barAssetRepository;
    private final BarCertificateRepository barCertificateRepository;
    private final BarCertificateBorrowRecordRepository borrowRecordRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    public List<BarCertificateResponseDTO> getCertificates(Long assetId) {
        ensureAssetExists(assetId);
        return barCertificateRepository.findByBarAssetIdOrderByExpiryDateAsc(assetId).stream()
                .map(ResourceResponseMapper::toDto)
                .toList();
    }

    public List<BarCertificateBorrowRecordDTO> getBorrowRecords(Long assetId, Long certificateId) {
        BarCertificate certificate = getCertificate(assetId, certificateId);
        boolean admin = projectAccessScopeService.currentUserHasAdminAccess();
        List<Long> allowedProjectIds = admin ? List.of() : projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        return borrowRecordRepository.findByCertificateIdOrderByBorrowedAtDesc(certificate.getId()).stream()
                .filter(record -> ProjectLinkedRecordVisibilityPolicy.visible(admin, allowedProjectIds, record.getProjectId()))
                .map(ResourceResponseMapper::toDto)
                .toList();
    }

    @Transactional
    public BarCertificateResponseDTO createCertificate(Long assetId, BarCertificateCreateRequest request) {
        ensureAssetExists(assetId);
        BarCertificate certificate = BarCertificate.builder()
                .barAssetId(assetId)
                .type(request.getType())
                .provider(request.getProvider())
                .serialNo(request.getSerialNo())
                .holder(request.getHolder())
                .location(request.getLocation())
                .expiryDate(request.getExpiryDate())
                .status(BarCertificate.CertificateStatus.AVAILABLE)
                .remark(request.getRemark())
                .build();
        return ResourceResponseMapper.toDto(barCertificateRepository.save(certificate));
    }

    @Transactional
    public BarCertificateResponseDTO updateCertificate(Long assetId, Long certificateId, BarCertificateUpdateRequest request) {
        BarCertificate certificate = getCertificate(assetId, certificateId);
        if (request.getType() != null) certificate.setType(request.getType());
        if (request.getProvider() != null) certificate.setProvider(request.getProvider());
        if (request.getSerialNo() != null) certificate.setSerialNo(request.getSerialNo());
        if (request.getHolder() != null) certificate.setHolder(request.getHolder());
        if (request.getLocation() != null) certificate.setLocation(request.getLocation());
        if (request.getExpiryDate() != null) certificate.setExpiryDate(request.getExpiryDate());
        if (request.getRemark() != null) certificate.setRemark(request.getRemark());
        return ResourceResponseMapper.toDto(barCertificateRepository.save(certificate));
    }

    @Transactional
    public void deleteCertificate(Long assetId, Long certificateId) {
        BarCertificate certificate = getCertificate(assetId, certificateId);
        if (certificate.getStatus() == BarCertificate.CertificateStatus.BORROWED) {
            throw new IllegalStateException("Borrowed certificate cannot be deleted");
        }
        barCertificateRepository.delete(certificate);
    }

    @Transactional
    public BarCertificateResponseDTO borrowCertificate(Long assetId, Long certificateId, BarCertificateBorrowRequest request) {
        BarCertificate certificate = getCertificate(assetId, certificateId);
        assertCanAccessProject(request.getProjectId());
        certificate.borrow(
                request.getBorrower(),
                request.getProjectId(),
                request.getPurpose(),
                request.getExpectedReturnDate(),
                request.getRemark()
        );
        BarCertificate saved = barCertificateRepository.save(certificate);

        borrowRecordRepository.save(BarCertificateBorrowRecord.builder()
                .certificateId(saved.getId())
                .borrower(request.getBorrower())
                .projectId(request.getProjectId())
                .purpose(request.getPurpose())
                .remark(request.getRemark())
                .expectedReturnDate(request.getExpectedReturnDate())
                .status(BarCertificateBorrowRecord.BorrowStatus.BORROWED)
                .build());

        return ResourceResponseMapper.toDto(saved);
    }

    @Transactional
    public BarCertificateResponseDTO returnCertificate(Long assetId, Long certificateId, BarCertificateReturnRequest request) {
        BarCertificate certificate = getCertificate(assetId, certificateId);
        if (certificate.getStatus() != BarCertificate.CertificateStatus.BORROWED) {
            throw new IllegalStateException("Only borrowed certificates can be returned");
        }
        List<BarCertificateBorrowRecord> records = borrowRecordRepository.findByCertificateIdOrderByBorrowedAtDesc(certificate.getId());
        BarCertificateBorrowRecord latestBorrow = records.stream()
                .filter(record -> record.getStatus() == BarCertificateBorrowRecord.BorrowStatus.BORROWED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Borrow record not found"));

        assertCanAccessProject(latestBorrow.getProjectId());
        latestBorrow.setStatus(BarCertificateBorrowRecord.BorrowStatus.RETURNED);
        latestBorrow.setReturnedAt(LocalDateTime.now());
        if (request.getRemark() != null && !request.getRemark().isBlank()) {
            latestBorrow.setRemark(request.getRemark());
        }
        borrowRecordRepository.save(latestBorrow);
        certificate.returnToPool(request.getRemark());
        return ResourceResponseMapper.toDto(barCertificateRepository.save(certificate));
    }

    private void ensureAssetExists(Long assetId) {
        if (!barAssetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("BarAsset", String.valueOf(assetId));
        }
    }

    private BarCertificate getCertificate(Long assetId, Long certificateId) {
        ensureAssetExists(assetId);
        BarCertificate certificate = barCertificateRepository.findById(certificateId)
                .orElseThrow(() -> new ResourceNotFoundException("BarCertificate", String.valueOf(certificateId)));
        if (!certificate.getBarAssetId().equals(assetId)) {
            throw new IllegalArgumentException("Certificate does not belong to the specified asset");
        }
        return certificate;
    }

    private void assertCanAccessProject(Long projectId) {
        if (projectId != null) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        }
    }
}
