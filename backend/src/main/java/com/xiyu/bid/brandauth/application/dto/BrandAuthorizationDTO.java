package com.xiyu.bid.brandauth.application.dto;

import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationScope;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BrandAuthorizationDTO(
        Long id,
        String brandName,
        String supplierName,
        AuthorizationScope scope,
        String scopeDetail,
        LocalDate startDate,
        LocalDate endDate,
        AuthorizationStatus status,
        String authorizationDocUrl,
        String remarks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
