// Input: REST payload for creating/updating a keyword subscription
// Output: validated record with name, keywords, logicOperator
// Pos: DTO/创建订阅请求
package com.xiyu.bid.tenderkeyword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSubscriptionRequest(
    @NotBlank @Size(max = 100) String name,
    @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 200) String> keywords,
    @Pattern(regexp = "AND|OR", message = "logicOperator must be AND or OR")
    @Size(max = 10) String logicOperator
) {
}
