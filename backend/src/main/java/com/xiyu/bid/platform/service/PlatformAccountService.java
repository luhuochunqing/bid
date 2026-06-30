// Input: platform repositories, DTOs, and support services
// Output: Platform Account business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.platform.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowAccountRequest;
import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.dto.PlatformAccountDTO;
import com.xiyu.bid.platform.dto.PlatformAccountMapper;
import com.xiyu.bid.platform.dto.PlatformAccountStatisticsDTO;
import com.xiyu.bid.platform.dto.ReturnAccountRequest;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.platform.util.PlatformAccountContactMatcher;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Service for managing Platform Accounts. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountService {

    /** Platform account data access. */
    private final PlatformAccountRepository repository;
    /** CO-403: 委托 BorrowService 同步更新借用申请表状态，避免跨边界直接操作 Repository。 */
    private final PlatformAccountBorrowService borrowService;
    /** Password encryption utility. */
    private final PasswordEncryptionUtil passwordEncryptionUtil;
    /** CO-373 统一角色码解析入口。 */
    private final EffectiveRoleResolver effectiveRoleResolver;
    /** CO-390: contactPerson userId → "姓名（工号）" 展示标签派生（独立类避免行数超 300）。 */
    private final PlatformAccountContactLabelEnricher contactLabelEnricher;

    /** Create a new platform account. */
    @Transactional
    @Auditable(action = "CREATE", entityType = "PlatformAccount",
              description = "Created platform account")
    public PlatformAccountDTO createAccount(PlatformAccountCreateRequest request, User currentUser) {
        validateRequest(request);

        if (repository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        if (repository.findByAccountName(request.getAccountName()).isPresent()) {
            throw new IllegalArgumentException("Account name already exists: " + request.getAccountName());
        }

        String encryptedPassword = passwordEncryptionUtil.encrypt(request.getPassword());

        PlatformAccount account = PlatformAccount.builder()
            .username(request.getUsername())
            .password(encryptedPassword)
            .accountName(request.getAccountName())
            .platformType(request.getPlatformType())
            .url(request.getUrl())
            .contactPerson(request.getContactPerson())
            .contactPhone(request.getContactPhone())
            .contactEmail(request.getContactEmail())
            .hasCa(request.getHasCa() != null ? request.getHasCa() : false)
            .remarks(request.getRemarks())
            .status(AccountStatus.AVAILABLE)
            .returnCount(0)
            .build();

        PlatformAccount savedAccount = repository.save(account);
        return contactLabelEnricher.enrich(PlatformAccountMapper.toDTO(savedAccount));
    }

    /** Return a borrowed account with mandatory password change. CO-403/CO-415. */
    @Transactional
    @Auditable(action = "RETURN", entityType = "PlatformAccount",
              description = "Returned platform account with password change")
    public PlatformAccountDTO returnAccount(Long id, ReturnAccountRequest request, User currentUser) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        PlatformAccountViewerPolicy.checkCanReturnAccount(
            effectiveRoleResolver.resolveRoleCode(currentUser), account, currentUser);
        borrowService.syncReturnedApplication(id);

        String encryptedPassword = passwordEncryptionUtil.encrypt(request.getNewPassword());
        account.returnWithPassword(encryptedPassword);

        PlatformAccount savedAccount = repository.save(account);
        return contactLabelEnricher.enrich(PlatformAccountMapper.toDTO(savedAccount));
    }

    /** Get account by ID. */
    public PlatformAccountDTO getAccountById(Long id) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        return contactLabelEnricher.enrich(PlatformAccountMapper.toDTO(account));
    }

    /** Get all accounts. */
    public List<PlatformAccountDTO> getAllAccounts() {
        List<PlatformAccountDTO> dtos = repository.findAll().stream()
            .map(PlatformAccountMapper::toDTO)
            .collect(Collectors.toList());
        return contactLabelEnricher.enrich(dtos);
    }

    /**
     * Get accounts projected for the given viewer.
     *
     * <p>管理员 / 投标管理员 / 投标组长看到完整 DTO；投标专员对自己为绑定联系人的行
     * 看到完整 DTO，其余行看到脱敏摘要；项目负责人等看到脱敏摘要。</p>
     */
    public List<?> getAccountsForViewer(User viewer) {
        String code = viewer == null ? null : effectiveRoleResolver.resolveRoleCode(viewer);
        boolean privileged = PlatformAccountViewerPolicy.isPrivilegedRole(code);
        boolean bidTeam = PlatformAccountViewerPolicy.isBidTeamRole(code);
        return repository.findAll().stream()
            .map(account -> (privileged || (bidTeam && PlatformAccountContactMatcher.isContactPerson(account, viewer)))
                ? PlatformAccountMapper.toDTO(account)
                : PlatformAccountMapper.toSummaryDTO(account))
            .collect(Collectors.toList());
    }

    /** Update an existing account. */
    @Transactional
    @Auditable(action = "UPDATE", entityType = "PlatformAccount",
              description = "Updated platform account")
    public PlatformAccountDTO updateAccount(Long id, PlatformAccountCreateRequest request, User currentUser) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        PlatformAccountViewerPolicy.checkCanManageAccount(
            effectiveRoleResolver.resolveRoleCode(currentUser), account, currentUser);
        validateUpdateUniqueness(request, account);
        applyUpdateFields(account, request);
        PlatformAccount savedAccount = repository.save(account);
        return contactLabelEnricher.enrich(PlatformAccountMapper.toDTO(savedAccount));
    }

    private void validateUpdateUniqueness(PlatformAccountCreateRequest request, PlatformAccount account) {
        if (request.getUsername() != null && !request.getUsername().trim().isEmpty() && !request.getUsername().equals(account.getUsername()) && repository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        if (request.getAccountName() != null && !request.getAccountName().trim().isEmpty() && !request.getAccountName().equals(account.getAccountName()) && repository.findByAccountName(request.getAccountName()).isPresent()) {
            throw new IllegalArgumentException("Account name already exists: " + request.getAccountName());
        }
    }

    private void applyUpdateFields(PlatformAccount account, PlatformAccountCreateRequest request) {
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty())
            account.setPassword(passwordEncryptionUtil.encrypt(request.getPassword()));
        account.setUsername(request.getUsername() != null ? request.getUsername() : account.getUsername());
        account.setAccountName(request.getAccountName() != null ? request.getAccountName() : account.getAccountName());
        account.setPlatformType(request.getPlatformType() != null ? request.getPlatformType() : account.getPlatformType());
        account.setUrl(request.getUrl() != null ? request.getUrl() : account.getUrl());
        account.setContactPerson(request.getContactPerson() != null ? request.getContactPerson() : account.getContactPerson());
        account.setContactPhone(request.getContactPhone() != null ? request.getContactPhone() : account.getContactPhone());
        account.setContactEmail(request.getContactEmail() != null ? request.getContactEmail() : account.getContactEmail());
        account.setHasCa(request.getHasCa() != null ? request.getHasCa() : account.getHasCa());
        account.setRemarks(request.getRemarks() != null ? request.getRemarks() : account.getRemarks());
    }

    /** Delete a platform account. */
    @Transactional
    @Auditable(action = "DELETE", entityType = "PlatformAccount",
              description = "Deleted platform account")
    public void deleteAccount(Long id, User currentUser) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        repository.delete(account);
    }

    /** Borrow a platform account. */
    @Transactional
    @Auditable(action = "BORROW", entityType = "PlatformAccount",
              description = "Borrowed platform account")
    public PlatformAccountDTO borrowAccount(Long id, BorrowAccountRequest request, User currentUser) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));

        LocalDateTime borrowedAt = LocalDateTime.now();
        LocalDateTime dueAt = resolveDueAt(request, borrowedAt);

        account.borrow(request.getBorrowedBy(), borrowedAt, dueAt);

        PlatformAccount savedAccount = repository.save(account);
        return contactLabelEnricher.enrich(PlatformAccountMapper.toDTO(savedAccount));
    }

    /**
     * Resolve the due-at timestamp from the blueprint-aligned field
     * (expectedReturnDate). Falls back to dueHours for backward compat.
     */
    private LocalDateTime resolveDueAt(BorrowAccountRequest request, LocalDateTime borrowedAt) {
        String expected = request.getExpectedReturnDate();
        if (expected != null && !expected.isBlank()) {
            try {
                String trimmed = expected.length() >= 10 ? expected.substring(0, 10) : expected;
                return LocalDateTime.parse(trimmed + "T23:59:59");
            } catch (java.time.format.DateTimeParseException ex) {
                log.warn("Invalid expectedReturnDate '{}', falling back to dueHours/now+7d", expected);
            }
        }
        Integer dueHours = request.getDueHours();
        if (dueHours != null && dueHours > 0) {
            return borrowedAt.plusDays(dueHours);
        }
        return borrowedAt.plusDays(7);
    }

    /** Return a borrowed account without password change. CO-403/CO-415. */
    @Transactional
    @Auditable(action = "RETURN", entityType = "PlatformAccount",
              description = "Returned platform account")
    public PlatformAccountDTO returnAccount(Long id, User currentUser) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        PlatformAccountViewerPolicy.checkCanReturnAccount(
            effectiveRoleResolver.resolveRoleCode(currentUser), account, currentUser);
        borrowService.syncReturnedApplication(id);

        account.returnToPool();

        PlatformAccount savedAccount = repository.save(account);
        return contactLabelEnricher.enrich(PlatformAccountMapper.toDTO(savedAccount));
    }

    /**
     * Get decrypted password for an account (CO-400 四轮).
     * Allowed for: admin / bidAdmin / bid-TeamLeader OR bid-Team as the account's contact person.
     */
    @Auditable(action = "VIEW_PASSWORD", entityType = "PlatformAccount",
              description = "Viewed password for platform account")
    public String getPassword(Long id, User currentUser) {
        if (currentUser == null) throw new IllegalStateException("Authentication required");
        String code = effectiveRoleResolver.resolveRoleCode(currentUser);
        boolean privileged = PlatformAccountViewerPolicy.isPrivilegedRole(code);
        if (!privileged && PlatformAccountViewerPolicy.isBidTeamRole(code)) {
            PlatformAccount account = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
            if (!PlatformAccountContactMatcher.isContactPerson(account, currentUser))
                throw new IllegalStateException(
                    "Only administrators or the account's contact person can view the password");
            return passwordEncryptionUtil.decrypt(account.getPassword());
        }
        if (!privileged) throw new IllegalStateException("Only administrators can view account passwords");
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        return passwordEncryptionUtil.decrypt(account.getPassword());
    }

    /** Get account statistics. */
    public PlatformAccountStatisticsDTO getStatistics() {
        long totalAccounts = repository.count();
        long availableAccounts = repository.countByStatus(AccountStatus.AVAILABLE);
        long inUseAccounts = repository.countByStatus(AccountStatus.IN_USE);
        long maintenanceAccounts = repository.countByStatus(AccountStatus.MAINTENANCE);
        long disabledAccounts = repository.countByStatus(AccountStatus.DISABLED);

        return PlatformAccountStatisticsDTO.builder()
            .totalAccounts(totalAccounts)
            .availableAccounts(availableAccounts)
            .inUseAccounts(inUseAccounts)
            .maintenanceAccounts(maintenanceAccounts)
            .disabledAccounts(disabledAccounts)
            .build();
    }

    /** Find overdue accounts. */
    public List<PlatformAccountDTO> findOverdueAccounts() {
        List<PlatformAccount> overdueAccounts =
            repository.findOverdueAccounts(AccountStatus.IN_USE, LocalDateTime.now());
        List<PlatformAccountDTO> dtos = overdueAccounts.stream()
            .map(PlatformAccountMapper::toDTO)
            .collect(Collectors.toList());
        return contactLabelEnricher.enrich(dtos);
    }

    private void validateRequest(PlatformAccountCreateRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (request.getAccountName() == null || request.getAccountName().trim().isEmpty()) {
            throw new IllegalArgumentException("Account name cannot be null or empty");
        }
        if (request.getPlatformType() == null) {
            throw new IllegalArgumentException("Platform type cannot be null");
        }
    }
}
