package com.xiyu.bid.contractborrow.controller;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateContractBorrowRequest(
    @NotBlank(message = "合同编号不能为空")
    @Size(max = 100, message = "合同编号长度不能超过100个字符")
    String contractNo,
    @NotBlank(message = "合同名称不能为空")
    @Size(max = 255, message = "合同名称长度不能超过255个字符")
    String contractName,
    @Size(max = 255, message = "来源长度不能超过255个字符")
    String sourceName,
    @NotBlank(message = "申请人不能为空")
    @Size(max = 100, message = "申请人长度不能超过100个字符")
    String borrowerName,
    @Size(max = 100, message = "部门长度不能超过100个字符")
    String borrowerDept,
    @Size(max = 255, message = "客户名称长度不能超过255个字符")
    String customerName,
    @NotBlank(message = "借阅用途不能为空")
    @Size(max = 5000, message = "借阅用途长度不能超过5000个字符")
    String purpose,
    @Size(max = 100, message = "借阅类型长度不能超过100个字符")
    String borrowType,
    @NotNull(message = "预计归还日期不能为空")
    @FutureOrPresent(message = "预计归还日期不能早于今天")
    LocalDate expectedReturnDate
) {
}
