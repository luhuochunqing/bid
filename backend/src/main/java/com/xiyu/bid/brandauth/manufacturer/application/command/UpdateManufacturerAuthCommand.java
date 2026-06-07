package com.xiyu.bid.brandauth.manufacturer.application.command;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import java.time.LocalDate;

public record UpdateManufacturerAuthCommand(
        ProductLine productLine,
        String brandId,
        String brandName,
        String importDomestic,
        String manufacturerName,
        String agentName,
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
