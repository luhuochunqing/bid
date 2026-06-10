package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CaCertificateRequest {
    /**
     * Associated platform account IDs.
     * Replaces the legacy comma-separated string. Persisted to
     * ca_certificate_platforms; the platform_ids column is kept
     * for back-fill only and is no longer the source of truth.
     */
    private List<Long> platformIds;

    @NotBlank(message = "CA类型不能为空")
    private String caType;

    @NotBlank(message = "印章类型不能为空")
    private String sealType;

    private String electronicAccount;

    @NotBlank(message = "CA密码不能为空")
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
