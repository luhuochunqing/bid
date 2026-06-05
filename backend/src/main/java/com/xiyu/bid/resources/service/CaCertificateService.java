package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.dto.*;
import com.xiyu.bid.resources.entity.*;
import com.xiyu.bid.resources.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaCertificateService {

    private final CaCertificateRepository certificateRepository;
    private final CaBorrowApplicationRepository borrowRepository;
    private final CaBorrowEventRepository eventRepository;
    private final UserRepository userRepository;

    // ========== CA 证书 CRUD ==========

    @Transactional
    public CaCertificateDTO create(CaCertificateRequest request) {
        CaCertificateEntity entity = CaCertificateEntity.builder()
                .platformIds(request.getPlatformIds())
                .caType(request.getCaType())
                .sealType(request.getSealType())
                .electronicAccount(request.getElectronicAccount())
                .caPassword(request.getCaPassword())
                .issuer(request.getIssuer())
                .holderName(request.getHolderName())
                .expiryDate(request.getExpiryDate())
                .caPlatformUrl(request.getCaPlatformUrl())
                .custodianId(request.getCustodianId())
                .custodianName(request.getCustodianName())
                .borrowStatus("IN_STOCK")
                .status(computeStatus(request.getExpiryDate()))
                .remarks(request.getRemarks())
                .build();
        return CaCertificateDTO.from(certificateRepository.save(entity));
    }

    @Transactional
    public CaCertificateDTO update(Long id, CaCertificateRequest request) {
        CaCertificateEntity entity = certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id));
        entity.setPlatformIds(request.getPlatformIds());
        entity.setCaType(request.getCaType());
        entity.setSealType(request.getSealType());
        entity.setElectronicAccount(request.getElectronicAccount());
        if (request.getCaPassword() != null && !request.getCaPassword().isEmpty()) {
            entity.setCaPassword(request.getCaPassword());
        }
        entity.setIssuer(request.getIssuer());
        entity.setHolderName(request.getHolderName());
        entity.setExpiryDate(request.getExpiryDate());
        entity.setCaPlatformUrl(request.getCaPlatformUrl());
        entity.setCustodianId(request.getCustodianId());
        entity.setCustodianName(request.getCustodianName());
        entity.setStatus(computeStatus(request.getExpiryDate()));
        entity.setRemarks(request.getRemarks());
        return CaCertificateDTO.from(certificateRepository.save(entity));
    }

    @Transactional
    public void deactivate(Long id) {
        CaCertificateEntity entity = certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id));
        entity.setStatus("INACTIVE");
        certificateRepository.save(entity);
    }

    public CaCertificateDTO getById(Long id) {
        return CaCertificateDTO.from(certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id)));
    }

    public Page<CaCertificateDTO> list(String status, String borrowStatus, String keyword,
                                        String caType, String sealType, Pageable pageable) {
        Specification<CaCertificateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), "INACTIVE"));
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (borrowStatus != null && !borrowStatus.isEmpty()) {
                predicates.add(cb.equal(root.get("borrowStatus"), borrowStatus));
            }
            if (caType != null && !caType.isEmpty()) {
                predicates.add(cb.equal(root.get("caType"), caType));
            }
            if (sealType != null && !sealType.isEmpty()) {
                predicates.add(cb.equal(root.get("sealType"), sealType));
            }
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("holderName"), pattern),
                        cb.like(root.get("issuer"), pattern),
                        cb.like(root.get("custodianName"), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return certificateRepository.findAll(spec, pageable).map(CaCertificateDTO::from);
    }

    public Map<String, Long> getOverview() {
        Map<String, Long> result = certificateRepository.getOverviewAggregated();
        if (result == null || result.get("total") == null) {
            return Map.of("total", 0L, "expiring", 0L, "expired", 0L, "borrowed", 0L);
        }
        return result;
    }

    // ========== User resolution ==========

    private User resolveUser(UserDetails userDetails) {
        if (userDetails == null) throw new CaBusinessException("未登录");
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new CaBusinessException("用户不存在: " + userDetails.getUsername()));
    }

    // ========== CA 借用申请 ==========

    @Transactional
    public CaBorrowApplicationDTO borrow(UserDetails userDetails, CaBorrowRequest request) {
        User user = resolveUser(userDetails);
        CaCertificateEntity cert = certificateRepository.findById(request.getCaCertificateId())
                .orElseThrow(() -> new CaBusinessException("CA证书不存在"));

        if (!"ENTITY_CA".equals(cert.getCaType())) {
            throw new CaBusinessException("电子CA无需借用");
        }
        if (!"IN_STOCK".equals(cert.getBorrowStatus())) {
            throw new CaBusinessException("CA当前不可借用，状态: " + cert.getBorrowStatus());
        }
        if ("EXPIRED".equals(cert.getStatus())) {
            throw new CaBusinessException("CA已过期，无法借用");
        }

        CaBorrowApplicationEntity app = CaBorrowApplicationEntity.builder()
                .caCertificateId(request.getCaCertificateId())
                .applicantId(user.getId())
                .applicantName(user.getUsername())
                .purpose(request.getPurpose())
                .projectId(request.getProjectId())
                .projectName(request.getProjectName())
                .borrowDurationType(request.getBorrowDurationType())
                .expectedReturnDate(request.getExpectedReturnDate())
                .commitmentLetterUrl(request.getCommitmentLetterUrl())
                .status("PENDING_APPROVAL")
                .approverId(cert.getCustodianId())
                .approverName(cert.getCustodianName())
                .build();
        app = borrowRepository.save(app);

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType("SUBMITTED")
                .actorId(user.getId())
                .actorName(user.getUsername())
                .statusAfter("PENDING_APPROVAL")
                .build());

        return CaBorrowApplicationDTO.from(app);
    }

    @Transactional
    public CaBorrowApplicationDTO approve(Long applicationId, UserDetails userDetails, String comment) {
        User user = resolveUser(userDetails);
        CaBorrowApplicationEntity app = borrowRepository.findById(applicationId)
                .orElseThrow(() -> new CaBusinessException("借用申请不存在"));
        if (!"PENDING_APPROVAL".equals(app.getStatus())) {
            throw new CaBusinessException("申请状态不允许审批: " + app.getStatus());
        }
        if (!app.getApproverId().equals(user.getId())) {
            throw new CaBusinessException("仅CA保管员可审批此申请");
        }

        String statusBefore = app.getStatus();
        app.setStatus("APPROVED");
        app.setApprovalComment(comment);
        app.setApprovedAt(java.time.LocalDateTime.now());
        borrowRepository.save(app);

        CaCertificateEntity cert = certificateRepository.findById(app.getCaCertificateId()).orElse(null);
        if (cert != null) {
            if ("LONG_TERM".equals(app.getBorrowDurationType())) {
                cert.setCustodianId(app.getApplicantId());
                cert.setCustodianName(app.getApplicantName());
                cert.setBorrowStatus("IN_STOCK");
            } else {
                cert.setBorrowStatus("BORROWED");
            }
            certificateRepository.save(cert);
        }

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType("APPROVED")
                .actorId(user.getId())
                .actorName(user.getUsername())
                .comment(comment)
                .statusBefore(statusBefore)
                .statusAfter("APPROVED")
                .build());

        return CaBorrowApplicationDTO.from(app);
    }

    @Transactional
    public CaBorrowApplicationDTO reject(Long applicationId, UserDetails userDetails, String comment) {
        User user = resolveUser(userDetails);
        CaBorrowApplicationEntity app = borrowRepository.findById(applicationId)
                .orElseThrow(() -> new CaBusinessException("借用申请不存在"));
        if (!"PENDING_APPROVAL".equals(app.getStatus())) {
            throw new CaBusinessException("申请状态不允许审批: " + app.getStatus());
        }
        if (!app.getApproverId().equals(user.getId())) {
            throw new CaBusinessException("仅CA保管员可审批此申请");
        }

        String statusBefore = app.getStatus();
        app.setStatus("REJECTED");
        app.setApprovalComment(comment);
        borrowRepository.save(app);

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType("REJECTED")
                .actorId(user.getId())
                .actorName(user.getUsername())
                .comment(comment)
                .statusBefore(statusBefore)
                .statusAfter("REJECTED")
                .build());

        return CaBorrowApplicationDTO.from(app);
    }

    @Transactional
    public CaBorrowApplicationDTO returnCertificate(Long applicationId, UserDetails userDetails, CaReturnRequest request) {
        User user = resolveUser(userDetails);
        CaBorrowApplicationEntity app = borrowRepository.findById(applicationId)
                .orElseThrow(() -> new CaBusinessException("借用申请不存在"));
        if (!"APPROVED".equals(app.getStatus())) {
            throw new CaBusinessException("仅已通过的申请可登记归还: " + app.getStatus());
        }

        CaCertificateEntity cert = certificateRepository.findById(app.getCaCertificateId()).orElse(null);
        if (cert == null) throw new CaBusinessException("CA证书不存在");

        String statusBefore = app.getStatus();
        app.setStatus("RETURNED");
        app.setActualReturnDate(request.getActualReturnDate());
        app.setReturnNotes(request.getReturnNotes());
        app.setReturnedAt(java.time.LocalDateTime.now());
        borrowRepository.save(app);

        if (!"LONG_TERM".equals(app.getBorrowDurationType())) {
            cert.setBorrowStatus("IN_STOCK");
        }
        certificateRepository.save(cert);

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType("RETURNED")
                .actorId(user.getId())
                .actorName(user.getUsername())
                .comment(request.getReturnNotes())
                .statusBefore(statusBefore)
                .statusAfter("RETURNED")
                .build());

        return CaBorrowApplicationDTO.from(app);
    }

    @Transactional
    public CaBorrowApplicationDTO cancelBorrow(Long applicationId, UserDetails userDetails) {
        User user = resolveUser(userDetails);
        CaBorrowApplicationEntity app = borrowRepository.findById(applicationId)
                .orElseThrow(() -> new CaBusinessException("借用申请不存在"));
        if (!"PENDING_APPROVAL".equals(app.getStatus())) {
            throw new CaBusinessException("仅待审批的申请可取消");
        }
        if (!app.getApplicantId().equals(user.getId())) {
            throw new CaBusinessException("仅申请人可取消申请");
        }

        String statusBefore = app.getStatus();
        app.setStatus("CANCELLED");
        borrowRepository.save(app);

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType("CANCELLED")
                .actorId(user.getId())
                .actorName(user.getUsername())
                .statusBefore(statusBefore)
                .statusAfter("CANCELLED")
                .build());

        return CaBorrowApplicationDTO.from(app);
    }

    // ========== 查询 ==========

    public List<CaBorrowApplicationDTO> getBorrowApplicationsByCaId(Long caCertificateId) {
        return borrowRepository.findByCaCertificateIdOrderByCreatedAtDesc(caCertificateId)
                .stream().map(CaBorrowApplicationDTO::from).collect(Collectors.toList());
    }

    public List<CaBorrowEventDTO> getBorrowEvents(Long applicationId) {
        return eventRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId)
                .stream().map(CaBorrowEventDTO::from).collect(Collectors.toList());
    }

    public List<CaBorrowApplicationDTO> getPendingApprovals(UserDetails userDetails) {
        User user = resolveUser(userDetails);
        return borrowRepository.findByApproverIdAndStatus(user.getId(), "PENDING_APPROVAL")
                .stream().map(CaBorrowApplicationDTO::from).collect(Collectors.toList());
    }

    // ========== 辅助 ==========

    private String computeStatus(LocalDate expiryDate) {
        if (expiryDate == null) return "ACTIVE";
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        if (daysUntil < 0) return "EXPIRED";
        if (daysUntil <= 30) return "EXPIRING";
        return "ACTIVE";
    }
}
