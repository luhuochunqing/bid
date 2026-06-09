package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for submitting a borrow application. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowApplicationRequest {

    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    @NotNull(message = "保管员ID不能为空")
    private Long custodianId;

    @NotBlank(message = "使用目的不能为空")
    @Size(max = 500, message = "使用目的不能超过500字")
    private String purpose;

    @Size(max = 200, message = "项目名称不能超过200字")
    private String projectName;

    private String expectedReturnAt;
}
