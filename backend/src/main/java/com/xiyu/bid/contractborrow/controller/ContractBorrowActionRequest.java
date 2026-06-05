package com.xiyu.bid.contractborrow.controller;

import jakarta.validation.constraints.Size;

public record ContractBorrowActionRequest(
    @Size(max = 100, message = "操作人长度不能超过100个字符")
    String actorName,
    @Size(max = 5000, message = "备注长度不能超过5000个字符")
    String comment,
    @Size(max = 5000, message = "原因长度不能超过5000个字符")
    String reason
) {
}
