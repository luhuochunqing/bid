package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CaCertificateRequest {
    private String platformIds;

    @NotBlank(message = "CA类型不能为空")
    private String caType;

    @NotBlank(message = "印章类型不能为空")
    private String sealType;

    private String electronicAccount;

    private String caPassword;

    private String issuer;

    private String holderName;

    @NotNull(message = "CA有效期不能为空")
    private LocalDate expiryDate;

    private String caPlatformUrl;

    @NotNull(message = "CA保管员不能为空")
    private Long custodianId;

    @NotBlank(message = "CA保管员姓名不能为空")
    private String custodianName;

    private String remarks;
}
