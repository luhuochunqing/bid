package com.xiyu.bid.fees.service;

import com.xiyu.bid.fees.dto.FeeDTO;
import com.xiyu.bid.fees.entity.Fee;

final class FeeMapper {

    private FeeMapper() {
    }

    static FeeDTO toDTO(Fee fee) {
        return FeeDTO.builder()
                .id(fee.getId())
                .projectId(fee.getProjectId())
                .feeType(FeeDTO.FeeType.valueOf(fee.getFeeType().name()))
                .amount(fee.getAmount())
                .feeDate(fee.getFeeDate())
                .status(FeeDTO.Status.valueOf(fee.getStatus().name()))
                .paymentDate(fee.getPaymentDate())
                .returnDate(fee.getReturnDate())
                .paidBy(fee.getPaidBy())
                .returnTo(fee.getReturnTo())
                .remarks(fee.getRemarks())
                .createdAt(fee.getCreatedAt())
                .updatedAt(fee.getUpdatedAt())
                .build();
    }
}
