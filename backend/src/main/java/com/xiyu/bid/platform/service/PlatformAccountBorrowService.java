// Input: AccountBorrowApplicationRepository, PlatformAccountService, DTOs
// Output: PlatformAccountBorrowService — borrow application lifecycle
// Pos: Service/业务层 — 账号借用审批流程
// 纯核心: submit/approve/reject/cancel/return 的状态流转
// 副作用: Repository 读写, PlatformAccountService 联调

package com.xiyu.bid.platform.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/** Service managing the platform account borrow application lifecycle. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountBorrowService {

    private final AccountBorrowApplicationRepository applicationRepository;
    private final PlatformAccountRepository accountRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;
    private final AccountBorrowApplicationMapper applicationMapper;

    /** Submit a new borrow application. */
    @Transactional
    @Auditable(action = "SUBMIT_BORROW", entityType = "AccountBorrowApplication",
              description = "Submitted borrow application")
    public BorrowApplicationDTO submitApplication(
            BorrowApplicationRequest request, User currentUser) {
        PlatformAccount account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new BusinessException("账号不存在: " + request.getAccountId()));

        // CO-386: custodianId 可由前端省略；未传时从 account.contactPerson 自动取值。
        Long custodianId = request.getCustodianId() != null
                ? request.getCustodianId()
                : account.getContactPerson();
        if (custodianId == null) {
            throw new BusinessException("该账户未绑定联系人，无法发起借用申请");
        }
        if (!custodianId.equals(account.getContactPerson())) {
            throw new BusinessException("保管员信息不匹配");
        }

        // Reserve the account by marking it PENDING_APPROVAL
        account.markPendingApproval();
        accountRepository.save(account);

        LocalDateTime expectedReturnAt = parseExpectedReturnAt(request.getExpectedReturnAt());

        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .accountId(request.getAccountId())
                .applicantId(currentUser.getId())
                .custodianId(custodianId)
                .purpose(request.getPurpose())
                .projectName(request.getProjectName())
                .projectId(request.getProjectId())
                .expectedReturnAt(expectedReturnAt)
                .status(BorrowStatus.PENDING_APPROVAL)
                .build();

        AccountBorrowApplication saved = applicationRepository.save(app);
        log.info("Borrow application {} submitted by user {} for account {}",
                saved.getId(), currentUser.getId(), request.getAccountId());
        return applicationMapper.toDTO(saved);
    }

    /** Approve a pending borrow application.
     * CO-403: 管理员角色可审批任意申请。 */
    @Transactional
    @Auditable(action = "APPROVE_BORROW", entityType = "AccountBorrowApplication",
              description = "Approved borrow application")
    public BorrowApplicationDTO approveApplication(Long applicationId, String comment, User currentUser, boolean isPrivileged) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException("申请不存在: " + applicationId));

        requireCustodianOrPrivilegedRole(app, currentUser, isPrivileged);
        app.approve(comment);
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Hand the account over to the applicant
        PlatformAccount account = accountRepository.findById(saved.getAccountId())
                .orElseThrow(() -> new BusinessException("账号不存在: " + saved.getAccountId()));
        account.approveBorrow(saved.getApplicantId(), saved.getApprovedAt(), saved.getExpectedReturnAt());
        accountRepository.save(account);

        log.info("Borrow application {} approved by user {}", applicationId, currentUser.getId());
        return applicationMapper.toDTO(saved);
    }

    /** Reject a pending borrow application. */
    @Transactional
    @Auditable(action = "REJECT_BORROW", entityType = "AccountBorrowApplication",
              description = "Rejected borrow application")
    public BorrowApplicationDTO rejectApplication(
            Long applicationId, String reason, User currentUser, boolean isPrivileged) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("拒绝时必须填写原因");
        }

        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException("申请不存在: " + applicationId));

        requireCustodianOrPrivilegedRole(app, currentUser, isPrivileged);
        app.reject(reason);
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Release the account back to AVAILABLE
        releaseAccount(saved.getAccountId());

        log.info("Borrow application {} rejected by user {} reason: {}",
                applicationId, currentUser.getId(), reason);
        return applicationMapper.toDTO(saved);
    }

    /** Cancel a pending borrow application (by applicant). */
    @Transactional
    @Auditable(action = "CANCEL_BORROW", entityType = "AccountBorrowApplication",
              description = "Cancelled borrow application")
    public BorrowApplicationDTO cancelApplication(Long applicationId, User currentUser) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException("申请不存在: " + applicationId));

        requireApplicant(app, currentUser);
        app.cancel();
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Release the account back to AVAILABLE
        releaseAccount(saved.getAccountId());

        log.info("Borrow application {} cancelled by user {}", applicationId, currentUser.getId());
        return applicationMapper.toDTO(saved);
    }

    /** Return account and update password. */
    @Transactional
    @Auditable(action = "RETURN_BORROW", entityType = "AccountBorrowApplication",
              description = "Returned borrowed account")
    public BorrowApplicationDTO returnAccount(
            Long applicationId, String newPassword, LocalDateTime actualReturnedAt, User currentUser, boolean isPrivileged) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码长度不能少于6位");
        }

        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException("申请不存在: " + applicationId));

        requireCustodianOrPrivilegedRole(app, currentUser, isPrivileged);
        app.markReturned(actualReturnedAt);
        AccountBorrowApplication saved = applicationRepository.save(app);

        // Return the account to pool and update password
        PlatformAccount account = accountRepository.findById(saved.getAccountId())
                .orElseThrow(() -> new BusinessException("账号不存在: " + saved.getAccountId()));
        account.returnWithPassword(passwordEncryptionUtil.encrypt(newPassword));
        accountRepository.save(account);

        log.info("Borrow application {} returned by user {} (password updated)", applicationId, currentUser.getId());
        return applicationMapper.toDTO(saved);
    }

    /** Get applications with optional status filter. */
    public List<BorrowApplicationDTO> getApplications(Long applicantId, Long custodianId, String status) {
        List<AccountBorrowApplication> apps;
        if (status != null) {
            BorrowStatus bs = parseBorrowStatus(status);
            apps = applicationRepository.findByStatus(bs);
        } else if (applicantId != null) {
            apps = applicationRepository.findByApplicantId(applicantId);
        } else if (custodianId != null) {
            apps = applicationRepository.findByCustodianId(custodianId);
        } else {
            apps = applicationRepository.findAll();
        }
        return applicationMapper.toDTOList(apps);
    }

    /**
     * CO-403: 管理员查看全部待审批申请。
     * 仅返回 PENDING_APPROVAL 状态的申请。
     */
    public List<BorrowApplicationDTO> findPendingApprovals() {
        return applicationMapper.toDTOList(applicationRepository.findByStatus(BorrowStatus.PENDING_APPROVAL));
    }

    /**
     * CO-403: 同步更新账号对应的借用申请状态为 RETURNED。
     * 供 PlatformAccountService.returnAccount 委托调用，避免跨边界直接操作 Repository。
     */
    @Transactional
    public void syncReturnedApplication(Long accountId) {
        applicationRepository.findByAccountIdAndStatus(accountId, BorrowStatus.BORROWED)
                .stream()
                .findFirst()
                .ifPresent(app -> {
                    app.markReturned(LocalDateTime.now());
                    applicationRepository.save(app);
                    log.info("同步更新借用申请 {} 状态为 RETURNED", app.getId());
                });
    }

    /** Get a single application by ID. */
    public BorrowApplicationDTO getApplication(Long applicationId) {
        AccountBorrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException("申请不存在: " + applicationId));
        return applicationMapper.toDTO(app);
    }

    // ── helpers ──

    private void releaseAccount(Long accountId) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.returnToPool();
            accountRepository.save(account);
        });
    }

    /**
     * CO-403: 校验当前用户是否有权操作借用申请。
     * 规则：
     * - 管理员角色（admin / bidAdmin / bid-TeamLeader）可操作任意申请
     * - 绑定联系人（custodianId）可操作对应申请
     * - 其他用户无权操作
     */
    private void requireCustodianOrPrivilegedRole(AccountBorrowApplication app, User currentUser, boolean isPrivileged) {
        if (currentUser == null) {
            throw new BusinessException("用户未登录");
        }
        // 管理员角色豁免
        if (isPrivileged) {
            log.info("管理员 {} 跨权限操作借用申请 {}", currentUser.getId(), app.getId());
            return;
        }
        // 绑定联系人校验
        if (!currentUser.getId().equals(app.getCustodianId())) {
            throw new BusinessException("只有账号绑定联系人可以操作该申请");
        }
    }

    private void requireApplicant(AccountBorrowApplication app, User currentUser) {
        if (currentUser == null || !currentUser.getId().equals(app.getApplicantId())) {
            throw new BusinessException("只有申请人可以撤销该申请");
        }
    }

    private LocalDateTime parseExpectedReturnAt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException("预计归还时间格式不正确: " + value);
        }
    }

    private BorrowStatus parseBorrowStatus(String value) {
        try {
            return BorrowStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("非法的申请状态: " + value);
        }
    }
}
