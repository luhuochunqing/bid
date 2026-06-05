package com.xiyu.bid.brandauth.application.command;

import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationScope;

import java.time.LocalDate;

public record BrandAuthUpsertCommand(
        String brandName,
        String supplierName,
        AuthorizationScope scope,
        String scopeDetail,
        LocalDate startDate,
        LocalDate endDate,
        String authorizationDocUrl,
        String remarks
) {}
