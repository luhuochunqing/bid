package com.xiyu.bid.platform.dto;

import com.xiyu.bid.platform.entity.PlatformAccount;

/** Maps PlatformAccount entities to DTOs. */
public final class PlatformAccountMapper {

    private PlatformAccountMapper() {
    }

    /** Convert entity to DTO. */
    public static PlatformAccountDTO toDTO(PlatformAccount account) {
        return PlatformAccountDTO.builder()
            .id(account.getId())
            .username(account.getUsername())
            .accountName(account.getAccountName())
            .contactPerson(account.getContactPerson())
            .contactPhone(account.getContactPhone())
            .contactEmail(account.getContactEmail())
            .platformType(account.getPlatformType())
            .url(account.getUrl())
            .hasCa(account.getHasCa())
            .caCustodian(account.getCaCustodian())
            .custodian(account.getCustodian())
            .remarks(account.getRemarks())
            .status(account.getStatus())
            .borrowedBy(account.getBorrowedBy())
            .borrowedAt(account.getBorrowedAt())
            .dueAt(account.getDueAt())
            .returnCount(account.getReturnCount())
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }
}
