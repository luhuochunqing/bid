package com.xiyu.bid.platform.dto;

import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Blueprint-aligned, sanitized projection of a Platform Account.
 *
 * <p>Returned to roles that should NOT see sensitive contact or credential
 * fields (project leaders / 销售人员 / 项目负责人). The DTO intentionally
 * omits username, password, contact person/phone/email, custodian, remarks
 * and borrow bookkeeping.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccountSummaryDTO {

    private Long id;
    private String accountName;
    private String url;
    private PlatformType platformType;
    private Boolean hasCa;
    private LocalDateTime updatedAt;
}
