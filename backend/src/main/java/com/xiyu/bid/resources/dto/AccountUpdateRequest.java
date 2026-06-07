package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.Account;
import lombok.Data;

@Data
public class AccountUpdateRequest {

    private String name;

    private Account.AccountType type;

    private String contactInfo;

    private String industry;

    private String region;

    private Account.CreditLevel creditLevel;
}
