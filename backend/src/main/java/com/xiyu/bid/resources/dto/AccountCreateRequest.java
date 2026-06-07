package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.Account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AccountCreateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Type is required")
    private Account.AccountType type;

    private String contactInfo;

    private String industry;

    private String region;

    @NotNull(message = "Credit level is required")
    private Account.CreditLevel creditLevel;
}
