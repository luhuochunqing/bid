package com.xiyu.bid.brandauth.manufacturer.application.command;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateManufacturerAuthCommand(
        String authorizationType,
        @NotNull ProductLine productLine,
        @NotBlank String brandId,
        @NotBlank String brandName,
        @NotBlank String importDomestic,
        @NotBlank String manufacturerName,
        String agentName,
        // AGENT 类型使用 auth1/auth2 双时间段，authStartDate/authEndDate 仅 MANUFACTURER 必填
        //（按类型在 AppService 内校验，见 CreateManufacturerAuthAppService#validate）
        LocalDate authStartDate,
        LocalDate authEndDate,
        LocalDate auth1StartDate,
        LocalDate auth1EndDate,
        String auth1Remarks,
        LocalDate auth2StartDate,
        LocalDate auth2EndDate,
        String auth2Remarks,
        String remarks
) { }
