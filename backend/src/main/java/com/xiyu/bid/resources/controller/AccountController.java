// Input: resources service and request DTOs
// Output: Account REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.config.PaginationConstants;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.resources.dto.AccountCreateRequest;
import com.xiyu.bid.resources.dto.AccountResponseDTO;
import com.xiyu.bid.resources.dto.AccountUpdateRequest;
import com.xiyu.bid.resources.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/resources/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Account", description = "Create account record")
    public ResponseEntity<ApiResponse<AccountResponseDTO>> createAccount(@Valid @RequestBody AccountCreateRequest request) {
        AccountResponseDTO account = accountService.createAccount(request);
        return ResponseEntity.ok(ApiResponse.success("Account created successfully", account));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<AccountResponseDTO>> getAccountById(@PathVariable Long id) {
        AccountResponseDTO account = accountService.getAccountById(id);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<AccountResponseDTO>>> getAllAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AccountResponseDTO> accounts = accountService.getAllAccounts(pageable);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<AccountResponseDTO>>> getAccountsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<AccountResponseDTO> accounts = accountService.getAccountsByType(type, pageable);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/industry/{industry}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<AccountResponseDTO>>> getAccountsByIndustry(
            @PathVariable String industry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<AccountResponseDTO> accounts = accountService.getAccountsByIndustry(industry, pageable);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/region/{region}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<AccountResponseDTO>>> getAccountsByRegion(
            @PathVariable String region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<AccountResponseDTO> accounts = accountService.getAccountsByRegion(region, pageable);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/credit-level/{creditLevel}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<AccountResponseDTO>>> getAccountsByCreditLevel(
            @PathVariable String creditLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<AccountResponseDTO> accounts = accountService.getAccountsByCreditLevel(creditLevel, pageable);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<AccountResponseDTO>>> searchAccounts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<AccountResponseDTO> accounts = accountService.searchAccounts(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "Account", description = "Update account record")
    public ResponseEntity<ApiResponse<AccountResponseDTO>> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountUpdateRequest request) {

        AccountResponseDTO account = accountService.updateAccount(id, request);
        return ResponseEntity.ok(ApiResponse.success("Account updated successfully", account));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "Account", description = "Delete account record")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccountStatistics() {
        Map<String, Object> statistics = accountService.getAccountStatistics();
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }
}
