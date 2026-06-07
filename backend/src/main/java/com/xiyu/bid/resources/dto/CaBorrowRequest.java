package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CaBorrowRequest {
    @NotNull(message = "CA证书ID不能为空")
    private Long caCertificateId;

    @NotBlank(message = "使用目的不能为空")
    private String purpose;

    private Long projectId;

    private String projectName;

    @NotBlank(message = "借用期限不能为空")
    private String borrowDurationType;

    private LocalDate expectedReturnDate;

    private String commitmentLetterUrl;
}
