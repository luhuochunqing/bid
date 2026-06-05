package com.xiyu.bid.brandauth.manufacturer.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ManufacturerAuthorizationDTO(
        Long id,
        String authorizationType,
        String productLine,
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
        String remarks,
        String status,
        String revokeReason,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AttachmentDTO> attachments
) {
    public record AttachmentDTO(
            Long id,
            String attachmentType,
            String fileName,
            String fileUrl,
            Long fileSize,
            String fileType,
            LocalDateTime createdAt
    ) { }
}
