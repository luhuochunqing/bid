package com.xiyu.bid.platform.dto;

import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for creating or updating a Platform Account. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccountCreateRequest {

    /** Max length for URL fields. */
    private static final int MAX_URL = 500;
    /** Max length for email fields. */
    private static final int MAX_EMAIL = 200;
    /** Max length for phone fields. */
    private static final int MAX_PHONE = 20;
    /** Max length for remarks. */
    private static final int MAX_REMARKS = 500;

    /** Platform login username. */
    @NotBlank(message = "平台账号不能为空")
    private String username;

    /** Platform login password. */
    @NotBlank(message = "平台密码不能为空")
    private String password;

    /** Display name of the platform. */
    @NotBlank(message = "平台名称不能为空")
    private String accountName;

    /** Platform type category. */
    @NotNull(message = "平台类型不能为空")
    private PlatformType platformType;

    /** Platform website URL. */
    @Size(max = MAX_URL)
    private String url;

    /** Contact person name (suggest format: name(employee ID)). */
    private String contactPerson;

    /** Contact phone number. */
    @Size(max = MAX_PHONE)
    private String contactPhone;

    /** Contact email address. */
    @Size(max = MAX_EMAIL)
    private String contactEmail;

    /** Whether a CA certificate is associated. */
    private Boolean hasCa;

    /** CA custodian user ID. */

    /** Account custodian user ID (borrow approval approver). */
    private Long custodian;
    private Long caCustodian;

    /** Optional remarks. */
    @Size(max = MAX_REMARKS)
    private String remarks;
}
