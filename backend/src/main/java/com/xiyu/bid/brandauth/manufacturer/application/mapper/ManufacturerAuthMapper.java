package com.xiyu.bid.brandauth.manufacturer.application.mapper;

import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO.AttachmentDTO;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ManufacturerAuthMapper {

    private ManufacturerAuthMapper() { }

    /**
     * Convert domain model and attachments to a DTO.
     *
     * @param auth        the manufacturer authorization domain object
     * @param attachments list of attachment entities
     * @return DTO with all fields and attachments populated
     */
    public static ManufacturerAuthorizationDTO toDTO(
            final ManufacturerAuthorization auth,
            final List<BrandAuthAttachmentEntity> attachments) {
        List<AttachmentDTO> atts = attachments != null
                ? attachments.stream()
                        .map(ManufacturerAuthMapper::toAttachmentDTO)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        return new ManufacturerAuthorizationDTO(
                auth.id(), auth.authorizationType(),
                auth.productLine().getDisplayName(),
                auth.brandId(), auth.brandName(),
                auth.importDomestic(), auth.manufacturerName(),
                auth.agentName(),
                auth.authStartDate(), auth.authEndDate(),
                auth.auth1StartDate(), auth.auth1EndDate(),
                auth.auth1Remarks(),
                auth.auth2StartDate(), auth.auth2EndDate(),
                auth.auth2Remarks(),
                auth.remarks(), auth.status().getDisplayName(),
                auth.revokeReason(), auth.createdBy(),
                auth.createdAt(), auth.updatedAt(),
                atts
        );
    }

    private static AttachmentDTO toAttachmentDTO(
            final BrandAuthAttachmentEntity e) {
        return new AttachmentDTO(
                e.getId(), e.getAttachmentType().name(),
                e.getFileName(), e.getFileUrl(),
                e.getFileSize(), e.getFileType(), e.getCreatedAt()
        );
    }
}
