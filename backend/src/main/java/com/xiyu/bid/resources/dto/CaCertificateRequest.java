package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CaCertificateRequest {

    /** CO-566: 关联平台改为文本（多个用逗号分隔），不再绑定平台账号ID。 */
    private String relatedPlatforms;

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
