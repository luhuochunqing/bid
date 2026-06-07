package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class BarCertificateResponseDTO {
    Long id;
    Long barAssetId;
    String type;
    String provider;
    String serialNo;
    String holder;
    String location;
    LocalDate expiryDate;
    CertificateStatus status;
    String currentBorrower;
    Long currentProjectId;
    String borrowPurpose;
    LocalDate expectedReturnDate;
    String remark;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public enum CertificateStatus {
        AVAILABLE,
        BORROWED,
        EXPIRED,
        DISABLED
    }
}
