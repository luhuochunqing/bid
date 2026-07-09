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
    /** CO-566: 关联平台改为文本（多个用逗号分隔），不再绑定平台账号ID。 */
    private String relatedPlatforms;
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
    /** CO-451: 保管员工号，用于前端显示"姓名（工号）"格式 */
    private String custodianEmployeeNumber;
    private String borrowStatus;
    private String status;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Build a DTO from an entity. {@code caPassword} is masked by default;
     * pass {@code revealPassword=true} for admin reveal flows.
     * CO-451: {@code custodianEmployeeNumber} is supplied by the caller (from User entity lookup).
     */
    public static CaCertificateDTO from(CaCertificateEntity entity,
                                        boolean revealPassword, String decryptedPassword,
                                        String custodianEmployeeNumber) {
        return CaCertificateDTO.builder()
                .id(entity.getId())
                .relatedPlatforms(entity.getRelatedPlatforms())
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
                .custodianEmployeeNumber(custodianEmployeeNumber)
                .borrowStatus(entity.getBorrowStatus())
                .status(entity.getStatus())
                .remarks(entity.getRemarks())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** Legacy overload for backward compatibility (custodianEmployeeNumber = null) */
    public static CaCertificateDTO from(CaCertificateEntity entity,
                                        boolean revealPassword, String decryptedPassword) {
        return from(entity, revealPassword, decryptedPassword, null);
    }

    public static CaCertificateDTO from(CaCertificateEntity entity) {
        return from(entity, false, null, null);
    }

    private static String maskPassword(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        return "******";
    }
}
