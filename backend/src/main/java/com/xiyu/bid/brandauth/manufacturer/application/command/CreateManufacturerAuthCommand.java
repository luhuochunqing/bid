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
        @NotNull LocalDate authStartDate,
        @NotNull LocalDate authEndDate,
        LocalDate auth1StartDate,
        LocalDate auth1EndDate,
        String auth1Remarks,
        LocalDate auth2StartDate,
        LocalDate auth2EndDate,
        String auth2Remarks,
        String remarks
) { }
