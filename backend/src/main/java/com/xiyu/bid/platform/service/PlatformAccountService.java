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
    /** Password encryption utility. */
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    /** Create a new platform account. */
    @Transactional
    @Auditable(action = "CREATE", entityType = "PlatformAccount",
              description = "Created platform account")
    public PlatformAccountDTO createAccount(PlatformAccountCreateRequest request, User currentUser) {
        validateRequest(request);

        if (repository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
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
            .caCustodian(request.getCaCustodian())
            .custodian(request.getCustodian())
            .remarks(request.getRemarks())
            .status(AccountStatus.AVAILABLE)
            .returnCount(0)
            .build();

        PlatformAccount savedAccount = repository.save(account);
        return PlatformAccountMapper.toDTO(savedAccount);
    }

    /** Return a borrowed account with mandatory password change. */
    @Transactional
    @Auditable(action = "RETURN", entityType = "PlatformAccount",
              description = "Returned platform account with password change")
    public PlatformAccountDTO returnAccount(Long id, ReturnAccountRequest request, User currentUser) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));

        String encryptedPassword = passwordEncryptionUtil.encrypt(request.getNewPassword());
        account.returnWithPassword(encryptedPassword);

        PlatformAccount savedAccount = repository.save(account);
        return PlatformAccountMapper.toDTO(savedAccount);
    }



    /** Get account by ID. */
    public PlatformAccountDTO getAccountById(Long id) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        return PlatformAccountMapper.toDTO(account);
    }

    /** Get all accounts. */
    public List<PlatformAccountDTO> getAllAccounts() {
        return repository.findAll().stream()
            .map(PlatformAccountMapper::toDTO)
            .collect(Collectors.toList());
    }

    /** Update an existing account. */
    @Transactional
    @Auditable(action = "UPDATE", entityType = "PlatformAccount",
              description = "Updated platform account")
    public PlatformAccountDTO updateAccount(Long id, PlatformAccountCreateRequest request, User currentUser) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));

        if (request.getUsername() != null
                && !request.getUsername().trim().isEmpty()
                && !request.getUsername().equals(account.getUsername())
                && repository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }

        String encryptedPassword = request.getPassword() != null && !request.getPassword().trim().isEmpty()
                ? passwordEncryptionUtil.encrypt(request.getPassword())
                : account.getPassword();

        account.setUsername(request.getUsername() != null ? request.getUsername() : account.getUsername());
        account.setPassword(encryptedPassword);
        account.setAccountName(request.getAccountName() != null ? request.getAccountName() : account.getAccountName());
        account.setPlatformType(request.getPlatformType() != null ? request.getPlatformType() : account.getPlatformType());
        account.setUrl(request.getUrl() != null ? request.getUrl() : account.getUrl());
        account.setContactPerson(request.getContactPerson() != null ? request.getContactPerson() : account.getContactPerson());
        account.setContactPhone(request.getContactPhone() != null ? request.getContactPhone() : account.getContactPhone());
        account.setContactEmail(request.getContactEmail() != null ? request.getContactEmail() : account.getContactEmail());
        account.setHasCa(request.getHasCa() != null ? request.getHasCa() : account.getHasCa());
        account.setCaCustodian(request.getCaCustodian() != null ? request.getCaCustodian() : account.getCaCustodian());
        account.setCustodian(request.getCustodian() != null ? request.getCustodian() : account.getCustodian());
        account.setRemarks(request.getRemarks() != null ? request.getRemarks() : account.getRemarks());

        PlatformAccount savedAccount = repository.save(account);
        return PlatformAccountMapper.toDTO(savedAccount);
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

        account.borrow(request.getBorrowedBy(), LocalDateTime.now(), LocalDateTime.now().plusDays(request.getDueHours() != null ? request.getDueHours() : 7));

        PlatformAccount savedAccount = repository.save(account);
        return PlatformAccountMapper.toDTO(savedAccount);
    }

    /** Return a borrowed account. */
    @Transactional
    @Auditable(action = "RETURN", entityType = "PlatformAccount",
              description = "Returned platform account")
    public PlatformAccountDTO returnAccount(Long id, User currentUser) {
        PlatformAccount account = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        account.returnToPool();

        PlatformAccount savedAccount = repository.save(account);
        return PlatformAccountMapper.toDTO(savedAccount);
    }

    /** Get decrypted password for an account (ADMIN only). */
    @Auditable(action = "VIEW_PASSWORD", entityType = "PlatformAccount",
              description = "Viewed password for platform account")
    public String getPassword(Long id, User currentUser) {
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            throw new IllegalStateException("Only administrators can view account passwords");
        }

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
        return overdueAccounts.stream()
            .map(PlatformAccountMapper::toDTO)
            .collect(Collectors.toList());
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
