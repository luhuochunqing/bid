package com.xiyu.bid.fees.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 费用统计数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeStatisticsDTO {

    private Long projectId;
    private BigDecimal totalPending;
    private BigDecimal totalPaid;
    private BigDecimal totalReturned;
    private BigDecimal totalCancelled;
    private BigDecimal grandTotal;

    public BigDecimal getTotalAmount() {
        return totalPending.add(totalPaid).add(totalReturned).add(totalCancelled);
    }
}
