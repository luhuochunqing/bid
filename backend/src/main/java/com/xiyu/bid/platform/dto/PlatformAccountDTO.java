package com.xiyu.bid.platform.dto;

import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Data Transfer Object for Platform Account (password excluded). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccountDTO {

    /** Account ID. */
    private Long id;
    /** Platform username. */
    private String username;
    /** Platform account name. */
    private String accountName;
    /** Contact person userId (FK to users.id). */
    private Long contactPerson;
    /** Contact person display label: "姓名（工号）", derived from userId. */
    private String contactPersonLabel;
    /** Platform URL. */
    private String url;
    /** Whether CA certificate is associated. */
    private Boolean hasCa;
    /** CO-567: 是否已设置平台密码（不暴露密文，仅用于前端展示判断）。 */
    private Boolean hasPassword;
    /** Remarks. */
    private String remarks;
    /** Registrant name (CO-474). */
    private String registrant;
    /** Registration phone number (CO-474). */
    private String registerPhone;
    /** Registration email address (CO-474). */
    private String registerEmail;
    /** Account status. */
    private AccountStatus status;
    /** ID of user who borrowed. */
    private Long borrowedBy;
    /** Borrow timestamp. */
    private LocalDateTime borrowedAt;
    /** Borrow due timestamp. */
    private LocalDateTime dueAt;
    /** Return count. */
    private Integer returnCount;
    /** Creation timestamp. */
    private LocalDateTime createdAt;
    /** Last update timestamp. */
    private LocalDateTime updatedAt;
}
