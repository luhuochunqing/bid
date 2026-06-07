package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.CaCertificateEntity;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CaCertificateDTO {
    private Long id;
    private String platformIds;
    private String caType;
    private String sealType;
    private String electronicAccount;
    private String caPassword;
    private String issuer;
    private String holderName;
    private LocalDate expiryDate;
    private String caPlatformUrl;
    private Long custodianId;
    private String custodianName;
    private String borrowStatus;
    private String status;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CaCertificateDTO from(CaCertificateEntity entity) {
        return CaCertificateDTO.builder()
                .id(entity.getId())
                .platformIds(entity.getPlatformIds())
                .caType(entity.getCaType())
                .sealType(entity.getSealType())
                .electronicAccount(entity.getElectronicAccount())
                .caPassword(entity.getCaPassword())
                .issuer(entity.getIssuer())
                .holderName(entity.getHolderName())
                .expiryDate(entity.getExpiryDate())
                .caPlatformUrl(entity.getCaPlatformUrl())
                .custodianId(entity.getCustodianId())
                .custodianName(entity.getCustodianName())
                .borrowStatus(entity.getBorrowStatus())
                .status(entity.getStatus())
                .remarks(entity.getRemarks())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
