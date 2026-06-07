package com.xiyu.bid.personnel.application.dto;

import com.xiyu.bid.personnel.domain.valueobject.CertificateType;

import java.time.LocalDate;

public record CertificateDTO(
        Long id,
        String name,
        String certificateNumber,
        CertificateType type,
        LocalDate issueDate,
        LocalDate expiryDate,
        String attachmentUrl,
        boolean expired,
        long remainingDays
) {}
