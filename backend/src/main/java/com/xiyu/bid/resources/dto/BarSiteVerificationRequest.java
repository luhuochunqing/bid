package com.xiyu.bid.resources.dto;

import lombok.Data;

@Data
public class BarSiteVerificationRequest {

    private String verifiedBy;

    private String status;

    private String message;
}
