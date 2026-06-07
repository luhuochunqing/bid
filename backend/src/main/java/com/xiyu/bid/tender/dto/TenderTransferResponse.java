package com.xiyu.bid.tender.dto;

import com.xiyu.bid.entity.Tender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标讯转派响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderTransferResponse {

    private Long tenderId;
    private Long oldOwnerId;
    private Long newOwnerId;
    private String department;
    private Tender.Status status;
}
