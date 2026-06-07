// Input: REST payload for updating an existing keyword subscription
// Output: validated record with optional fields
// Pos: DTO/更新订阅请求
package com.xiyu.bid.tenderkeyword.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateSubscriptionRequest(
    @Size(max = 100) String name,
    @Size(max = 20) List<@Size(max = 200) String> keywords,
    @Pattern(regexp = "AND|OR", message = "logicOperator must be AND or OR")
    @Size(max = 10) String logicOperator,
    @Pattern(regexp = "ACTIVE|PAUSED", message = "status must be ACTIVE or PAUSED")
    @Size(max = 20) String status
) {
}
