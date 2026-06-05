package com.xiyu.bid.resources.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BarCertificateUpdateRequest {

    private String type;

    private String provider;

    private String serialNo;

    private String holder;

    private String location;

    private LocalDate expiryDate;

    private String remark;
}
