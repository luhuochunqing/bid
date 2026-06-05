package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.command.UpdateManufacturerAuthCommand;
import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.mapper.ManufacturerAuthMapper;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.service.AuthorizationExpiryPolicy;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UpdateManufacturerAuthAppService {

    private final ManufacturerAuthorizationRepository repository;
    private final BrandAuthAttachmentJpaRepository attachmentRepository;

    @Transactional
    public ManufacturerAuthorizationDTO update(Long id, UpdateManufacturerAuthCommand cmd) {
        ManufacturerAuthorization existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("授权记录不存在: " + id));

        if (existing.isRevoked())
            throw new IllegalStateException("已作废的授权不可修改");
        if (existing.status() == AuthStatus.EXPIRED && cmd.manufacturerName() != null)
            throw new IllegalStateException("已失效的授权仅可修改备注，请使用续期功能");

        ManufacturerAuthorization updated = new ManufacturerAuthorization(
                existing.id(), existing.authorizationType(),
                cmd.productLine() != null ? cmd.productLine() : existing.productLine(),
                cmd.brandId() != null ? cmd.brandId() : existing.brandId(),
                cmd.brandName() != null ? cmd.brandName() : existing.brandName(),
                cmd.importDomestic() != null ? cmd.importDomestic() : existing.importDomestic(),
                cmd.manufacturerName() != null ? cmd.manufacturerName() : existing.manufacturerName(),
                cmd.agentName() != null ? cmd.agentName() : existing.agentName(),
                cmd.authStartDate() != null ? cmd.authStartDate() : existing.authStartDate(),
                cmd.authEndDate() != null ? cmd.authEndDate() : existing.authEndDate(),
                cmd.auth1StartDate() != null ? cmd.auth1StartDate() : existing.auth1StartDate(),
                cmd.auth1EndDate() != null ? cmd.auth1EndDate() : existing.auth1EndDate(),
                cmd.auth1Remarks() != null ? cmd.auth1Remarks() : existing.auth1Remarks(),
                cmd.auth2StartDate() != null ? cmd.auth2StartDate() : existing.auth2StartDate(),
                cmd.auth2EndDate() != null ? cmd.auth2EndDate() : existing.auth2EndDate(),
                cmd.auth2Remarks() != null ? cmd.auth2Remarks() : existing.auth2Remarks(),
                cmd.remarks() != null ? cmd.remarks() : existing.remarks(),
                AuthorizationExpiryPolicy.refreshStatus(existing),
                existing.revokeReason(), existing.createdBy(),
                existing.createdAt(), existing.updatedAt(), existing.version());

        if (!updated.authEndDate().isAfter(updated.authStartDate()))
            throw new IllegalArgumentException("结束时间须晚于开始时间");

        ManufacturerAuthorization saved = repository.save(updated);
        List<BrandAuthAttachmentEntity> atts = attachmentRepository.findByAuthorizationId(id);
        return ManufacturerAuthMapper.toDTO(saved, atts);
    }
}
