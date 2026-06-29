package com.xiyu.bid.platform.service;

import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 借用申请 DTO 转换器。
 * 从 PlatformAccountBorrowService 提取，解决 line-budget 超限问题。
 */
@Component
public class AccountBorrowApplicationMapper {

    private final PlatformAccountRepository accountRepository;

    public AccountBorrowApplicationMapper(PlatformAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** 批量转换，自动填充 accountName。 */
    public java.util.List<BorrowApplicationDTO> toDTOList(java.util.List<AccountBorrowApplication> apps) {
        Set<Long> accountIds = apps.stream()
                .map(AccountBorrowApplication::getAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> accountNameMap = accountIds.isEmpty()
                ? Collections.emptyMap()
                : accountRepository.findAllById(accountIds).stream()
                        .collect(Collectors.toMap(PlatformAccount::getId, PlatformAccount::getAccountName));
        return apps.stream()
                .map(app -> toDTO(app, accountNameMap::get))
                .collect(Collectors.toList());
    }

    /** 单条转换，无 accountName。 */
    public BorrowApplicationDTO toDTO(AccountBorrowApplication app) {
        return toDTO(app, id -> null);
    }

    /** 单条转换，带 accountName 解析器。 */
    public BorrowApplicationDTO toDTO(AccountBorrowApplication app, Function<Long, String> accountNameResolver) {
        String accountName = app.getAccountId() != null ? accountNameResolver.apply(app.getAccountId()) : null;
        if (accountName == null && app.getAccountId() != null) {
            accountName = accountRepository.findById(app.getAccountId())
                    .map(PlatformAccount::getAccountName)
                    .orElse(null);
        }
        return BorrowApplicationDTO.builder()
                .id(app.getId())
                .accountId(app.getAccountId())
                .accountName(accountName)
                .applicantId(app.getApplicantId())
                .custodianId(app.getCustodianId())
                .purpose(app.getPurpose())
                .projectName(app.getProjectName())
                .projectId(app.getProjectId())
                .expectedReturnAt(app.getExpectedReturnAt())
                .status(app.getStatus().name())
                .rejectReason(app.getRejectReason())
                .approvalComment(app.getApprovalComment())
                .approvedAt(app.getApprovedAt())
                .returnedAt(app.getReturnedAt())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
