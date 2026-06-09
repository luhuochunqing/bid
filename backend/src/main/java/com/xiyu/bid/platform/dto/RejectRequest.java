package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for rejecting a borrow application. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequest {

    @Size(max = 500, message = "拒绝原因不能超过500字")
    private String reason;
}
