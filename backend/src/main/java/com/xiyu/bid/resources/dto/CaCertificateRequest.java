package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Data
public class CaCertificateRequest {
    private static final Set<String> VALID_SEAL_TYPES = Set.of(
            "OFFICIAL_SEAL", "LEGAL_PERSON_SEAL", "LEGAL_SIGN", "CONTACT_SIGN");

    private List<Long> platformIds;

    @NotBlank(message = "CA类型不能为空")
    private String caType;

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
