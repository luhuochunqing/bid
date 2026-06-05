package com.xiyu.bid.integration.external;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 外部系统更新标讯请求 DTO（接口规范 v2.0）。
 * 所有字段均可选，仅传入非空字段会被更新。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderUpdateRequest {

    @Size(max = 500)
    private String title;

    @Size(max = 500)
    private String customerName;

    private LocalDate publishDate;

    private String dueDate;

    @DecimalMin(value = "0", message = "预算金额不能为负数")
    @DecimalMax(value = "999999999999", message = "预算金额超出范围")
    private BigDecimal budgetAmount;

    @Size(max = 100)
    private String contactPerson;

    @Size(max = 50)
    private String contactPhone;

    @Size(max = 50)
    private String contactTel;

    @Size(max = 100)
    private String contactMail;

    @Size(max = 5000)
    private String contentDesc;

    private List<TenderPushRequest.AttachmentRef> attachments;
}
