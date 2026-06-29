package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.NotBlank;
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

    private Long accountId;

    // CO-386: custodianId 可选；未传时由 Service 从 account.contactPerson 自动取值
    private Long custodianId;

    @NotBlank(message = "使用目的不能为空")
    @Size(max = 500, message = "使用目的不能超过500字")
    private String purpose;

    @Size(max = 200, message = "项目名称不能超过200字")
    private String projectName;

    private Long projectId;

    private String expectedReturnAt;
}
