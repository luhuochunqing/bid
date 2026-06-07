package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for borrowing a Platform Account. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowAccountRequest {

    /** Borrower user ID. */
    @NotNull(message = "Borrower ID is required")
    private Long borrowedBy;

    /** Due hours for the borrow (deprecated in favor of expectedReturnDate). */
    @Min(value = 1, message = "Due hours must be at least 1")
    private Integer dueHours;

    /** Purpose of the borrow. */
    private String purpose;

    /** Associated project ID. */
    private Long projectId;

    /** Expected return date (ISO format). */
    private String expectedReturnDate;
}
