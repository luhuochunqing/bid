package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BarCertificateCreateRequest {

    @NotBlank(message = "Type is required")
    private String type;

    private String provider;

    @NotBlank(message = "Serial number is required")
    private String serialNo;

    private String holder;

    private String location;

    private LocalDate expiryDate;

    private String remark;
}
