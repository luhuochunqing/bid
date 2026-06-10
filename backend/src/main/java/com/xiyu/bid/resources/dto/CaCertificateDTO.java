package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.CaCertificateEntity;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
public class CaCertificateDTO {
    private Long id;
    private List<Long> platformIds;
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

    /**
     * Build a DTO from an entity. {@code platformIds} must be supplied
     * by the caller (from the join table). {@code caPassword} is masked
     * by default; pass {@code revealPassword=true} for admin reveal flows.
     */
    public static CaCertificateDTO from(CaCertificateEntity entity, List<Long> platformIds,
                                        boolean revealPassword, String decryptedPassword) {
        return CaCertificateDTO.builder()
                .id(entity.getId())
                .platformIds(platformIds == null ? Collections.emptyList() : platformIds)
                .caType(entity.getCaType())
                .sealType(entity.getSealType())
                .electronicAccount(entity.getElectronicAccount())
                .caPassword(revealPassword ? decryptedPassword : maskPassword(entity.getCaPassword()))
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

    public static CaCertificateDTO from(CaCertificateEntity entity, List<Long> platformIds) {
        return from(entity, platformIds, false, null);
    }

    private static String maskPassword(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        return "******";
    }
}
