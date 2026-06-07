package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BarCertificateBorrowRequest {

    @NotBlank(message = "Borrower is required")
    private String borrower;

    private Long projectId;

    private String purpose;

    private String remark;

    private LocalDate expectedReturnDate;
}
