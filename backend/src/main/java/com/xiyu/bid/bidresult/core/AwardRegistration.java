package com.xiyu.bid.bidresult.core;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input: 手工登记投标结果的纯值
 */
public record AwardRegistration(
        Long projectId,
        String projectName,
        ResultOutcome result,
        BigDecimal amount,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        Integer contractDurationMonths,
        String remark,
        Integer skuCount,
        String attachmentReference
) {
    public BidResultAttachmentRef attachmentRef() {
        if (attachmentReference == null || attachmentReference.isBlank()) {
            return null;
        }
        String trimmed = attachmentReference.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return null;
        }
        String[] parts = trimmed.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return new BidResultAttachmentRef(
                Long.parseLong(parts[1]),
                BidResultAttachmentRef.AttachmentType.valueOf(parts[0])
        );
    }

    public enum ResultOutcome {
        WON,
        LOST
    }
}
