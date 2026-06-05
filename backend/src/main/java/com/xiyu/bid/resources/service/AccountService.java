// Input: resources repositories, DTOs, and support services
// Output: Account business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.AccountCreateRequest;
import com.xiyu.bid.resources.dto.AccountResponseDTO;
import com.xiyu.bid.resources.dto.AccountUpdateRequest;
import com.xiyu.bid.resources.dto.ResourceResponseMapper;
import com.xiyu.bid.resources.entity.Account;
import com.xiyu.bid.resources.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponseDTO createAccount(AccountCreateRequest request) {
        // Validation
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Type is required");
        }
        if (request.getCreditLevel() == null) {
            throw new IllegalArgumentException("Credit level is required");
        }

        // Check for duplicate name
        accountRepository.findByName(request.getName())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Account with this name already exists");
                });

        Account account = Account.builder()
                .name(request.getName())
                .type(request.getType())
                .contactInfo(request.getContactInfo())
                .industry(request.getIndustry())
                .region(request.getRegion())
                .creditLevel(request.getCreditLevel())
                .build();

        return ResourceResponseMapper.toDto(accountRepository.save(account));
    }

    public AccountResponseDTO getAccountById(Long id) {
        return accountRepository.findById(id)
                .map(ResourceResponseMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
    }

    public Page<AccountResponseDTO> getAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable).map(ResourceResponseMapper::toDto);
    }

    public Page<AccountResponseDTO> getAccountsByType(String type, Pageable pageable) {
        return accountRepository.findByType(Account.AccountType.valueOf(type), pageable)
                .map(ResourceResponseMapper::toDto);
    }

    public Page<AccountResponseDTO> getAccountsByIndustry(String industry, Pageable pageable) {
        return accountRepository.findByIndustry(industry, pageable).map(ResourceResponseMapper::toDto);
    }

    public Page<AccountResponseDTO> getAccountsByRegion(String region, Pageable pageable) {
        return accountRepository.findByRegion(region, pageable).map(ResourceResponseMapper::toDto);
    }

    public Page<AccountResponseDTO> getAccountsByCreditLevel(String creditLevel, Pageable pageable) {
        return accountRepository.findByCreditLevel(Account.CreditLevel.valueOf(creditLevel), pageable)
                .map(ResourceResponseMapper::toDto);
    }

    public Page<AccountResponseDTO> searchAccounts(String keyword, Pageable pageable) {
        return accountRepository.searchByNameContainingIgnoreCase(keyword, pageable)
                .map(ResourceResponseMapper::toDto);
    }

    @Transactional
    public AccountResponseDTO updateAccount(Long id, AccountUpdateRequest request) {
        Account account = getAccountEntityById(id);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            // Check if name is being changed and if new name already exists
            if (!account.getName().equals(request.getName())) {
                accountRepository.findByName(request.getName())
                        .ifPresent(existing -> {
                            throw new IllegalArgumentException("Account with this name already exists");
                        });
                account.setName(request.getName());
            }
        }
        if (request.getType() != null) {
            account.setType(request.getType());
        }
        if (request.getContactInfo() != null) {
            account.setContactInfo(request.getContactInfo());
        }
        if (request.getIndustry() != null) {
            account.setIndustry(request.getIndustry());
        }
        if (request.getRegion() != null) {
            account.setRegion(request.getRegion());
        }
        if (request.getCreditLevel() != null) {
            account.setCreditLevel(request.getCreditLevel());
        }

        return ResourceResponseMapper.toDto(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new RuntimeException("Account not found with id: " + id);
        }
        accountRepository.deleteById(id);
    }

    public Map<String, Object> getAccountStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalAccounts", accountRepository.count());

        for (Account.AccountType type : Account.AccountType.values()) {
            statistics.put(type.name().toLowerCase() + "Count", accountRepository.countByType(type));
        }

        return statistics;
    }

    private Account getAccountEntityById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
    }
}
