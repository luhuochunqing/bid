package com.xiyu.bid.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Statistics DTO for Platform Accounts. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccountStatisticsDTO {

    /** Total account count. */
    private Long totalAccounts;
    /** Available accounts. */
    private Long availableAccounts;
    /** In-use accounts. */
    private Long inUseAccounts;
    /** Maintenance accounts. */
    private Long maintenanceAccounts;
    /** Disabled accounts. */
    private Long disabledAccounts;
}
