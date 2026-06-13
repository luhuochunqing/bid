package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.dto.CaBorrowApplicationDTO;
import com.xiyu.bid.resources.dto.CaBorrowEventDTO;
import com.xiyu.bid.resources.dto.CaBorrowRequest;
import com.xiyu.bid.resources.dto.CaReturnRequest;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaBorrowEventEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.notification.CaNotificationDispatcher;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaBorrowEventRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaBorrowService {

    private final CaCertificateRepository certificateRepository;
    private final CaBorrowApplicationRepository borrowRepository;
    private final CaBorrowEventRepository eventRepository;
    private final UserRepository userRepository;
    private final CaNotificationDispatcher caNotificationDispatcher;

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

        // IJTHTX 修复：提交借用申请后通知 CA 保管员
        caNotificationDispatcher.onBorrowSubmitted(cert, app);

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
        app.setApprovedAt(LocalDateTime.now());
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
        app.setReturnedAt(LocalDateTime.now());
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

        // IJTHTX 修复：归还后检查 CA 是否即将到期 / 已过期
        if (cert != null) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), cert.getExpiryDate());
            if (daysLeft < 0) {
                caNotificationDispatcher.onExpired(cert);
            } else if (daysLeft <= 30) {
                caNotificationDispatcher.onExpiring(cert, daysLeft);
            }
        }

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

    // ========== User resolution ==========

    private User resolveUser(UserDetails userDetails) {
        if (userDetails == null) throw new CaBusinessException("未登录");
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new CaBusinessException("用户不存在: " + userDetails.getUsername()));
    }
}
