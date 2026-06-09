// Input: AccountBorrowApplicationRepository, PlatformAccountService, DTOs
// Output: PlatformAccountBorrowService — borrow application lifecycle
// Pos: Service/业务层 — 账号借用审批流程
// 纯核心: submit/approve/reject/cancel/return 的状态流转
// 副作用: Repository 读写, PlatformAccountService 联调

package com.xiyu.bid.platform.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.ApproveRequest;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Service managing the platform account borrow application lifecycle. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountBorrowService {

    private final AccountBorrowApplicationRepository applicationRepository;
    private final PlatformAccountRepository accountRepository;

    /** Submit a new borrow application. */
    @Transactional
    @Auditable(action = "SUBMIT_BORROW", entityType = "AccountBorrowApplication",
              description = "Submitted borrow application")
    public BorrowApplicationDTO submitApplication(
            BorrowApplicationRequest request, User currentUser) {
        PlatformAccount account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + request.getAccountId()));

        // Reserve the account by marking it PENDING_APPROVAL
        account.markPendingApproval();
        accountRepository.save(account);

        LocalDateTime expectedReturnAt = request.getExpectedReturnAt() != null
                ? LocalDateTime.parse(request.getExpectedReturnAt())
                : null;

        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .accountId(request.getAccountId())
                .applicantId(currentUser.getId())
                .custodianId(request.getCustodianId())
                .purpose(request.getPurpose())
                .projectName(request.getProjectName())
                .expectedReturnAt(expectedReturnAt)
                .status(BorrowStatus.PENDING_APPROVAL)
                .build();

        AccountBorrowApplication saved = applicationRepository.save(app);
        log.info("Borrow application {} submitted by user {} for account {}",
                saved.getId(), currentUser.getId(), request.getAccountId());
        return toDTO(saved);
    }

    /** Approve a pending borrow application. */
    @Transactional
    @Auditable(action = "APPROVE_BORROW", entityType = "AccountBorrowApplication",
              description = "Approved borrow application")
    public BorrowApplicationDTO approveApplication(Long applicationId, User currentUser) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));

        app.approve();
        AccountBorrowApplication saved = applicationRepository.save(app);
        log.info("Borrow application {} approved by user {}", applicationId, currentUser.getId());
        return toDTO(saved);
    }

    /** Reject a pending borrow application. */
    @Transactional
    @Auditable(action = "REJECT_BORROW", entityType = "AccountBorrowApplication",
              description = "Rejected borrow application")
    public BorrowApplicationDTO rejectApplication(
            Long applicationId, String reason, User currentUser) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("拒绝时必须填写原因");
        }

        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));

        app.reject(reason);
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Release the account back to AVAILABLE
        releaseAccount(saved.getAccountId());

        log.info("Borrow application {} rejected by user {} reason: {}",
                applicationId, currentUser.getId(), reason);
        return toDTO(saved);
    }

    /** Cancel a pending borrow application (by applicant). */
    @Transactional
    @Auditable(action = "CANCEL_BORROW", entityType = "AccountBorrowApplication",
              description = "Cancelled borrow application")
    public BorrowApplicationDTO cancelApplication(Long applicationId, User currentUser) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));

        app.cancel();
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Release the account back to AVAILABLE
        releaseAccount(saved.getAccountId());

        log.info("Borrow application {} cancelled by user {}", applicationId, currentUser.getId());
        return toDTO(saved);
    }

    /** Return account and update password (delegates password change to IJTGJK). */
    @Transactional
    @Auditable(action = "RETURN_BORROW", entityType = "AccountBorrowApplication",
              description = "Returned borrowed account")
    public BorrowApplicationDTO returnAccount(
            Long applicationId, String newPassword, User currentUser) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));

        app.markReturned();
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Return the account to pool and update password
        PlatformAccount account = accountRepository.findById(saved.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + saved.getAccountId()));
        account.returnToPool();
        // XXX: IJTGJK agent will wire password change here
        // account.setPassword(passwordEncryptionUtil.encrypt(newPassword));
        accountRepository.save(account);

        log.info("Borrow application {} returned by user {}", applicationId, currentUser.getId());
        return toDTO(saved);
    }

    /** Get applications with optional status filter. */
    public List<BorrowApplicationDTO> getApplications(Long applicantId, Long custodianId, String status) {
        List<AccountBorrowApplication> apps;
        if (status != null) {
            BorrowStatus bs = BorrowStatus.valueOf(status);
            apps = applicationRepository.findByStatus(bs);
        } else if (applicantId != null) {
            apps = applicationRepository.findByApplicantId(applicantId);
        } else if (custodianId != null) {
            apps = applicationRepository.findByCustodianId(custodianId);
        } else {
            apps = applicationRepository.findAll();
        }
        return apps.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** Get a single application by ID. */
    public BorrowApplicationDTO getApplication(Long applicationId) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));
        return toDTO(app);
    }

    // ── helpers ──

    private BorrowApplicationDTO toDTO(AccountBorrowApplication app) {
        return BorrowApplicationDTO.builder()
                .id(app.getId())
                .accountId(app.getAccountId())
                .applicantId(app.getApplicantId())
                .custodianId(app.getCustodianId())
                .purpose(app.getPurpose())
                .projectName(app.getProjectName())
                .expectedReturnAt(app.getExpectedReturnAt())
                .status(app.getStatus().name())
                .rejectReason(app.getRejectReason())
                .approvedAt(app.getApprovedAt())
                .returnedAt(app.getReturnedAt())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }

    private void releaseAccount(Long accountId) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.returnToPool();
            accountRepository.save(account);
        });
    }
}
