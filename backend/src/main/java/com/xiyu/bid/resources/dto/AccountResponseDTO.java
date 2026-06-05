package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AccountResponseDTO {
    Long id;
    String name;
    AccountType type;
    String contactInfo;
    String industry;
    String region;
    CreditLevel creditLevel;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public enum AccountType {
        CLIENT,
        SUPPLIER,
        PARTNER,
        GOVERNMENT,
        OTHER
    }

    public enum CreditLevel {
        A,
        B,
        C,
        D
    }
}
