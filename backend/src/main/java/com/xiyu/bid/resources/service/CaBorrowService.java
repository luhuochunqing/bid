package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.dto.CaBorrowApplicationDTO;
import com.xiyu.bid.resources.dto.CaBorrowEventDTO;
import com.xiyu.bid.resources.dto.CaBorrowRequest;
import com.xiyu.bid.resources.dto.CaReturnRequest;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity.BorrowStatus;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity.BorrowDurationType;
import com.xiyu.bid.resources.entity.CaBorrowEventEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity.CaBorrowStatus;
import com.xiyu.bid.resources.notification.CaNotificationDispatcher;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaBorrowEventRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CaBorrowService {

    private final CaCertificateRepository certificateRepository;
    private final CaBorrowApplicationRepository borrowRepository;
    private final CaBorrowEventRepository eventRepository;
    private final UserRepository userRepository;
    private final CaNotificationDispatcher caNotificationDispatcher;
    private final EffectiveRoleResolver effectiveRoleResolver;
    private final CaBorrowApplicationNameEnricher caNameEnricher;

    // ========== CA 借用申请 ==========

    @Transactional
    public CaBorrowApplicationDTO borrow(UserDetails userDetails, CaBorrowRequest request) {
        User user = resolveUser(userDetails);
        CaCertificateEntity cert = certificateRepository.findById(request.getCaCertificateId())
                .orElseThrow(() -> new CaBusinessException("CA证书不存在"));

        if (!"ENTITY_CA".equals(cert.getCaType())) {
            throw new CaBusinessException("电子CA无需借用");
        }
        if (!CaBorrowStatus.IN_STOCK.name().equals(cert.getBorrowStatus())) {
            throw new CaBusinessException("CA当前不可借用，状态: " + cert.getBorrowStatus());
        }
        if ("EXPIRED".equals(cert.getStatus())) {
            throw new CaBusinessException("CA已过期，无法借用");
        }
        // CO-476: 同一申请人对同一 CA 不允许重复发起待审批申请
        if (borrowRepository.existsByCaCertificateIdAndApplicantIdAndStatus(
                request.getCaCertificateId(), user.getId(), BorrowStatus.PENDING_APPROVAL.name())) {
            throw new CaBusinessException("该CA已有待审批的借用申请，请勿重复申请");
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
                .status(BorrowStatus.PENDING_APPROVAL.name())
                .approverId(cert.getCustodianId())
                .approverName(cert.getCustodianName())
                .build();
        app = borrowRepository.save(app);

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType("SUBMITTED")
                .actorId(user.getId())
                .actorName(user.getUsername())
                .statusAfter(BorrowStatus.PENDING_APPROVAL.name())
                .build());

        caNotificationDispatcher.onBorrowSubmitted(cert, app);

        return CaBorrowApplicationDTO.from(app);
    }

    @Transactional
    public CaBorrowApplicationDTO approve(Long applicationId, UserDetails userDetails, String comment) {
        User user = resolveUser(userDetails);
        CaBorrowApplicationEntity app = borrowRepository.findById(applicationId)
                .orElseThrow(() -> new CaBusinessException("借用申请不存在"));
        if (!BorrowStatus.PENDING_APPROVAL.name().equals(app.getStatus())) {
            throw new CaBusinessException("申请状态不允许审批: " + app.getStatus());
        }
        CaBorrowPermissionChecker.requireCustodianOrPrivilegedRole(app, user, effectiveRoleResolver.resolveRoleCode(user));

        String statusBefore = app.getStatus();
        app.setStatus(BorrowStatus.APPROVED.name());
        app.setApprovalComment(comment);
        app.setApprovedAt(LocalDateTime.now());
        borrowRepository.save(app);

        CaCertificateEntity cert = certificateRepository.findById(app.getCaCertificateId()).orElse(null);
        if (cert != null) {
            if (BorrowDurationType.LONG_TERM.name().equals(app.getBorrowDurationType())) {
                cert.setCustodianId(app.getApplicantId());
                cert.setCustodianName(app.getApplicantName());
                cert.setBorrowStatus(CaBorrowStatus.IN_STOCK.name());
            } else {
                cert.setBorrowStatus(CaBorrowStatus.BORROWED.name());
            }
            certificateRepository.save(cert);
        }

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType(BorrowStatus.APPROVED.name())
                .actorId(user.getId())
                .actorName(user.getUsername())
                .comment(comment)
                .statusBefore(statusBefore)
                .statusAfter(BorrowStatus.APPROVED.name())
                .build());

        caNotificationDispatcher.onBorrowApproved(app);
        log.info("CA借用申请 {} 由用户 {} 审批通过", applicationId, user.getId());
        return CaBorrowApplicationDTO.from(app);
    }

    @Transactional
    public CaBorrowApplicationDTO reject(Long applicationId, UserDetails userDetails, String comment) {
        User user = resolveUser(userDetails);
        CaBorrowApplicationEntity app = borrowRepository.findById(applicationId)
                .orElseThrow(() -> new CaBusinessException("借用申请不存在"));
        if (!BorrowStatus.PENDING_APPROVAL.name().equals(app.getStatus())) {
            throw new CaBusinessException("申请状态不允许审批: " + app.getStatus());
        }
        CaBorrowPermissionChecker.requireCustodianOrPrivilegedRole(app, user, effectiveRoleResolver.resolveRoleCode(user));

        String statusBefore = app.getStatus();
        app.setStatus(BorrowStatus.REJECTED.name());
        app.setApprovalComment(comment);
        borrowRepository.save(app);

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType(BorrowStatus.REJECTED.name())
                .actorId(user.getId())
                .actorName(user.getUsername())
                .comment(comment)
                .statusBefore(statusBefore)
                .statusAfter(BorrowStatus.REJECTED.name())
                .build());

        log.info("CA借用申请 {} 由用户 {} 拒绝，原因: {}", applicationId, user.getId(), comment);
        return CaBorrowApplicationDTO.from(app);
    }

    @Transactional
    public CaBorrowApplicationDTO returnCertificate(Long applicationId, UserDetails userDetails, CaReturnRequest request) {
        User user = resolveUser(userDetails);
        CaBorrowApplicationEntity app = borrowRepository.findById(applicationId)
                .orElseThrow(() -> new CaBusinessException("借用申请不存在"));
        if (!BorrowStatus.APPROVED.name().equals(app.getStatus())) {
            throw new CaBusinessException("仅已通过的申请可登记归还: " + app.getStatus());
        }

        CaCertificateEntity cert = certificateRepository.findById(app.getCaCertificateId()).orElse(null);
        if (cert == null) throw new CaBusinessException("CA证书不存在");

        CaBorrowPermissionChecker.requireCustodianOrPrivilegedRole(app, user, effectiveRoleResolver.resolveRoleCode(user));

        String statusBefore = app.getStatus();
        app.setStatus(BorrowStatus.RETURNED.name());
        app.setActualReturnDate(request.getActualReturnDate());
        app.setReturnNotes(request.getReturnNotes());
        app.setReturnedAt(LocalDateTime.now());
        borrowRepository.save(app);

        if (!BorrowDurationType.LONG_TERM.name().equals(app.getBorrowDurationType())) {
            cert.setBorrowStatus(CaBorrowStatus.IN_STOCK.name());
        }
        certificateRepository.save(cert);

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType(BorrowStatus.RETURNED.name())
                .actorId(user.getId())
                .actorName(user.getUsername())
                .comment(request.getReturnNotes())
                .statusBefore(statusBefore)
                .statusAfter(BorrowStatus.RETURNED.name())
                .build());

        if (cert != null) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), cert.getExpiryDate());
            if (daysLeft < 0) {
                caNotificationDispatcher.onExpired(cert);
            } else if (daysLeft <= 30) {
                caNotificationDispatcher.onExpiring(cert, daysLeft);
            }
        }

        log.info("CA借用申请 {} 由用户 {} 登记归还", applicationId, user.getId());
        return CaBorrowApplicationDTO.from(app);
    }

    @Transactional
    public CaBorrowApplicationDTO cancelBorrow(Long applicationId, UserDetails userDetails) {
        User user = resolveUser(userDetails);
        CaBorrowApplicationEntity app = borrowRepository.findById(applicationId)
                .orElseThrow(() -> new CaBusinessException("借用申请不存在"));
        if (!BorrowStatus.PENDING_APPROVAL.name().equals(app.getStatus())) {
            throw new CaBusinessException("仅待审批的申请可取消");
        }
        if (!app.getApplicantId().equals(user.getId())) {
            throw new CaBusinessException("仅申请人可取消申请");
        }

        String statusBefore = app.getStatus();
        app.setStatus(BorrowStatus.CANCELLED.name());
        borrowRepository.save(app);

        eventRepository.save(CaBorrowEventEntity.builder()
                .applicationId(app.getId())
                .eventType(BorrowStatus.CANCELLED.name())
                .actorId(user.getId())
                .actorName(user.getUsername())
                .statusBefore(statusBefore)
                .statusAfter(BorrowStatus.CANCELLED.name())
                .build());

        return CaBorrowApplicationDTO.from(app);
    }

    // ========== 查询 ==========

    public List<CaBorrowApplicationDTO> getBorrowApplicationsByCaId(Long caCertificateId) {
        return caNameEnricher.enrich(borrowRepository.findByCaCertificateIdOrderByCreatedAtDesc(caCertificateId));
    }

    public List<CaBorrowEventDTO> getBorrowEvents(Long applicationId) {
        return eventRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId)
                .stream().map(CaBorrowEventDTO::from).collect(Collectors.toList());
    }

    /** CO-459: 待审批列表 — 数据库层面过滤，管理员返回全部待审批，保管员返回自己的。 */
    public List<CaBorrowApplicationDTO> getPendingApprovals(UserDetails userDetails) {
        User user = resolveUser(userDetails);
        String roleCode = effectiveRoleResolver.resolveRoleCode(user);
        List<CaBorrowApplicationEntity> apps;
        if (CaBorrowPermissionChecker.isPrivilegedRole(roleCode)) {
            apps = borrowRepository.findByStatusOrderByCreatedAtDesc(BorrowStatus.PENDING_APPROVAL.name());
        } else {
            apps = borrowRepository.findByApproverIdAndStatus(user.getId(), BorrowStatus.PENDING_APPROVAL.name());
        }
        return caNameEnricher.enrich(apps);
    }

    /** CO-459: 我的借用申请 — 返回当前用户作为申请人的全部申请。 */
    public List<CaBorrowApplicationDTO> getMyBorrowApplications(UserDetails userDetails) {
        User user = resolveUser(userDetails);
        return caNameEnricher.enrich(borrowRepository.findByApplicantIdOrderByCreatedAtDesc(user.getId()));
    }

    /** CO-459: 我的审批 Tab — 管理员返回全部申请，保管员返回自己的。 */
    public List<CaBorrowApplicationDTO> findAllApprovals(UserDetails userDetails) {
        User user = resolveUser(userDetails);
        String roleCode = effectiveRoleResolver.resolveRoleCode(user);
        List<CaBorrowApplicationEntity> apps;
        if (CaBorrowPermissionChecker.isPrivilegedRole(roleCode)) {
            apps = borrowRepository.findAllByOrderByCreatedAtDesc();
        } else {
            apps = borrowRepository.findByApproverIdOrderByCreatedAtDesc(user.getId());
        }
        return caNameEnricher.enrich(apps);
    }

    // ========== User resolution ==========

    private User resolveUser(UserDetails userDetails) {
        if (userDetails == null) throw new CaBusinessException("未登录");
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new CaBusinessException("用户不存在: " + userDetails.getUsername()));
    }
}
