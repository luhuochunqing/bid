package com.xiyu.bid.alerts.dto;

import com.xiyu.bid.alerts.domain.DedupPolicy;
import com.xiyu.bid.alerts.entity.AlertHistory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertHistoryCreateRequest {

    @NotNull(message = "Rule ID is required")
    private Long ruleId;

    @NotNull(message = "Level is required")
    private AlertHistory.AlertLevel level;

    @NotBlank(message = "Message is required")
    private String message;

    private String relatedId;

    /**
     * 去重策略，默认 {@link DedupPolicy#REUSE_UNTIL_RESOLVED}（原行为）。
     * CO-546: CA 到期预警使用 {@link DedupPolicy#DAILY_DEDUP} 实现每日通知。
     */
    private DedupPolicy dedupPolicy = DedupPolicy.REUSE_UNTIL_RESOLVED;
}
